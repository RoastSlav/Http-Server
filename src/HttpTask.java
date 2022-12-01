import org.apache.commons.cli.CommandLine;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

public class HttpTask implements Runnable {
    private static final String DEFAULT_SERVER_PATH = "webroot";
    private static final String INDEX_FILE = "\\index.html";
    private static final Path NOT_FOUND_PAGE = Path.of("webroot\\NotFound.html");
    private static final String GZ_EXTENSION = ".gz";
    private static final Set<String> FILES_TO_COMPRESS = Set.of("text/plain", "text/css", "text/csv" , "text/html", "text/calendar", "text/javascript", "text/xml", "application/json", "application/xml", "image/svg+xml");
    Socket connection;
    static CommandLine cmd;

    HttpTask(Socket connection, CommandLine cmd) {
        this.connection = connection;
        HttpTask.cmd = cmd;
    }

    private static HttpRequest parseRequest(String requestString) {
        String[] requestSplit = requestString.split("\r\n");
        HttpRequest httpRequest = new HttpRequest();

        String[] request = requestSplit[0].split(" ");
        String httpRequestType = request[0];
        httpRequest.method = HTTP_REQUEST_METHOD.valueOf(httpRequestType);
        httpRequest.path = request[1];
        httpRequest.protocol = request[2];

        String header = requestSplit[1];
        int i = 1;
        for (; !header.equals("") && i < requestSplit.length; i++) {
            String[] nameAndValue = header.split(": ");
            httpRequest.headers.put(nameAndValue[0], nameAndValue[1]);
            header = requestSplit[i];
        }

        StringBuilder content = new StringBuilder();
        for (; i < requestSplit.length; i++) {
            content.append(requestSplit[i]);
        }
        httpRequest.content = content.toString();

        return httpRequest;
    }

    private static void logRequest(HttpRequest request) {
        StringBuilder log = buildLog(request);
        System.out.println(log);
    }

    private static void logError(HttpRequest request) {
        StringBuilder log = buildLog(request);
        log.append(" Error (404): \"Not found\"");
        System.out.println(log);
    }

    private static StringBuilder buildLog(HttpRequest request) {
        StringBuilder log = new StringBuilder();
        log.append(new Date()).append(" ");
        log.append(request.method).append(" ");
        log.append(request.path).append(" ");
        log.append(request.headers.get("User-Agent")).append(" ");
        return log;
    }

    private static void compressFile(Path filepath) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(filepath.toFile())) {
            try (GZIPOutputStream outputStream = new GZIPOutputStream(new FileOutputStream(filepath + GZ_EXTENSION))) {
                byte[] dataBuffer = new byte[2048];
                for (int bytesRead; (bytesRead = inputStream.read(dataBuffer, 0, 2048)) != -1; ) {
                    outputStream.write(dataBuffer, 0, bytesRead);
                }
            }
        }
    }

    private static void sendNotFoundResponse(HttpRequest request, Socket socket) throws IOException {
        HttpResponse response = new HttpResponse();
        response.filePath = NOT_FOUND_PAGE;
        response.headers.add(new Date().toString());
        response.statusCode = STATUS_CODE.NOT_FOUND;
        response.protocol = request.protocol;

        checkForCompressed(request, response);
        response.sendResponse(socket);
    }

    private static void sendOkResponse(HttpRequest request, Socket socket, Path filePath) throws IOException {
        if (Files.isDirectory(filePath)) {
            sendDirResponse(socket, request, filePath);
            return;
        }

        HttpResponse response = new HttpResponse();
        response.statusCode = STATUS_CODE.OK;
        response.protocol = request.protocol;
        response.filePath = filePath;
        response.headers.add("content-type: " + Files.probeContentType(filePath));
        response.headers.add("date: " + new Date());
        response.headers.add("last-modified: " + Files.getLastModifiedTime(filePath));

        checkForCompressed(request, response);
        response.sendResponse(socket);
    }

    private static void sendDirResponse(Socket socket, HttpRequest request, Path filepath) throws IOException {
        HttpResponse response = new HttpResponse();

        Path indexFile = Paths.get(filepath.toString(), INDEX_FILE);
        if (Files.exists(indexFile)) {
            response.filePath = indexFile;
            response.headers.add("last-modified: " + Files.getLastModifiedTime(indexFile));
            checkForCompressed(request, response);
        } else if (cmd.hasOption("d")) {
            response.filePath = filepath;
            response.headers.add("last-modified: " + Files.getLastModifiedTime(filepath));
        } else {
            sendNotFoundResponse(request, socket);
            return;
        }

        response.statusCode = STATUS_CODE.OK;
        response.protocol = request.protocol;
        response.headers.add("content-type: text/html");
        response.headers.add("date: " + new Date());

        response.sendResponse(socket);
    }

    private static void checkForCompressed(HttpRequest request, HttpResponse response) throws IOException {
        if (!cmd.hasOption("g") || !request.headers.get("Accept-Encoding").contains("gzip"))
            return;

        Path filepathCompressed = Path.of(response.filePath + GZ_EXTENSION);
        if (Files.exists(filepathCompressed)) {
            response.filePath = filepathCompressed;
            response.headers.add("content-encoding: gzip");
        } else if (cmd.hasOption("c") && FILES_TO_COMPRESS.contains(Files.probeContentType(response.filePath))) {
            compressFile(response.filePath);
            response.filePath = filepathCompressed;
            response.headers.add("content-encoding: gzip");
        }
    }

    @Override
    public void run() {
        StringBuilder requestString = new StringBuilder();
        try {
            BufferedInputStream inputStream = new BufferedInputStream(connection.getInputStream());
            while (inputStream.available() > 0) {
                requestString.append((char) inputStream.read());
            }

            if (requestString.isEmpty())
                return;
        } catch (Exception e) {
            System.out.println("There was an error receiving the request");
        }

        try {
            HttpRequest request = parseRequest(requestString.toString());
            Path filePath = Paths.get(DEFAULT_SERVER_PATH, request.path);
            if (Files.exists(filePath)) {
                logRequest(request);
                sendOkResponse(request, connection, filePath);
            } else {
                logError(request);
                sendNotFoundResponse(request, connection);
            }
        } catch (IOException e) {
            System.out.println("There was an error sending the response");
        }
    }
}
