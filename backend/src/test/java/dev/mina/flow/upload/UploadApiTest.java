package dev.mina.flow.upload;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class UploadApiTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @TempDir static Path root;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("app.upload-root", () -> root.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate rest;
    @SpyBean UploadStorage storage;
    @BeforeEach void resetIsolatedData() throws IOException {
        jdbc.update("DELETE FROM upload_record");
        jdbc.update("DELETE FROM extension_policy WHERE kind = 'CUSTOM'");
        jdbc.update("UPDATE extension_policy SET enabled = FALSE");
        jdbc.update("UPDATE policy_scope SET version = 0");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_ready ON upload_record");
        try (var paths = Files.list(root)) { for (Path file : paths.toList()) Files.delete(file); }
    }
    private MockPart file(String name, byte[] bytes) { return new MockPart("file", name, bytes); }
    private void emptyStorage() throws IOException {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM upload_record", Integer.class)).isZero();
        try (var paths = Files.list(root)) { assertThat(paths.count()).isZero(); }
    }
    @Test void normalUploadStoresUuidBytesAndMetadata() throws Exception {
        String response = mvc.perform(multipart("/api/uploads").part(file("hello.txt", new byte[]{1,2,3})))
                .andExpect(status().isCreated()).andExpect(jsonPath("state").value("READY"))
                .andExpect(jsonPath("originalName").value("hello.txt")).andExpect(jsonPath("policyVersion").value(0))
                .andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();
        assertThat(Files.readAllBytes(root.resolve(id + ".bin"))).containsExactly(new byte[]{1,2,3});
        assertThat(jdbc.queryForObject("SELECT state FROM upload_record", String.class)).isEqualTo("READY");
        mvc.perform(get("/uploads/" + id + ".bin")).andExpect(status().isNotFound());
    }
    @Test void selectedExeBlocksDirectApiAndUnselectedExeIsAllowed() throws Exception {
        jdbc.update("UPDATE extension_policy SET enabled = TRUE WHERE extension = 'exe'");
        jdbc.update("UPDATE policy_scope SET version = 1");
        mvc.perform(multipart("/api/uploads").part(file("sample.EXE", new byte[]{77,90})))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("error.code").value("EXTENSION_BLOCKED"))
                .andExpect(jsonPath("error.extension").value("exe")).andExpect(jsonPath("error.policyVersion").value(1));
        emptyStorage();
        jdbc.update("UPDATE extension_policy SET enabled = FALSE WHERE extension = 'exe'");
        MockPart spoof = file("sample.EXE", new byte[]{77,90});
        spoof.getHeaders().setContentType(MediaType.IMAGE_JPEG);
        mvc.perform(multipart("/api/uploads").part(spoof)).andExpect(status().isCreated());
        // MIME is untrusted metadata; no hidden always-block-exe/content-scanner rule.
    }
    @ParameterizedTest
    @CsvSource({"README,FILE_EXTENSION_REQUIRED", ".env,HIDDEN_FILE_NAME", "file.,FILE_NAME_TRAILING_DOT", "../a.txt,INVALID_FILE_NAME", "C:\\a.txt,INVALID_FILE_NAME"})
    void invalidFilenameHasDistinctReason(String name, String code) throws Exception {
        mvc.perform(multipart("/api/uploads").part(file(name,new byte[]{1})))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error.code").value(code));
        emptyStorage();
    }
    @Test void lastSuffixAndUtf8ByteBoundaries() throws Exception {
        jdbc.update("UPDATE extension_policy SET enabled = TRUE WHERE extension = 'exe'");
        for (String name : new String[]{"report.exe.txt", "archive.tar.gz", "<img onerror=alert(1)>.txt", "a".repeat(251)+".txt", "가".repeat(83)+".txt"})
            mvc.perform(multipart("/api/uploads").part(file(name,new byte[]{1}))).andExpect(status().isCreated());
        mvc.perform(multipart("/api/uploads").part(file("가".repeat(84)+".txt",new byte[]{1})))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error.code").value("FILE_NAME_TOO_LONG"));
        mvc.perform(multipart("/api/uploads").part(file("a\u0001.txt",new byte[]{1})))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error.code").value("INVALID_FILE_NAME"));
    }
    @Test void missingEmptyWrongFieldAndMultipleFilesAreDifferent() throws Exception {
        mvc.perform(multipart("/api/uploads").part(new MockPart("note",new byte[]{1})))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error.code").value("FILE_REQUIRED"));
        mvc.perform(multipart("/api/uploads").part(file("empty.txt",new byte[0])))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error.code").value("FILE_EMPTY"));
        mvc.perform(multipart("/api/uploads").part(new MockPart("other","a.txt",new byte[]{1})))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error.code").value("FILE_FIELD_REQUIRED"));
        mvc.perform(multipart("/api/uploads").part(file("a.txt",new byte[]{1}),new MockPart("other","b.txt",new byte[]{2})))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error.code").value("FILE_COUNT_INVALID"));
        emptyStorage();
    }
    @Test void realHttpParserEnforcesSizeAtOneByteBoundary() {
        for (int size : new int[]{10485759,10485760,10485761}) {
            System.out.println("HTTP file boundary bytes=" + size);
            var parts = new LinkedMultiValueMap<String,Object>();
            parts.add("file",new ByteArrayResource(new byte[size]) { @Override public String getFilename(){return "boundary.txt";} });
            var headers = new HttpHeaders(); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            var response = rest.postForEntity("/api/uploads",new HttpEntity<>(parts,headers),String.class);
            System.out.println("HTTP file boundary status=" + response.getStatusCode().value());
            assertThat(response.getStatusCode().value()).isEqualTo(size > 10485760 ? 413 : 201);
        }
    }
    @Test void moveFailureRollsBackAndCleansStaging() throws Exception {
        doThrow(new IOException("injected move failure")).when(storage).publish(any(),any());
        mvc.perform(multipart("/api/uploads").part(file("a.txt",new byte[]{1})))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("error.code").value("FILE_STORAGE_FAILED"));
        emptyStorage();
    }
    @Test void databaseReadyFailureRollsBackAndRemovesPublishedFile() throws Exception {
        jdbc.execute("CREATE OR REPLACE FUNCTION reject_ready() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected ready failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_ready BEFORE UPDATE ON upload_record FOR EACH ROW EXECUTE FUNCTION reject_ready()");
        mvc.perform(multipart("/api/uploads").part(file("a.txt",new byte[]{1}))).andExpect(status().isServiceUnavailable());
        emptyStorage();
    }
    @Test void aCommittedSnapshotIsUsedEvenWhenPolicyChangesDuringCopy() throws Exception {
        doAnswer(invocation -> {
            jdbc.update("INSERT INTO extension_policy(policy_id,extension,kind,enabled) VALUES(1,'txt','CUSTOM',TRUE)");
            jdbc.update("UPDATE policy_scope SET version = 1");
            return invocation.callRealMethod();
        }).when(storage).copy(any(),any());
        mvc.perform(multipart("/api/uploads").part(file("a.txt",new byte[]{1})))
                .andExpect(status().isCreated()).andExpect(jsonPath("policyVersion").value(0));
        mvc.perform(multipart("/api/uploads").part(file("b.txt",new byte[]{1})))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("error.policyVersion").value(1));
    }
    @Test void crossOriginBrowserWriteIsRejected() throws Exception {
        mvc.perform(multipart("/api/uploads").part(file("a.txt",new byte[]{1})).header("Origin","https://untrusted.invalid"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("error.code").value("ORIGIN_NOT_ALLOWED"));
        emptyStorage();
    }
}
