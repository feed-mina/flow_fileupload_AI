package dev.mina.flow.upload;

import dev.mina.flow.policy.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadServiceTest {
    @TempDir Path root;
    @Test void databaseUnavailableFailsClosedBeforeFileCreation() throws Exception {
        PolicyService policies = mock(PolicyService.class);
        when(policies.get()).thenThrow(new CannotCreateTransactionException("test unavailable"));
        UploadStorage storage = spy(new UploadStorage(root.toString()));
        UploadService service = new UploadService(policies,storage,mock(JdbcTemplate.class),mock(PlatformTransactionManager.class));
        assertThatThrownBy(() -> service.upload("a.txt",1,new ByteArrayInputStream(new byte[]{1}))).isInstanceOf(CannotCreateTransactionException.class);
        verify(storage,never()).temporary();
    }
    @Test void actualReadBytesCannotExceedLimitEvenIfDeclaredSizeIsSmall() throws Exception {
        UploadStorage storage = new UploadStorage(root.toString());
        Path temporary = storage.temporary();
        assertThatThrownBy(() -> storage.copy(new ByteArrayInputStream(new byte[10485761]),temporary))
                .isInstanceOf(UploadStorage.FileTooLargeException.class);
    }
}
