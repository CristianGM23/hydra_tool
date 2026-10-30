package com.example.editor.validation.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.nio.file.ExtendedOpenOption;

import tools.jackson.databind.json.JsonMapper;

/**
 * Hallazgo de la regresión del jar 14 (EventFlowRuntimeBranchingJournalAndBusIntegrationTests): en Windows, la publicación del temporal
 * falló con AccessDeniedException porque otro proceso tenía abierto el destino un instante, y el diario se perdió. Se reproduce abriendo el
 * destino sin FILE_SHARE_DELETE, como hacen antivirus e indexadores. Un bloqueo breve debe absorberse; uno persistente debe seguir fallando
 * con STORAGE_ERROR en un tiempo acotado y sin tocar el documento ya publicado.
 */
class AtomicJsonDocumentStoreTransientLockTests {
    @TempDir Path temporary;

    @BeforeEach void onlyOnWindows() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"), "el bloqueo de borrado es específico de Windows");
    }

    @Test void aBriefLockOnTheDestinationDoesNotLoseTheWrite() throws Exception {
        AtomicJsonDocumentStore<Sample> store = store();
        String etag = store.create(new Sample("sample", "v1")).etag();
        Path destination = temporary.resolve("sample.json");
        CompletableFuture<Void> released;
        try(FileChannel holder = FileChannel.open(destination, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
            released = CompletableFuture.runAsync(() -> {
                try { Thread.sleep(250); holder.close(); } catch(Exception exception) { throw new IllegalStateException(exception); }
            });
            StoredCanonicalDocument<Sample> stored = store.replace("sample", new Sample("sample", "v2"), etag);
            assertThat(stored.document().value()).isEqualTo("v2");
        }
        released.get(5, TimeUnit.SECONDS);
        assertThat(store.find("sample").orElseThrow().document().value()).isEqualTo("v2");
        assertThat(temporary.resolve("sample.json.tmp")).doesNotExist();
    }

    @Test void aPersistentLockStillFailsWithAStorageErrorWithinABoundedTime() throws Exception {
        AtomicJsonDocumentStore<Sample> store = store();
        String etag = store.create(new Sample("sample", "v1")).etag();
        Path destination = temporary.resolve("sample.json");
        long start = System.nanoTime();
        try(FileChannel holder = FileChannel.open(destination, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
            assertThatThrownBy(() -> store.replace("sample", new Sample("sample", "v2"), etag))
                    .isInstanceOfSatisfying(CanonicalDocumentStoreException.class,
                            exception -> assertThat(exception.getReason()).isEqualTo(CanonicalDocumentStoreException.Reason.STORAGE_ERROR));
        }
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(10_000);
        assertThat(store.find("sample").orElseThrow().document().value()).isEqualTo("v1");
        assertThat(temporary.resolve("sample.json.tmp")).doesNotExist();
        assertThat(Files.readString(destination)).contains("v1");
    }

    private AtomicJsonDocumentStore<Sample> store() {
        return new AtomicJsonDocumentStore<>(temporary, ".json", true, JsonMapper.builder().build(), Sample.class, Sample::id);
    }

    record Sample(String id, String value) { }
}
