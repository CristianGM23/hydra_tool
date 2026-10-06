package com.example.editor.artifact.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HydraArtifactTransactionServiceTests {
    @TempDir Path temporary;

    @Test void removesJournalAfterSuccessfulOperation() {
        HydraArtifactTransactionService service = new HydraArtifactTransactionService(temporary, Clock.systemUTC());
        service.execute(HydraArtifactTransactionOperation.UPDATE, "artifact-one", List.of("a.json"), () -> { });
        assertThat(service.pending()).isEmpty();
    }

    @Test void keepsRecoveryJournalAfterFailure() {
        HydraArtifactTransactionService service = new HydraArtifactTransactionService(temporary, Clock.systemUTC());
        assertThatThrownBy(() -> service.execute(HydraArtifactTransactionOperation.MIGRATE, "artifact-two", List.of("b.json"),
                () -> { throw new IllegalStateException("write failed"); }))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("write failed");
        assertThat(service.pending()).singleElement().satisfies(journal -> {
            assertThat(journal.status()).isEqualTo(HydraArtifactTransactionStatus.RECOVERY_REQUIRED);
            assertThat(journal.subjectId()).isEqualTo("artifact-two");
            assertThat(journal.message()).contains("write failed");
        });
    }
}
