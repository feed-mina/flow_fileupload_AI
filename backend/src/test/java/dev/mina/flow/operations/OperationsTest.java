package dev.mina.flow.operations;

import dev.mina.flow.upload.UploadStorage;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class OperationsTest {
    @TempDir Path root;
    @Test void expiredCrashFilesAreRemovedButActiveAndReadyFilesRemain() throws Exception {
        UploadStorage storage=new UploadStorage(root.toString());
        Path active=storage.temporary(),old=root.resolve("pending-crash.tmp"),ready=root.resolve("123.bin"),young=root.resolve("pending-young.tmp");
        Files.writeString(old,"old");Files.writeString(ready,"saved");Files.writeString(young,"new");
        FileTime past=FileTime.from(Instant.now().minus(Duration.ofHours(25)));
        Files.setLastModifiedTime(old,past);Files.setLastModifiedTime(active,past);Files.setLastModifiedTime(ready,past);
        assertThat(storage.removeExpiredTemporary(Duration.ofHours(24),100)).isEqualTo(1);
        assertThat(Files.exists(old)).isFalse();assertThat(Files.exists(active)).isTrue();assertThat(Files.exists(ready)).isTrue();assertThat(Files.exists(young)).isTrue();
    }
    public static class CrashWriter {
        public static void main(String[] args) throws Exception {
            Path p=Path.of(args[0]).resolve("pending-killed.tmp");Files.writeString(p,"interrupted");
            System.out.println("STAGED");System.out.flush();Thread.sleep(60000);
        }
    }
    @Test void actualKilledProcessLeavesTemporaryThatNextInstanceExpires() throws Exception {
        String java=Path.of(System.getProperty("java.home"),"bin","java").toString();
        Process child=new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),CrashWriter.class.getName(),root.toString()).redirectErrorStream(true).start();
        try {
            assertThat(child.inputReader().readLine()).isEqualTo("STAGED");
            child.destroyForcibly();assertThat(child.waitFor(10,TimeUnit.SECONDS)).isTrue();
            Path old=root.resolve("pending-killed.tmp");assertThat(Files.exists(old)).isTrue();
            Files.setLastModifiedTime(old,FileTime.from(Instant.now().minus(Duration.ofHours(25))));
            assertThat(new UploadStorage(root.toString()).removeExpiredTemporary(Duration.ofHours(24),100)).isEqualTo(1);
            assertThat(Files.exists(old)).isFalse();
        }finally{child.destroyForcibly();}
    }
    @Test void rateLimitHasRetryAfterAndDoesNotTrustForwardedAddress()throws Exception {
        TrafficGuard guard=new TrafficGuard(1,1,Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"),ZoneOffset.UTC));
        MockHttpServletRequest first=new MockHttpServletRequest("POST","/api/policy/custom");first.setRemoteAddr("127.0.0.1");
        guard.doFilter(first,new MockHttpServletResponse(),(q,s)->{});
        MockHttpServletRequest second=new MockHttpServletRequest("POST","/api/policy/custom");second.setRemoteAddr("127.0.0.1");second.addHeader("X-Forwarded-For","1.2.3.4");
        MockHttpServletResponse response=new MockHttpServletResponse();guard.doFilter(second,response,(q,s)->{throw new AssertionError("must reject");});
        assertThat(response.getStatus()).isEqualTo(429);assertThat(response.getHeader("Retry-After")).isEqualTo("60");assertThat(response.getContentAsString()).contains("REQUEST_LIMIT_REACHED");
    }
    @Test void concurrentUploadCapacityIsReleasedAfterFailure()throws Exception {
        TrafficGuard guard=new TrafficGuard(10,1);CountDownLatch entered=new CountDownLatch(1),finish=new CountDownLatch(1);
        var first=CompletableFuture.runAsync(()->{try{guard.doFilter(new MockHttpServletRequest("POST","/api/uploads"),new MockHttpServletResponse(),(q,s)->{entered.countDown();try{finish.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});}catch(Exception e){throw new RuntimeException(e);}});
        assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();var response=new MockHttpServletResponse();
        guard.doFilter(new MockHttpServletRequest("POST","/api/uploads"),response,(q,s)->{throw new AssertionError("busy");});
        assertThat(response.getStatus()).isEqualTo(429);assertThat(response.getContentAsString()).contains("UPLOAD_BUSY");finish.countDown();first.get(5,TimeUnit.SECONDS);
        guard.doFilter(new MockHttpServletRequest("POST","/api/uploads"),new MockHttpServletResponse(),(q,s)->{});
    }
    @Test void requestLogContainsMetricsButNoFilenameTokenOrIp()throws Exception {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(RequestLogFilter.class);
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();logger.addAppender(appender);
        try {
            var req=new MockHttpServletRequest("POST","/api/uploads");req.addHeader("Authorization","private-token");req.setQueryString("filename=private-name");req.setAttribute("policyVersion",3L);req.setAttribute("uploadBytes",12L);
            var response=new MockHttpServletResponse();new RequestLogFilter().doFilter(req,response,(q,s)->response.setStatus(201));
            var event=appender.list.get(0);String fields=event.getKeyValuePairs().toString();
            assertThat(fields).contains("durationMs","policyVersion","bytes","201").doesNotContain("private-name","private-token","127.0.0.1");
            assertThat(response.getHeader("X-Request-Id")).isNotBlank();assertThat(org.slf4j.MDC.get("requestId")).isNull();
        }finally{logger.detachAppender(appender);}
    }
}
