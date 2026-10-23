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
        dev.mina.flow.operations.RequestLogFilter.result(exception.code(),null,null);
        return ResponseEntity.status(exception.status()).header("Cache-Control", "no-store").body(new Failure(new Error(
                exception.code(), exception.getMessage(), dev.mina.flow.operations.RequestLogFilter.traceId(), exception.field())));
    }

    @ExceptionHandler({BindException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Failure> invalid(Exception exception) {
        return failure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 내용을 확인해 주세요.");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<Failure> database(Exception exception) {
        dev.mina.flow.operations.RequestLogFilter.result("POLICY_STORAGE_UNAVAILABLE",null,null);
        String requestId = dev.mina.flow.operations.RequestLogFilter.traceId();
        log.error("Policy database request failed; requestId={}; errorType={}",requestId,exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Cache-Control", "no-store").body(new Failure(
                new Error("POLICY_STORAGE_UNAVAILABLE", "규칙 저장소에 연결하지 못했습니다. 잠시 후 다시 조회해 주세요.", requestId, null)));
    }

    private ResponseEntity<Failure> failure(HttpStatus status, String code, String message) {
        dev.mina.flow.operations.RequestLogFilter.result(code,null,null);
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(new Failure(new Error(code, message, dev.mina.flow.operations.RequestLogFilter.traceId(), null)));
    }

    record Failure(Error error) {}
    record Error(String code, String message, String requestId, String field) {}

    @ExceptionHandler(UploadException.class)
    ResponseEntity<UploadFailure> upload(UploadException exception) {
        dev.mina.flow.operations.RequestLogFilter.result(exception.code(),exception.policyVersion(),null);
        return ResponseEntity.status(exception.status()).header("Cache-Control", "no-store").body(new UploadFailure(new UploadError(
                exception.code(), exception.getMessage(), dev.mina.flow.operations.RequestLogFilter.traceId(), exception.policyVersion(), exception.extension())));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Failure> tooLarge(MaxUploadSizeExceededException exception) {
        dev.mina.flow.operations.RequestLogFilter.result("UPLOAD_SIZE_LIMIT",null,null);
        return failure(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_SIZE_LIMIT", "파일은 10 MiB, 전체 요청은 12 MiB 이하여야 합니다.");
    }
    record UploadFailure(UploadError error) {}
    record UploadError(String code, String message, String requestId, Long policyVersion, String extension) {}

    @ExceptionHandler(java.io.IOException.class)
    ResponseEntity<Failure> uploadRead(Exception exception) {
        dev.mina.flow.operations.RequestLogFilter.result("UPLOAD_READ_FAILED",null,null);
        String requestId = dev.mina.flow.operations.RequestLogFilter.traceId();
        log.error("Upload request read failed; requestId={}; errorType={}",requestId,exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).header("Cache-Control", "no-store").body(new Failure(
                new Error("UPLOAD_READ_FAILED", "파일 전송을 완료하지 못했습니다. 저장 결과를 확인해 주세요.", requestId, null)));
    }
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    ResponseEntity<Failure> malformedMultipart(Exception exception) {
        return failure(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART", "파일 전송 형식을 확인해 주세요.");
    }
}
