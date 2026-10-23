package dev.mina.flow.upload;

import dev.mina.flow.policy.PolicyService;
import dev.mina.flow.policy.PolicyView;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class UploadService {
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    private final PolicyService policies;
    private final UploadStorage storage;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public UploadService(PolicyService policies, UploadStorage storage, JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.policies = policies; this.storage = storage; this.jdbc = jdbc; this.transactions = new TransactionTemplate(manager);
    }
    public UploadResult upload(String name, long declaredSize, InputStream input) {
        // Read exactly one committed snapshot before validating/copying this file.
        PolicyView policy = policies.get();
        String extension = FilenameRules.extension(name, policy.version());
        if (declaredSize > MAX_FILE_BYTES) throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "파일은 10 MiB 이하여야 합니다.", policy, extension);
        if (declaredSize == 0) throw failure(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "0바이트 파일은 보낼 수 없습니다.", policy, extension);
        boolean blocked = policy.fixed().stream().anyMatch(item -> item.enabled() && item.extension().equals(extension))
                || policy.custom().stream().anyMatch(item -> item.extension().equals(extension));
        if (blocked) throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "EXTENSION_BLOCKED", "현재 규칙에서 차단한 확장자입니다: " + extension, policy, extension);
        UUID id = UUID.randomUUID();
        Path destination = storage.destination(id);
        Path temporary = null;
        boolean transactionStarted = false;
        try {
            temporary = storage.temporary();
            long size = storage.copy(input, temporary);
            if (size == 0) throw failure(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "0바이트 파일은 보낼 수 없습니다.", policy, extension);
            if (size != declaredSize) throw failure(HttpStatus.BAD_REQUEST, "FILE_SIZE_MISMATCH", "파일을 끝까지 읽지 못했습니다. 다시 확인해 주세요.", policy, extension);
            Path staged = temporary;
            // execute() commits before returning: an unsuccessful commit cannot yield HTTP 201.
            transactionStarted = true;
            return transactions.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int completion) {
                        if (completion == STATUS_ROLLED_BACK) cleanup(destination);
                        // Unknown commit outcome: retain file for reconciliation, never pretend success.
                    }
                });
                jdbc.update("INSERT INTO upload_record (id, original_name, extension, size_bytes, policy_version, state) VALUES (?, ?, ?, ?, ?, 'PENDING')", id, name, extension, size, policy.version());
                try { storage.publish(staged, destination); }
                catch (IOException error) { throw failure(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_FAILED", "파일을 저장하지 못했습니다.", policy, extension); }
                jdbc.update("UPDATE upload_record SET state = 'READY' WHERE id = ?", id);
                return new UploadResult(id, name, extension, size, policy.version(), "READY");
            });
        } catch (UploadStorage.FileTooLargeException error) {
            throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "파일은 10 MiB 이하여야 합니다.", policy, extension);
        } catch (IOException error) {
            throw failure(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_FAILED", "파일을 읽거나 저장하지 못했습니다.", policy, extension);
        } finally {
            if (temporary != null) cleanup(temporary);
            if (!transactionStarted) cleanup(destination);
        }
    }
    private void cleanup(Path path) {
        try { storage.remove(path); }
        catch (IOException error) { log.error("Upload cleanup failed; reconciliation required", error); }
    }
    private UploadException failure(HttpStatus status, String code, String message, PolicyView policy, String extension) {
        return new UploadException(status, code, message, policy.version(), extension);
    }
    public record UploadResult(UUID id, String originalName, String extension, long sizeBytes, long policyVersion, String state) {}
}
