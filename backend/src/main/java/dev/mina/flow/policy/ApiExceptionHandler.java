package dev.mina.flow.policy;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Failure> database(DataAccessException exception) {
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
}
