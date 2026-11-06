package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * File-level recovery component. Until the jar 15 it quarantined a pair as soon as a read reported a parse failure
 * (recoverIfMalformed, tested here before); the user decided on 30/10 that a read never moves documents, so that entry point is gone and
 * the moving is covered, with fingerprints and manifests, by GamificationExplicitRecoveryTests. What stays here is the component contract:
 * bounded locks, reading without changes, the identifier policy and the confinement of every path to its metamodel.
 */
class GamificationCorruptDocumentRecoveryTests {
    @TempDir Path temp;

    @Test
    void fingerprintsStoredDocumentsWithoutChangingThemAndKeepsBoundedLockState() throws Exception {
        GamificationProperties properties = new GamificationProperties();
        properties.setDirectory(temp);
        GamificationCorruptDocumentRecovery recovery = new GamificationCorruptDocumentRecovery(properties);
        Path definitions = temp.resolve("people.gamification-definitions.json");
        Files.writeString(definitions, "{broken");
        long modified = Files.getLastModifiedTime(definitions).toMillis();

        GamificationCorruptDocumentRecovery.StoredFile stored = recovery.stored("people", GamificationCorruptDocumentRecovery.DEFINITIONS);
        GamificationCorruptDocumentRecovery.StoredFile missing = recovery.stored("people", GamificationCorruptDocumentRecovery.BINDINGS);

        assertThat(stored.present()).isTrue();
        assertThat(stored.etag()).isEqualTo(GamificationStoredPairFixture.etag(definitions));
        assertThat(stored.fileName()).isEqualTo("people.gamification-definitions.json");
        assertThat(missing.present()).isFalse();
        assertThat(definitions).hasContent("{broken");
        assertThat(Files.getLastModifiedTime(definitions).toMillis()).isEqualTo(modified);
        assertThat(recovery.quarantines("people")).isEmpty();
        assertThat(temp.resolve(".invalid-recovery")).doesNotExist();
        assertThat(recovery.stripeCount()).isEqualTo(64);
    }

    @Test
    void aDisabledRecoveryReadsNothingAndRefusesToQuarantine() {
        GamificationCorruptDocumentRecovery recovery = GamificationCorruptDocumentRecovery.disabled();
        assertThat(recovery.stored("people", GamificationCorruptDocumentRecovery.DEFINITIONS).present()).isFalse();
        assertThat(recovery.quarantines("people")).isEmpty();
        assertThatThrownBy(() -> recovery.begin("people", List.of(), ""))
                .isInstanceOf(GamificationApplicationException.class);
    }

    @Test
    void rejectsMetamodelIdsThatWouldEscapeTheGamificationDirectory() throws Exception {
        GamificationProperties properties = new GamificationProperties();
        properties.setDirectory(temp);
        GamificationCorruptDocumentRecovery recovery = new GamificationCorruptDocumentRecovery(properties);
        Files.writeString(temp.resolve("safe.gamification-definitions.json"), "{broken");

        assertThatThrownBy(() -> recovery.stored("../safe", GamificationCorruptDocumentRecovery.DEFINITIONS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recovery.quarantines("../safe")).isInstanceOf(IllegalArgumentException.class);
        assertThat(recovery.quarantined("safe", "..", "safe.gamification-definitions.json")).isEmpty();
        assertThat(recovery.quarantined("safe", "2026", "../../safe.gamification-definitions.json")).isEmpty();
    }
}
