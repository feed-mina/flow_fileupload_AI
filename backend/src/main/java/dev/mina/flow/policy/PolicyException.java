package dev.mina.flow.policy;

import org.springframework.http.HttpStatus;

public class PolicyException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String field;

    public PolicyException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public PolicyException(HttpStatus status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String field() { return field; }
}
