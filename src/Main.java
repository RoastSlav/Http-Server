import org.apache.commons.cli.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final String PROGRAM_NAME = "Http-Server";
    private static final int DEFAULT_PORT = 8085;
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
        Option directories = Option.builder("d").argName("Show directories").desc("If the file is a directory show the files in it.").build();
        options.addOption(directories);
        Option compress = Option.builder("c").argName("Real time compression").desc("Compress file realtime before sending").build();
        options.addOption(compress);
        Option sendCompressed = Option.builder("g").argName("Send compressed").desc("Send compressed files if the client supports it").build();
        options.addOption(sendCompressed);
        return options;
    }

    private static void listenOnPort() throws IOException {
        int portToUse = DEFAULT_PORT;
        if (cmd.hasOption("p"))
            portToUse = Integer.parseInt(cmd.getOptionValue("p"));

        ServerSocket socket = new ServerSocket(portToUse);
        Socket s = null;
        while ((s = socket.accept()) != null) {
            threadPool.submit(new HttpTask(s, cmd));
        }
    }
}