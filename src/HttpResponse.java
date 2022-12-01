import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;

public class HttpResponse {
    ArrayList<String> headers = new ArrayList<>();
    STATUS_CODE statusCode;
    String protocol;
    Path filePath;

    public void sendResponse(Socket socket) throws IOException {
        headers.add("Content-Length: " + filePath.toFile().length());
        OutputStream clientOutput = socket.getOutputStream();
        clientOutput.write((protocol + " " + statusCode + "\r\n").getBytes());
        for (String header : headers)
            clientOutput.write((header + "\r\n").getBytes());
        clientOutput.write("\r\n".getBytes());
        sendContent(filePath, clientOutput);

        clientOutput.flush();
        clientOutput.close();
    }

    private void sendContent(Path filePath, OutputStream output) throws IOException {
        if (filePath.toFile().isDirectory()) {
            Iterator<Path> files = Files.list(filePath).iterator();
            while (files.hasNext()) {
                Path next = files.next();
                String fileName = "<a href=\"" + next.toString() + "\">" + next.getFileName().toString() + "</a> </br>";
                output.write(fileName.getBytes());
            }
        } else {
            try (FileInputStream in = new FileInputStream(filePath.toFile())) {
                byte[] dataBuffer = new byte[2048];
                for (int bytesRead; (bytesRead = in.read(dataBuffer, 0, 2048)) != -1; ) {
                    output.write(dataBuffer, 0, bytesRead);
                }
            }
        }
    }
}
