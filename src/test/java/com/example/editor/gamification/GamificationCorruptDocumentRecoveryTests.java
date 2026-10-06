package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException.Reason;

class GamificationCorruptDocumentRecoveryTests {
    @TempDir Path temp;

    @Test
    void quarantinesMalformedPairAndKeepsBoundedLockState() throws Exception {
        GamificationProperties properties = new GamificationProperties();
        properties.setDirectory(temp);
        GamificationCorruptDocumentRecovery recovery = new GamificationCorruptDocumentRecovery(properties);
        Path definitions = temp.resolve("people.gamification-definitions.json");
        Path bindings = temp.resolve("people.gamification-bindings.json");
        Files.writeString(definitions, "{broken");
        Files.writeString(bindings, "{also-broken");

        boolean recovered = recovery.recoverIfMalformed("people", new CanonicalDocumentStoreException(
                Reason.STORAGE_ERROR, "Could not parse document: " + definitions));

        assertThat(recovered).isTrue();
        assertThat(definitions).doesNotExist();
        assertThat(bindings).doesNotExist();
        assertThat(recovery.stripeCount()).isEqualTo(64);
        try (var files = Files.walk(temp.resolve(".invalid-recovery/people"))) {
            assertThat(files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("people.gamification-definitions.json", "people.gamification-bindings.json");
        }
    }

    @Test
    void doesNotHideOrdinaryStorageFailuresAsCorruption() {
        GamificationProperties properties = new GamificationProperties();
        properties.setDirectory(temp);
        GamificationCorruptDocumentRecovery recovery = new GamificationCorruptDocumentRecovery(properties);

        assertThat(recovery.recoverIfMalformed("people", new CanonicalDocumentStoreException(
                Reason.STORAGE_ERROR, "Could not read gamification document"))).isFalse();
    }

    @Test
    void rejectsMetamodelIdsThatWouldEscapeTheGamificationDirectory() throws Exception {
        GamificationProperties properties = new GamificationProperties();
        properties.setDirectory(temp);
        GamificationCorruptDocumentRecovery recovery = new GamificationCorruptDocumentRecovery(properties);
        Files.writeString(temp.resolve("safe.gamification-definitions.json"), "{broken");

        assertThatThrownBy(() -> recovery.recoverIfMalformed("../safe", new CanonicalDocumentStoreException(
                Reason.STORAGE_ERROR, "Could not parse document: malformed")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
