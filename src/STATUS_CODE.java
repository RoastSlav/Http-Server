public enum STATUS_CODE {
    OK("200 OK"), NOT_FOUND("404 Not Found");

    private String value;
    STATUS_CODE(String i) {
        this.value = i;
    }
}
