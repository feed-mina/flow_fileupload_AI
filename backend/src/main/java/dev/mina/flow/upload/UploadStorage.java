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
    public UploadStorage(@Value("${app.upload-root:./uploads}") String configuredRoot) throws IOException {
        Path configured = Path.of(configuredRoot).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(configured)) throw new IOException("Upload root must not be a symbolic link");
        Files.createDirectories(configured);
        this.root = configured.toRealPath();
    }
    public Path temporary() throws IOException { return Files.createTempFile(root, "pending-", ".tmp"); }
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
    public void remove(Path path) throws IOException { Files.deleteIfExists(path); }
    public static class FileTooLargeException extends IOException {}
}
