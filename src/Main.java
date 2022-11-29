import org.apache.commons.cli.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final String PROGRAM_NAME = "Http-Server";
    private static final int DEFAULT_PORT = 8085;
    private static final String DEFAULT_SERVER_PATH = "/webroot";
    private static final HelpFormatter FORMATTER = new HelpFormatter();
    private static final int DEFAULT_THREAD_COUNT = 1;
    private static ExecutorService threadPool;
    private static CommandLine cmd = null;
    public static void main(String[] args) throws IOException {
        Options options = intializeOptions();
        CommandLineParser cmdParser = new DefaultParser();
        try {
            cmd = cmdParser.parse(options, args);
        } catch (ParseException e) {
            FORMATTER.printHelp(PROGRAM_NAME, options, true);
            return;
        }

        if (cmd.hasOption("help")) {
            FORMATTER.printHelp(PROGRAM_NAME, options, true);
            return;
        }

        int numberOfThreads = DEFAULT_THREAD_COUNT;
        if (cmd.hasOption("t"))
            numberOfThreads = Integer.parseInt(cmd.getOptionValue("t"));
        threadPool = Executors.newFixedThreadPool(numberOfThreads);

        listenOnPort();
    }

    private static Options intializeOptions() {
        Options options = new Options();
        Option path = Option.builder("path").argName("Server Path").required().hasArg().desc("The path for the server content").build();
        options.addOption(path);
        Option port = Option.builder("p").longOpt("port").argName("Port").hasArg().desc("The port for the server to use").build();
        options.addOption(port);
        Option threads = Option.builder("t").longOpt("threads").argName("Threads number").hasArg().desc("The number of threads to process requests").build();
        options.addOption(threads);
        Option directories = Option.builder("d").argName("Show directories").hasArg().desc("If the file is a directory show the files in it.").build();
        options.addOption(directories);
        return options;
    }

    private static void listenOnPort() throws IOException {
        int portToUse = DEFAULT_PORT;
        if (cmd.hasOption("p"))
            portToUse = Integer.parseInt(cmd.getOptionValue("p"));

        ServerSocket socket = new ServerSocket(portToUse);
        while (!socket.isClosed()) {
            Socket connection = socket.accept();
            Runnable r = () -> {
                try {
                    BufferedInputStream inputStream = new BufferedInputStream(connection.getInputStream());
                    OutputStream outputStream = connection.getOutputStream();
                    StringBuilder requestString = new StringBuilder();
                    while (inputStream.available() > 0) {
                        requestString.append((char) inputStream.read());
                    }

                    HttpRequest request;
                    if (!requestString.isEmpty()) {
                        request = parseRequest(requestString.toString());

                    }
                } catch (Exception e) {
                    System.out.println("There was an error receiving the request");
                }
            };
            threadPool.submit(r);
        }
    }

    private static HttpRequest parseRequest(String requestString) {
        String[] requestSplit = requestString.split("\n");
        HttpRequest httpRequest = new HttpRequest();

        String[] request = requestSplit[0].split(" ");
        String httpRequestType = request[0];
        httpRequest.type = HttpRequestTypes.valueOf(httpRequestType);
        httpRequest.filename = request[1];
        httpRequest.httpProtocol = request[2];

        String header = requestSplit[1];
        int i = 1;
        for (; !header.equals("\r"); i++) {
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
}