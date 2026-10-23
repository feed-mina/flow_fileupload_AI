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
        assertThat(exchange("/custom", HttpMethod.POST, Map.of("extension", "a".repeat(21)), "\"0\"").getStatusCode().value()).isEqualTo(400);
        assertThat(exchange("/fixed/exe", HttpMethod.PUT, Map.of(), "\"0\"").getStatusCode().value()).isEqualTo(400);
        assertThat(get().getBody().get("version").asLong()).isZero();
    }

    private ResponseEntity<JsonNode> get() { return rest.getForEntity("/api/policy", JsonNode.class); }

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
