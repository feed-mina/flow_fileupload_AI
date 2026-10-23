package dev.mina.flow.operations;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+20)
public class TrafficGuard extends OncePerRequestFilter {
    private final Map<String,Window> windows=new ConcurrentHashMap<>();
    private final Semaphore uploads;
    private final int perMinute;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public TrafficGuard(@Value("${app.write-requests-per-minute:120}") int perMinute,
            @Value("${app.concurrent-uploads:4}") int concurrentUploads) { this(perMinute,concurrentUploads,Clock.systemUTC()); }
    public TrafficGuard(int perMinute,int concurrentUploads,Clock clock) {
        if(perMinute<1||concurrentUploads<1)throw new IllegalArgumentException("Traffic limits must be positive");
        this.perMinute=perMinute;this.uploads=new Semaphore(concurrentUploads);this.clock=clock;
    }
    private record Window(long minute,int count) {}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        if (!req.getRequestURI().startsWith("/api/")||!java.util.Set.of("POST","PUT","DELETE").contains(req.getMethod())) { chain.doFilter(req,res);return; }
        long minute=clock.millis()/60_000;
        // Connection peer only. Untrusted X-Forwarded-For never selects a rate-limit bucket.
        String key=req.getRemoteAddr();
        if(windows.size()>10_000)windows.entrySet().removeIf(e->e.getValue().minute()<minute);
        if(!windows.containsKey(key)&&windows.size()>=10_000){reject(res,"REQUEST_LIMIT_REACHED");return;}
        Window next=windows.compute(key,(k,old)->new Window(minute,old==null||old.minute()!=minute?1:old.count()+1));
        if(next.count()>perMinute){reject(res,"REQUEST_LIMIT_REACHED");return;}
        boolean upload="/api/uploads".equals(req.getRequestURI())&&"POST".equals(req.getMethod());
        if(upload&&!uploads.tryAcquire()){reject(res,"UPLOAD_BUSY");return;}
        try{chain.doFilter(req,res);}finally{if(upload)uploads.release();}
    }
    private void reject(HttpServletResponse res,String code)throws IOException {
        res.setStatus(429);res.setContentType("application/json;charset=UTF-8");res.setHeader("Cache-Control","no-store");res.setHeader("Retry-After","60");
        RequestLogFilter.result(code,null,null);
        res.getWriter().write("{\"error\":{\"code\":\""+code+"\",\"message\":\"요청이 많습니다. 60초 뒤 다시 시도해 주세요. 자동 재전송하지 않습니다.\",\"retryAfterSeconds\":60}}");
    }
}
