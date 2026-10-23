package dev.mina.flow.upload;

import org.springframework.http.HttpStatus;

public class UploadException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Long policyVersion;
    private final String extension;

    public UploadException(HttpStatus status, String code, String message, Long policyVersion, String extension) {
        super(message);
        this.status = status; this.code = code; this.policyVersion = policyVersion; this.extension = extension;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Long policyVersion() { return policyVersion; }
    public String extension() { return extension; }
}
