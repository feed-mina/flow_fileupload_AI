package dev.mina.flow.upload;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public final class FilenameRules {
    private FilenameRules() {}
    public static String extension(String name, long version) {
        if (name == null || name.isBlank()) throw error("FILE_NAME_REQUIRED", "파일 이름이 필요합니다.", version);
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0 || name.codePoints().anyMatch(Character::isISOControl))
            throw error("INVALID_FILE_NAME", "파일 이름에는 경로·제어문자를 사용할 수 없습니다.", version);
        if (name.getBytes(StandardCharsets.UTF_8).length > 255)
            throw error("FILE_NAME_TOO_LONG", "파일 이름은 UTF-8 기준 255바이트 이하여야 합니다.", version);
        int dot = name.lastIndexOf('.');
        if (dot < 0) throw error("FILE_EXTENSION_REQUIRED", "확장자가 없는 파일은 보낼 수 없습니다.", version);
        if (dot == name.length() - 1) throw error("FILE_NAME_TRAILING_DOT", "이름이 점으로 끝나는 파일은 보낼 수 없습니다.", version);
        if (dot == 0) throw error("HIDDEN_FILE_NAME", "확장자를 구분할 수 없는 단독 숨김 이름은 보낼 수 없습니다.", version);
        String extension = name.substring(dot + 1);
        if (!extension.matches("[A-Za-z0-9]{1,20}"))
            throw error("INVALID_FILE_EXTENSION", "마지막 확장자는 영문·숫자 1~20자여야 합니다.", version);
        return extension.toLowerCase(Locale.ROOT);
    }
    private static UploadException error(String code, String message, long version) {
        return new UploadException(HttpStatus.BAD_REQUEST, code, message, version, null);
    }
}
