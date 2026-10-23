package dev.mina.flow.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/session")
public class SessionController {
    private final UserDetailsService users;
    private final PasswordEncoder encoder;
    private final HttpSessionSecurityContextRepository contexts;
    private final boolean available;
    public SessionController(UserDetailsService users, PasswordEncoder encoder, HttpSessionSecurityContextRepository contexts,
            @Value("${app.demo-user:}") String user, @Value("${app.demo-password:}") String password) {
        this.users=users; this.encoder=encoder; this.contexts=contexts; this.available=!user.isBlank()&&!password.isBlank();
    }
    @GetMapping
    public ResponseEntity<Map<String,Object>> session(CsrfToken csrf, Authentication auth) {
        boolean loggedIn=auth!=null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of(
            "csrfToken",csrf.getToken(), "authenticated",loggedIn, "loginAvailable",available,
            "username",loggedIn ? auth.getName() : ""));
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Credentials input, HttpServletRequest request, HttpServletResponse response) {
        if (!available) return ResponseEntity.status(503).body(Map.of("error",Map.of("code","LOGIN_NOT_CONFIGURED","message","로그인 계정이 설정되지 않았습니다.")));
        boolean valid=false;
        try { valid=input.username()!=null && input.password()!=null && input.password().length()<=200
            && encoder.matches(input.password(),users.loadUserByUsername(input.username()).getPassword()); }
        catch (org.springframework.security.core.userdetails.UsernameNotFoundException ignored) { encoder.matches("invalid",encoder.encode("dummy-password")); }
        if (!valid) return ResponseEntity.status(401).body(Map.of("error",Map.of("code","LOGIN_FAILED","message","아이디와 비밀번호를 확인해 주세요.")));
        request.getSession(true); request.changeSessionId();
        var auth=UsernamePasswordAuthenticationToken.authenticated(input.username(),null,List.of(new SimpleGrantedAuthority("ROLE_DEMO")));
        var context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth);
        SecurityContextHolder.setContext(context); contexts.saveContext(context,request,response);
        new HttpSessionCsrfTokenRepository().saveToken(null,request,response);
        return ResponseEntity.noContent().header("Cache-Control","no-store").build();
    }
    public record Credentials(String username, String password) {}
}
