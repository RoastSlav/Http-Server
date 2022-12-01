import org.apache.commons.cli.CommandLine;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

public class HttpTask implements Runnable {
    private static final String DEFAULT_SERVER_PATH = "webroot";
    private static final String INDEX_FILE = "\\index.html";
    private static final Path NOT_FOUND_PAGE = Path.of("webroot\\NotFound.html");
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

    private static void sendNotFoundResponse(HttpRequest request, Socket socket) throws IOException {
        HttpResponse response = new HttpResponse();
        response.filePath = NOT_FOUND_PAGE;
        response.headers.add(new Date().toString());
        response.statusCode = STATUS_CODE.NOT_FOUND;
        response.protocol = request.protocol;

        if (request.headers.get("Accept-Encoding").contains("gzip")) {
            response.headers.add("content-encoding: gzip");
            response.sendResponseEncoded(socket);
            return;
        }
        response.sendResponse(socket);
    }

    private static void sendOkResponse(HttpRequest request, Socket socket, Path filepath) throws IOException {
        if (Files.isDirectory(filepath)) {
            sendDirResponse(socket, request, filepath);
            return;
        }

        HttpResponse response = new HttpResponse();
        response.statusCode = STATUS_CODE.OK;
        response.protocol = request.protocol;
        response.filePath = filepath;
        response.headers.add("content-type: " + Files.probeContentType(filepath));
        response.headers.add("date: " + new Date());
        response.headers.add("last-modified: " + Files.getLastModifiedTime(filepath));

        if (request.headers.get("Accept-Encoding").contains("gzip")) {
            response.headers.add("content-encoding: gzip");
            response.sendResponseEncoded(socket);
            return;
        }
        response.sendResponse(socket);
    }

    private static void sendDirResponse(Socket socket, HttpRequest request, Path filepath) throws IOException {
        HttpResponse response = new HttpResponse();

        Path indexFile = Paths.get(filepath.toString(), INDEX_FILE);
        if (Files.exists(indexFile)) {
            response.filePath = indexFile;
            response.headers.add("last-modified: " + Files.getLastModifiedTime(indexFile));
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

        if (request.headers.get("Accept-Encoding").contains("gzip")) {
            response.headers.add("content-encoding: gzip");
            response.sendResponseEncoded(socket);
            return;
        }
        response.sendResponse(socket);
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
