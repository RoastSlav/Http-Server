public enum STATUS_CODE {
    OK("200 OK"), NOT_FOUND("404 Not Found"), BAD_REQUEST("400 Bad Request");

    private String value;
    STATUS_CODE(String i) {
        this.value = i;
    }
}
