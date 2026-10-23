package dev.mina.flow.policy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PolicyApiTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetOnlyIsolatedTestDatabase() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_audit ON policy_change");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_version ON policy_scope");
        jdbc.update("DELETE FROM policy_change");
        jdbc.update("DELETE FROM extension_policy WHERE kind = 'CUSTOM'");
        jdbc.update("UPDATE extension_policy SET enabled = FALSE WHERE kind = 'FIXED'");
        jdbc.update("UPDATE policy_scope SET version = 0 WHERE id = 1");
    }

    @Test
    @DisplayName("T01: 최초 고정 7종은 정확한 순서로 모두 미선택")
    void initialPolicy() {
        ResponseEntity<JsonNode> response = get();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        List<String> names = new ArrayList<>();
        for (JsonNode fixed : body.get("fixed")) {
            names.add(fixed.get("extension").asText());
            assertThat(fixed.get("enabled").asBoolean()).isFalse();
        }
        assertThat(names).containsExactly("bat", "cmd", "com", "cpl", "exe", "scr", "js");
        assertThat(body.get("custom").size()).isZero();
        assertThat(response.getHeaders().getETag()).isEqualTo("\"0\"");
    }

    @Test
    @DisplayName("T02: 고정 선택 저장 후 별도 HTTP 조회에도 유지")
    void fixedSelectionPersistsAcrossRequests() {
        ResponseEntity<JsonNode> saved = exchange("/fixed/exe", HttpMethod.PUT, Map.of("enabled", true), "\"0\"");
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> reloaded = get();
        assertThat(fixed(reloaded.getBody(), "exe")).isTrue();
        assertThat(reloaded.getHeaders().getETag()).isEqualTo("\"1\"");
        assertThat(jdbc.queryForObject("SELECT enabled FROM extension_policy WHERE extension = 'exe'", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("T04: sh 추가 후 DB와 재조회 목록에 저장")
    void customAdditionPersists() {
        ResponseEntity<JsonNode> added = exchange("/custom", HttpMethod.POST, Map.of("extension", "sh"), "\"0\"");
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode reloaded = get().getBody();
        assertThat(reloaded.get("customCount").asInt()).isEqualTo(1);
        assertThat(reloaded.get("custom").get(0).get("extension").asText()).isEqualTo("sh");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM extension_policy WHERE kind = 'CUSTOM' AND extension = 'sh'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("버전 누락은 428, 오래된 버전은 412이며 값은 변경 안 됨")
    void requiresCurrentVersion() {
        assertThat(exchange("/fixed/exe", HttpMethod.PUT, Map.of("enabled", true), null).getStatusCode().value()).isEqualTo(428);
        assertThat(exchange("/fixed/exe", HttpMethod.PUT, Map.of("enabled", true), "\"9\"").getStatusCode().value()).isEqualTo(412);
        assertThat(fixed(get().getBody(), "exe")).isFalse();
    }

    @Test
    @DisplayName("임시 입력 기준: 정규화 중복과 고정 이름 충돌은 409")
    void rejectsDuplicateAndFixedNameWithoutAutoEnabling() {
        assertThat(exchange("/custom", HttpMethod.POST, Map.of("extension", ".SH"), "\"0\"").getStatusCode().value()).isEqualTo(201);
        assertThat(exchange("/custom", HttpMethod.POST, Map.of("extension", "sh"), "\"1\"").getStatusCode().value()).isEqualTo(409);
        assertThat(exchange("/custom", HttpMethod.POST, Map.of("extension", "exe"), "\"1\"").getStatusCode().value()).isEqualTo(409);
        assertThat(fixed(get().getBody(), "exe")).isFalse();
        assertThat(get().getBody().get("version").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("길이 초과와 enabled 누락은 저장하지 않고 400")
    void rejectsInvalidRequests() {
        for (String invalid : List.of("K", "ｓｈ", "tar.gz", "s h", "..sh")) {
            assertThat(exchange("/custom", HttpMethod.POST, Map.of("extension", invalid), "\"0\"").getStatusCode().value()).isEqualTo(400);
        }
        assertThat(exchange("/custom", HttpMethod.POST, Map.of("extension", "a".repeat(21)), "\"0\"").getStatusCode().value()).isEqualTo(400);
        assertThat(exchange("/fixed/exe", HttpMethod.PUT, Map.of(), "\"0\"").getStatusCode().value()).isEqualTo(400);
        assertThat(get().getBody().get("version").asLong()).isZero();
    }

    @Test
    void customLengthDeleteAndDuplicateAtLimit() {
        String twenty = "a".repeat(20);
        var added = exchange("/custom", HttpMethod.POST, Map.of("extension", twenty), "\"0\"");
        assertThat(added.getStatusCode().value()).isEqualTo(201);
        long id = added.getBody().get("custom").get(0).get("id").asLong();
        assertThat(exchange("/custom/"+id,HttpMethod.DELETE,null,"\"1\"").getStatusCode().value()).isEqualTo(200);
        assertThat(get().getBody().get("customCount").asInt()).isZero();
        assertThat(exchange("/custom/"+id,HttpMethod.DELETE,null,"\"2\"").getStatusCode().value()).isEqualTo(404);
        for (int i=0;i<200;i++) jdbc.update("INSERT INTO extension_policy(policy_id,extension,kind,enabled) VALUES(1,?,'CUSTOM',TRUE)","x"+i);
        var duplicate = exchange("/custom",HttpMethod.POST,Map.of("extension","X0"),"\"2\"");
        assertThat(duplicate.getBody().get("error").get("code").asText()).isEqualTo("DUPLICATE_EXTENSION");
        assertThat(exchange("/custom",HttpMethod.POST,Map.of("extension","new"),"\"2\"").getBody().get("error").get("code").asText()).isEqualTo("CUSTOM_LIMIT_REACHED");
    }

    @Test
    void concurrentAdditionAt199CannotExceed200() {
        for (int i=0;i<199;i++) jdbc.update("INSERT INTO extension_policy(policy_id,extension,kind,enabled) VALUES(1,?,'CUSTOM',TRUE)","x"+i);
        var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> exchange("/custom",HttpMethod.POST,Map.of("extension","alpha"),"\"0\""));
        var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> exchange("/custom",HttpMethod.POST,Map.of("extension","beta"),"\"0\""));
        assertThat(List.of(first.join().getStatusCode().value(),second.join().getStatusCode().value())).containsExactlyInAnyOrder(201,412);
        assertThat(get().getBody().get("customCount").asInt()).isEqualTo(200);
    }

    private ResponseEntity<JsonNode> get() { return rest.getForEntity("/api/policy", JsonNode.class); }

    @Test
    void policyVersionAndAuditCommitTogetherAndRepeatedDeleteHasNoExtraChange() {
        var added = exchange("/custom",HttpMethod.POST,Map.of("extension","sh"),"\"0\"");
        long id = added.getBody().get("custom").get(0).get("id").asLong();
        assertThat(jdbc.queryForObject("SELECT action FROM policy_change WHERE policy_version=1",String.class)).isEqualTo("CUSTOM_ADDED");
        assertThat(exchange("/custom/"+id,HttpMethod.DELETE,null,"\"1\"").getStatusCode().value()).isEqualTo(200);
        assertThat(exchange("/custom/"+id,HttpMethod.DELETE,null,"\"2\"").getBody().get("error").get("code").asText()).isEqualTo("CUSTOM_NOT_FOUND");
        assertThat(get().getBody().get("version").asLong()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM policy_change",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT extension FROM policy_change WHERE action='CUSTOM_DELETED'",String.class)).isEqualTo("sh");
    }

    @Test
    void auditFailureRollsBackFixedAddAndDeleteIncludingVersion() {
        var added = exchange("/custom",HttpMethod.POST,Map.of("extension","sh"),"\"0\"");
        long id = added.getBody().get("custom").get(0).get("id").asLong();
        jdbc.execute("CREATE OR REPLACE FUNCTION fail_ux_write() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'private audit path /app/uploads'; END $$");
        jdbc.execute("CREATE TRIGGER fail_audit BEFORE INSERT ON policy_change FOR EACH ROW EXECUTE FUNCTION fail_ux_write()");
        try {
            for (var result : List.of(exchange("/fixed/exe",HttpMethod.PUT,Map.of("enabled",true),"\"1\""),
                    exchange("/custom",HttpMethod.POST,Map.of("extension","abc"),"\"1\""),
                    exchange("/custom/"+id,HttpMethod.DELETE,null,"\"1\""))) {
                assertThat(result.getStatusCode().value()).isEqualTo(503);
                assertThat(result.getBody().get("error").get("requestId").asText()).isNotBlank();
                assertThat(result.getBody().toString()).doesNotContain("private audit", "/app/uploads", "SQLException");
            }
            assertThat(fixed(get().getBody(),"exe")).isFalse();
            assertThat(get().getBody().get("version").asLong()).isEqualTo(1);
            assertThat(get().getBody().get("customCount").asInt()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM policy_change",Integer.class)).isEqualTo(1);
        } finally { jdbc.execute("DROP TRIGGER fail_audit ON policy_change"); }
    }

    @Test
    void versionFailureRollsBackPolicyAndWhitespaceInputIdentifiesField() {
        var invalid = exchange("/custom",HttpMethod.POST,Map.of("extension","   "),"\"0\"");
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(invalid.getBody().get("error").get("code").asText()).isEqualTo("INVALID_EXTENSION");
        assertThat(invalid.getBody().get("error").get("field").asText()).isEqualTo("extension");
        jdbc.execute("CREATE OR REPLACE FUNCTION fail_ux_write() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'simulated version write failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_version BEFORE UPDATE ON policy_scope FOR EACH ROW EXECUTE FUNCTION fail_ux_write()");
        try {
            assertThat(exchange("/fixed/exe",HttpMethod.PUT,Map.of("enabled",true),"\"0\"").getStatusCode().value()).isEqualTo(503);
            assertThat(fixed(get().getBody(),"exe")).isFalse();
            assertThat(get().getBody().get("version").asLong()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM policy_change",Integer.class)).isZero();
        } finally { jdbc.execute("DROP TRIGGER fail_version ON policy_scope"); }
    }

    private ResponseEntity<JsonNode> exchange(String suffix, HttpMethod method, Object body, String version) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (version != null) headers.set(HttpHeaders.IF_MATCH, version);
        return rest.exchange("/api/policy" + suffix, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private boolean fixed(JsonNode policy, String extension) {
        for (JsonNode fixed : policy.get("fixed")) {
            if (extension.equals(fixed.get("extension").asText())) return fixed.get("enabled").asBoolean();
        }
        throw new AssertionError("Missing fixed extension " + extension);
    }
}
