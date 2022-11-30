import org.apache.commons.cli.CommandLine;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;

public class HttpTask implements Runnable{
    private static final String DEFAULT_SERVER_PATH = "webroot";
    private static final String INDEX_FILE = "\\index.html";
    Socket connection;
    static CommandLine cmd;
    HttpTask(Socket connection, CommandLine cmd) {
        this.connection = connection;
        HttpTask.cmd = cmd;
    }

    private static void sendResponse(Socket socket, String protocol, STATUS_CODE status, String[] headers, byte[] content, boolean encrypted) throws IOException {
        OutputStream clientOutput = encrypted ? new GZIPOutputStream(socket.getOutputStream()) : socket.getOutputStream();
        //
        clientOutput.write((protocol + " " + status + "\r\n").getBytes());
        for (String header : headers)
            clientOutput.write((header + "\r\n").getBytes());
        clientOutput.write("\r\n".getBytes());
        clientOutput.write(content);
        clientOutput.flush();
        clientOutput.close();
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
        StringBuilder log = new StringBuilder();
        log.append(new Date());
        log.append(request.method);
        log.append(request.path);
        log.append(request.headers.get("User-Agent"));
        System.out.println(log);
    }

    private static void logError(HttpRequest request) {
        StringBuilder log = new StringBuilder();
        log.append(new Date()).append(" ");
        log.append(request.method).append(" ");
        log.append(request.path).append(" ");
        log.append(request.headers.get("User-Agent")).append(" ");
        log.append(" Error (404): \"Not found\"");
        System.out.println(log);
    }

    private static void sendNotFoundResponse(HttpRequest request, Socket socket) throws IOException {
        byte[] content = ("Not found: " + request.path).getBytes();
        ArrayList<String> headers = new ArrayList<>();
        headers.add(new Date().toString());
        sendResponse(socket, request.protocol, STATUS_CODE.NOT_FOUND, headers.toArray(String[]::new), content, false);
    }

    private static void sendOkResponse(HttpRequest request, Socket socket, Path filepath) throws IOException {
        if (Files.isDirectory(filepath)) {
            sendDirResponse(socket, request, filepath);
            return;
        }
        byte[] bytes = Files.readAllBytes(filepath);
        ArrayList<String> headers = new ArrayList<>();
        headers.add("content-type: " + Files.probeContentType(filepath));
        headers.add("content-length: " + bytes.length);
        headers.add("date: " + new Date());
        headers.add("last-modified: " + Files.getLastModifiedTime(filepath));

        boolean encoded = false;
        if (request.headers.get("Accept-Encoding").contains("gzip")) {
            headers.add("content-encoding: gzip");
            encoded = true;
        }

        sendResponse(socket, request.protocol, STATUS_CODE.OK, headers.toArray(String[]::new), bytes, encoded);
    }

    private static void sendDirResponse(Socket socket, HttpRequest request, Path filepath) throws IOException {
        if (Files.exists(filepath)) {
            byte[] bytes = null;
            Path indexFile = Paths.get(filepath.toString(), INDEX_FILE);
            if (Files.exists(indexFile))
                bytes = Files.readAllBytes(indexFile);
            else if (cmd.hasOption("d")){
                ByteBuffer byteBuffer = ByteBuffer.allocate(100000);
                Iterator<Path> files = Files.list(filepath).iterator();
                while (files.hasNext()) {
                    Path next = files.next();
                    String fileName = "<a href=\"" + next.toString() + "\">" + next.getFileName().toString() + "</a> </br>";
                    byteBuffer.put(fileName.getBytes());
                }
                bytes = byteBuffer.array();
            }
            if (bytes == null)
                sendNotFoundResponse(request, socket);

            ArrayList<String> headers = new ArrayList<>();
            headers.add("content-type: text/text");
            headers.add("content-length: " + bytes.length);
            headers.add("date: " + new Date());
            headers.add("last-modified: " + Files.getLastModifiedTime(filepath));

            boolean encoded = false;
            if (request.headers.get("content-encoding").contains("gzip")) {
                headers.add("content-encoding: gzip");
                encoded = true;
            }
            sendResponse(socket, request.protocol, STATUS_CODE.OK, headers.toArray(String[]::new), bytes, encoded);
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
            e.printStackTrace();
        }
    }
}
