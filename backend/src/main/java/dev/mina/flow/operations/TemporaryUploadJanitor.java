package dev.mina.flow.operations;

import dev.mina.flow.upload.UploadStorage;
import java.time.Duration;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class TemporaryUploadJanitor {
    private static final Logger log=LoggerFactory.getLogger(TemporaryUploadJanitor.class);
    private final UploadStorage storage;private final Duration age;
    public TemporaryUploadJanitor(UploadStorage storage,@Value("${app.temporary-max-age-hours:24}") long hours) {
        if(hours<1)throw new IllegalArgumentException("Temporary cleanup age must be at least one hour");
        this.storage=storage;this.age=Duration.ofHours(hours);
    }
    @Scheduled(initialDelayString="${app.cleanup-initial-delay-ms:60000}",fixedDelayString="${app.cleanup-delay-ms:3600000}")
    public void clean() {
        try { int count=storage.removeExpiredTemporary(age,100);log.atInfo().addKeyValue("event","temporary_cleanup").addKeyValue("removed",count).log("temporary_cleanup"); }
        catch(java.io.IOException error){log.atWarn().addKeyValue("event","temporary_cleanup_failed").addKeyValue("errorType",error.getClass().getSimpleName()).log("temporary_cleanup_failed");}
    }
}
