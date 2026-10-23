package dev.mina.flow;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Same-origin browser writes; not authentication for non-browser API clients. */
@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE+10)
public class BrowserOriginFilter extends OncePerRequestFilter {
    @org.springframework.beans.factory.annotation.Value("${app.allowed-origins:}") private String allowedOrigins = "";
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/") && Set.of("POST", "PUT", "DELETE").contains(request.getMethod())) {
            String origin = request.getHeader("Origin");
            String expected = request.getScheme() + "://" + request.getHeader("Host");
            boolean configured = origin != null && java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim).anyMatch(origin::equals);
            if ((origin != null && !origin.equals(expected) && !configured) || ("cross-site".equals(request.getHeader("Sec-Fetch-Site")) && !configured)) {
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Cache-Control", "no-store");
                response.getWriter().write("{\"error\":{\"code\":\"ORIGIN_NOT_ALLOWED\",\"message\":\"같은 출처의 화면에서 요청해 주세요.\",\"requestId\":\"" + UUID.randomUUID() + "\"}}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
