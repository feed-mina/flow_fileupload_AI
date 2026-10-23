package dev.mina.flow.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SessionSecurity {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(@Value("${app.demo-user:}") String user,
            @Value("${app.demo-password:}") String password, PasswordEncoder encoder) {
        if (user.isBlank() || password.isBlank()) return new InMemoryUserDetailsManager();
        if (password.length() < 12) throw new IllegalArgumentException("Demo password must contain at least 12 characters");
        return new InMemoryUserDetailsManager(User.withUsername(user).password(encoder.encode(password)).roles("DEMO").build());
    }
    @Bean HttpSessionSecurityContextRepository contexts() { return new HttpSessionSecurityContextRepository(); }
    @Bean SecurityFilterChain chain(HttpSecurity http, CorsConfigurationSource cors) throws Exception {
        return http.cors(c -> c.configurationSource(cors))
            .csrf(c -> {})
            // BE-S11 is intentionally deferred: authentication does not isolate policies or authorize editing.
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .securityContext(c -> c.securityContextRepository(contexts()))
            .formLogin(c -> c.disable()).httpBasic(c -> c.disable())
            .logout(c -> c.logoutUrl("/api/session/logout").invalidateHttpSession(true)
                .logoutSuccessHandler((req, res, auth) -> { res.setHeader("Cache-Control", "no-store"); res.setStatus(204); }))
            .exceptionHandling(c -> c.accessDeniedHandler((req, res, error) -> {
                res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                res.setHeader("Cache-Control", "no-store");
                res.getWriter().write("{\"error\":{\"code\":\"CSRF_INVALID\",\"message\":\"보안 세션이 만료되었습니다. 최신 화면을 조회한 뒤 다시 시도해 주세요.\"}}");
            })).build();
    }
    @Bean CorsConfigurationSource cors(@Value("${app.allowed-origins:}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(java.util.Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "If-Match", "X-CSRF-TOKEN", "X-Upload-Request-Id"));
        config.setExposedHeaders(List.of("ETag", "Retry-After", "X-Request-Id"));
        config.setAllowCredentials(true); config.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config); return source;
    }
}
