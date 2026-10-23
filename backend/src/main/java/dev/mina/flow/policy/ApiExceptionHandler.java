package dev.mina.flow.policy;

import java.util.UUID;
import dev.mina.flow.upload.UploadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.transaction.TransactionException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PolicyException.class)
    ResponseEntity<Failure> policy(PolicyException exception) {
        return failure(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler({BindException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Failure> invalid(Exception exception) {
        return failure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 내용을 확인해 주세요.");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<Failure> database(Exception exception) {
        String requestId = UUID.randomUUID().toString();
        log.error("Policy database request failed; requestId={}", requestId, exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Failure(
                new Error("POLICY_STORAGE_UNAVAILABLE", "규칙 저장소에 연결하지 못했습니다. 잠시 후 다시 조회해 주세요.", requestId)));
    }

    private ResponseEntity<Failure> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new Failure(new Error(code, message, UUID.randomUUID().toString())));
    }

    record Failure(Error error) {}
    record Error(String code, String message, String requestId) {}

    @ExceptionHandler(UploadException.class)
    ResponseEntity<UploadFailure> upload(UploadException exception) {
        return ResponseEntity.status(exception.status()).body(new UploadFailure(new UploadError(
                exception.code(), exception.getMessage(), UUID.randomUUID().toString(), exception.policyVersion(), exception.extension())));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Failure> tooLarge(MaxUploadSizeExceededException exception) {
        return failure(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_SIZE_LIMIT", "파일은 10 MiB, 전체 요청은 12 MiB 이하여야 합니다.");
    }
    record UploadFailure(UploadError error) {}
    record UploadError(String code, String message, String requestId, Long policyVersion, String extension) {}
}
