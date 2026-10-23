package dev.mina.flow.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@WebMvcTest(controllers=SessionController.class,properties={"app.demo-user=reviewer","app.demo-password=only-disposable-test-password"})
@Import(SessionSecurity.class)
class SessionSecurityTest {
    @Autowired MockMvc mvc;
    @Test void cookieSessionRequiresCsrfAndRotatesOnLoginAndLogout()throws Exception {
        var first=mvc.perform(get("/api/session")).andExpect(status().isOk()).andExpect(jsonPath("authenticated").value(false)).andReturn();
        MockHttpSession session=(MockHttpSession)first.getRequest().getSession(false);
        String csrf=new com.fasterxml.jackson.databind.ObjectMapper().readTree(first.getResponse().getContentAsString()).get("csrfToken").asText();
        String oldId=session.getId();
        mvc.perform(post("/api/session/login").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"reviewer\",\"password\":\"only-disposable-test-password\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("error.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/session/login").session(session).header("X-CSRF-TOKEN",csrf).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"reviewer\",\"password\":\"only-disposable-test-password\"}"))
            .andExpect(status().isNoContent());
        assertThat(session.getId()).isNotEqualTo(oldId);
        var renewed=mvc.perform(get("/api/session").session(session)).andExpect(jsonPath("authenticated").value(true)).andExpect(jsonPath("username").value("reviewer")).andReturn();
        String fresh=new com.fasterxml.jackson.databind.ObjectMapper().readTree(renewed.getResponse().getContentAsString()).get("csrfToken").asText();
        mvc.perform(post("/api/session/logout").session(session).header("X-CSRF-TOKEN",csrf)).andExpect(status().isForbidden());
        mvc.perform(post("/api/session/logout").session(session).header("X-CSRF-TOKEN",fresh)).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }
}
