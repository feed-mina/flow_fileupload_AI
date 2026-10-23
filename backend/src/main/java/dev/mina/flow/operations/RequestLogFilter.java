package dev.mina.flow.operations;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {
    private static final Logger log=LoggerFactory.getLogger(RequestLogFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        if (!req.getRequestURI().startsWith("/api/")) { chain.doFilter(req,res); return; }
        long start=System.nanoTime();
        MDC.put("requestId",UUID.randomUUID().toString());
        res.setHeader("X-Request-Id",MDC.get("requestId"));
        try { chain.doFilter(req,res); }
        finally {
            String route=req.getRequestURI().startsWith("/api/uploads")?"uploads":req.getRequestURI().startsWith("/api/policy")?"policy":req.getRequestURI().startsWith("/api/session")?"session":"other";
            log.atInfo().addKeyValue("event","api_request").addKeyValue("route",route)
                .addKeyValue("method",req.getMethod()).addKeyValue("status",res.getStatus())
                .addKeyValue("resultCode",req.getAttribute("resultCode")==null?"HTTP_"+res.getStatus():req.getAttribute("resultCode"))
                .addKeyValue("policyVersion",req.getAttribute("policyVersion"))
                .addKeyValue("bytes",req.getAttribute("uploadBytes"))
                .addKeyValue("durationMs",(System.nanoTime()-start)/1_000_000.0).log("api_request");
            MDC.clear();
        }
    }
    public static void result(String code,Long version,Long bytes) {
        var attrs=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            var req=servlet.getRequest(); req.setAttribute("resultCode",code);
            if(version!=null)req.setAttribute("policyVersion",version);
            if(bytes!=null)req.setAttribute("uploadBytes",bytes);
        }
    }
    public static String traceId() {
        String id=MDC.get("requestId");return id==null?UUID.randomUUID().toString():id;
    }
}
