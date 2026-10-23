package dev.mina.flow.policy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/policy")
public class PolicyController {
    private final PolicyService service;
    public PolicyController(PolicyService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<PolicyView> get() { return response(service.get(), HttpStatus.OK); }

    @PutMapping("/fixed/{extension}")
    public ResponseEntity<PolicyView> setFixed(@PathVariable String extension,
            @Valid @RequestBody FixedRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return response(service.setFixed(extension, request.enabled(), ifMatch), HttpStatus.OK);
    }

    @PostMapping("/custom")
    public ResponseEntity<PolicyView> addCustom(@RequestBody CustomRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return response(service.addCustom(request.extension(), ifMatch), HttpStatus.CREATED);
    }

    private ResponseEntity<PolicyView> response(PolicyView view, HttpStatus status) {
        return ResponseEntity.status(status).eTag("\"" + view.version() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(view);
    }

    public record FixedRequest(@NotNull Boolean enabled) {}
    public record CustomRequest(String extension) {}
}
