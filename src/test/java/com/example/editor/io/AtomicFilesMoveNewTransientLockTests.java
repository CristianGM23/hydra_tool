package com.example.editor.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.nio.file.ExtendedOpenOption;

/**
 * FIND-REC-PROJECT-IMPORT-500 (ronda 38, REC-R5R6-logistore): la importación de un proyecto publica su directorio de staging con
 * AtomicFiles.moveNew y Windows rechaza el renombrado con AccessDeniedException mientras otro proceso (antivirus, indexador) tiene abierto
 * uno de los ficheros recién extraídos. moveReplacing ya reintentaba este caso (T-WIN-LOCK); moveNew no. El bloqueo se reproduce abriendo
 * un fichero del directorio sin FILE_SHARE_DELETE, igual que esos procesos.
 */
class AtomicFilesMoveNewTransientLockTests {
    @TempDir Path temporary;

    @Test void aBriefLockOnAFileInsideTheStagingDirectoryIsAbsorbedAndAPersistentOneStillFailsWithoutPublishing() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"), "el bloqueo de borrado es específico de Windows");
        Path staging = temporary.resolve(".project-import-1");
        Path held = staging.resolve("behavior").resolve("actions.json");
        Files.createDirectories(held.getParent());
        Files.writeString(held, "{}");
        Path target = temporary.resolve("project");
        CompletableFuture<Void> released;
        try(FileChannel scanner = FileChannel.open(held, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
            released = CompletableFuture.runAsync(() -> {
                try { Thread.sleep(250); scanner.close(); } catch(Exception exception) { throw new IllegalStateException(exception); }
            });
            AtomicFiles.moveNew(staging, target);
        }
        released.get(5, TimeUnit.SECONDS);
        assertThat(target.resolve("behavior").resolve("actions.json")).hasContent("{}");
        assertThat(staging).doesNotExist();

        Path second = temporary.resolve(".project-import-2");
        Path secondHeld = second.resolve("model.json");
        Files.createDirectories(second);
        Files.writeString(secondHeld, "{\"v\":2}");
        Path secondTarget = temporary.resolve("project-2");
        try(FileChannel scanner = FileChannel.open(secondHeld, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
            assertThatThrownBy(() -> AtomicFiles.moveNew(second, secondTarget)).isInstanceOf(java.io.IOException.class);
        }
        assertThat(secondHeld).as("a persistent lock fails as before and leaves the staging intact").hasContent("{\"v\":2}");
        assertThat(secondTarget).doesNotExist();
    }

    @Test void anExistingDestinationIsStillRefusedAtOnce() throws Exception {
        Path source = temporary.resolve("source");
        Path target = temporary.resolve("target");
        Files.createDirectories(source);
        Files.createDirectories(target);
        long started = System.nanoTime();
        assertThatThrownBy(() -> AtomicFiles.moveNew(source, target)).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).as("create-only is not a transient failure: no retry").isLessThan(1000);
        assertThat(source).exists();
    }
}
