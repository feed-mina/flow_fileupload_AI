package dev.mina.flow.policy;

import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyService {
    private static final Set<String> FIXED = Set.of("bat", "cmd", "com", "cpl", "exe", "scr", "js");
    private final PolicyRepository repository;

    public PolicyService(PolicyRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PolicyView get() { return repository.snapshot(); }

    @Transactional
    public PolicyView setFixed(String extension, boolean enabled, String ifMatch) {
        if (!FIXED.contains(extension)) {
            throw new PolicyException(HttpStatus.NOT_FOUND, "FIXED_NOT_FOUND", "지정된 고정 확장자가 아닙니다.");
        }
        checkVersion(repository.lockVersion(), ifMatch);
        repository.setFixed(extension, enabled);
        repository.advanceVersion();
        repository.audit("FIXED_SET", extension, enabled);
        return repository.snapshot();
    }

    @Transactional
    public PolicyView addCustom(String input, String ifMatch) {
        String extension = normalize(input);
        checkVersion(repository.lockVersion(), ifMatch);
        if (FIXED.contains(extension)) {
            throw new PolicyException(HttpStatus.CONFLICT, "CUSTOM_FIXED_CONFLICT", "고정 확장자 영역에서 선택해 주세요.");
        }
        if (repository.exists(extension)) {
            throw new PolicyException(HttpStatus.CONFLICT, "DUPLICATE_EXTENSION", "이미 추가한 확장자입니다.");
        }
        if (repository.customCount() >= 200) {
            throw new PolicyException(HttpStatus.CONFLICT, "CUSTOM_LIMIT_REACHED", "커스텀 확장자는 최대 200개까지 추가할 수 있습니다.");
        }
        repository.addCustom(extension);
        repository.advanceVersion();
        repository.audit("CUSTOM_ADDED", extension, true);
        return repository.snapshot();
    }

    @Transactional
    public PolicyView deleteCustom(long id, String ifMatch) {
        checkVersion(repository.lockVersion(), ifMatch);
        String extension = repository.customExtension(id);
        if (repository.deleteCustom(id) != 1) {
            throw new PolicyException(HttpStatus.NOT_FOUND, "CUSTOM_NOT_FOUND", "삭제할 커스텀 확장자가 없습니다. 최신 규칙을 확인해 주세요.");
        }
        repository.advanceVersion();
        repository.audit("CUSTOM_DELETED", extension, false);
        return repository.snapshot();
    }

    private String normalize(String input) {
        if (input == null) throw invalidExtension();
        String normalized = input.strip();
        if (normalized.startsWith(".")) normalized = normalized.substring(1);
        // Validate the raw alphabet before Unicode case folding (Kelvin sign -> k).
        if (!normalized.matches("[A-Za-z0-9]{1,20}")) throw invalidExtension();
        return normalized.toLowerCase(Locale.ROOT);
    }

    private PolicyException invalidExtension() {
        return new PolicyException(HttpStatus.BAD_REQUEST, "INVALID_EXTENSION", "확장자는 영문·숫자 1~20자로 입력해 주세요.", "extension");
    }

    private void checkVersion(long actual, String ifMatch) {
        if (ifMatch == null) {
            throw new PolicyException(HttpStatus.PRECONDITION_REQUIRED, "POLICY_VERSION_REQUIRED", "최신 규칙을 조회한 뒤 다시 시도해 주세요.");
        }
        if (!ifMatch.matches("\"[0-9]+\"")) {
            throw new PolicyException(HttpStatus.BAD_REQUEST, "INVALID_POLICY_VERSION", "규칙 버전 형식이 올바르지 않습니다.");
        }
        if (!ifMatch.equals("\"" + actual + "\"")) {
            throw new PolicyException(HttpStatus.PRECONDITION_FAILED, "POLICY_VERSION_CONFLICT", "다른 화면에서 규칙이 바뀌었습니다. 최신 규칙을 확인해 주세요.");
        }
    }
}
