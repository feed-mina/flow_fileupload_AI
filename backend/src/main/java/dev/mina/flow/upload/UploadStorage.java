package dev.mina.flow.upload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UploadStorage {
    private final Path root;
    private final java.util.Set<Path> active = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public UploadStorage(@Value("${app.upload-root:./uploads}") String configuredRoot) throws IOException {
        Path configured = Path.of(configuredRoot).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(configured)) throw new IOException("Upload root must not be a symbolic link");
        Files.createDirectories(configured);
        this.root = configured.toRealPath();
    }
    public Path temporary() throws IOException {
        Path path=Files.createTempFile(root,"pending-",".tmp"); active.add(path); return path;
    }
    public int removeExpiredTemporary(java.time.Duration age,int limit) throws IOException {
        if(age.isZero()||age.isNegative()||limit<1)throw new IllegalArgumentException("Invalid cleanup limits");
        java.time.Instant cutoff=java.time.Instant.now().minus(age); int removed=0;
        try(var paths=Files.newDirectoryStream(root,"pending-*.tmp")) {
            for(Path path:paths) {
                if(removed>=limit)break;
                if(active.contains(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))continue;
                if(Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)&&Files.deleteIfExists(path))removed++;
            }
        }
        return removed;
    }
    public Path destination(UUID id) {
        Path target = root.resolve(id + ".bin").normalize();
        if (!target.getParent().equals(root)) throw new IllegalStateException("Invalid storage key");
        return target;
    }
    public long copy(InputStream input, Path temporary) throws IOException {
        long count = 0;
        try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1;) {
                count += read;
                if (count > UploadService.MAX_FILE_BYTES) throw new FileTooLargeException();
                output.write(buffer, 0, read);
            }
        }
        return count;
    }
    public void publish(Path temporary, Path destination) throws IOException {
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(destination.toString());
        try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, destination); }
    }
    public void remove(Path path) throws IOException { try { Files.deleteIfExists(path); } finally { active.remove(path); } }
    public static class FileTooLargeException extends IOException {}
}
