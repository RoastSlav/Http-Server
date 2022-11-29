import java.util.HashMap;

public class HttpRequest {
    HttpRequestTypes type;
    String filename;
    String httpProtocol;
    HashMap<String, String> headers = new HashMap<>();
    String content;
}
