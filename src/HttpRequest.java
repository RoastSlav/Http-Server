import java.util.HashMap;

public class HttpRequest {
    HTTP_REQUEST_METHOD method;
    String path;
    String protocol;
    HashMap<String, String> headers = new HashMap<>();
    String content;
}
