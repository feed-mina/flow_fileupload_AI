package dev.mina.flow.upload;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {
    private final UploadService service;
    public UploadController(UploadService service) { this.service = service; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadService.UploadResult> upload(HttpServletRequest request,
            @RequestHeader(value = "X-Upload-Request-Id", required = false) String requestId) throws IOException, ServletException {
        UUID lookupId = requestId == null ? UUID.randomUUID() : parseId(requestId);
        List<Part> parts = List.copyOf(request.getParts());
        List<Part> files = parts.stream().filter(part -> part.getSubmittedFileName() != null).toList();
        if (files.isEmpty()) throw invalid("FILE_REQUIRED", "보낼 파일을 선택해 주세요.");
        if (files.size() != 1) throw invalid("FILE_COUNT_INVALID", "한 번에 파일 1개만 보낼 수 있습니다.");
        Part file = files.get(0);
        if (!file.getName().equals("file")) throw invalid("FILE_FIELD_REQUIRED", "file 필드로 파일을 보내 주세요.");
        if (parts.size() != 1) throw invalid("UNEXPECTED_PART", "파일 이외의 추가 항목은 보낼 수 없습니다.");
        try (InputStream input = file.getInputStream()) {
            return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store")
                    .body(service.upload(file.getSubmittedFileName(), file.getSize(), input, lookupId));
        }
    }
    @GetMapping("/status/{requestId}")
    public ResponseEntity<UploadService.UploadResult> status(@PathVariable String requestId) {
        UploadService.UploadResult result = service.find(parseId(requestId));
        if (result == null) throw new UploadException(HttpStatus.NOT_FOUND, "UPLOAD_NOT_CONFIRMED",
                "완료된 저장 결과를 아직 찾지 못했습니다. 처리 중이거나 저장에 실패했을 수 있습니다. 자동 재전송하지 않습니다.", null, null);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(result);
    }
    private UUID parseId(String value) {
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw invalid("INVALID_UPLOAD_REQUEST_ID", "업로드 요청 번호 형식이 올바르지 않습니다.");
        return UUID.fromString(value);
    }
    private UploadException invalid(String code, String message) { return new UploadException(HttpStatus.BAD_REQUEST, code, message, null, null); }
}
