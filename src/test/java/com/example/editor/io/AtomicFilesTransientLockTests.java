package com.example.editor.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.nio.file.ExtendedOpenOption;

/** Igual que en AtomicJsonDocumentStoreTransientLockTests, para la publicación compartida de AtomicFiles (Windows). */
class AtomicFilesTransientLockTests {
    @TempDir Path temporary;

    @Test void aBriefLockOnTheDestinationIsAbsorbedAndAPersistentOneStillFails() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"), "el bloqueo de borrado es específico de Windows");
        Path target = temporary.resolve("index.json");
        Files.writeString(target, "v1");
        CompletableFuture<Void> released;
        try(FileChannel holder = FileChannel.open(target, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
            released = CompletableFuture.runAsync(() -> {
                try { Thread.sleep(250); holder.close(); } catch(Exception exception) { throw new IllegalStateException(exception); }
            });
            AtomicFiles.writeReplacing(target, "v2".getBytes(StandardCharsets.UTF_8));
        }
        released.get(5, TimeUnit.SECONDS);
        assertThat(target).hasContent("v2");

        try(FileChannel holder = FileChannel.open(target, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
            assertThatThrownBy(() -> AtomicFiles.writeReplacing(target, "v3".getBytes(StandardCharsets.UTF_8))).isInstanceOf(java.io.IOException.class);
        }
        assertThat(target).hasContent("v2");
        try(var files = Files.list(temporary)) { assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("index.json"); }
    }

    @Test void onlyAccessDeniedIsRetried() {
        int[] calls = {0};
        assertThatThrownBy(() -> TransientFileAccessRetry.run(() -> { calls[0]++; throw new java.io.IOException("disk full"); }, 5, 1, 1, millis -> { }))
                .hasMessage("disk full");
        assertThat(calls[0]).isEqualTo(1);
        calls[0] = 0;
        assertThatThrownBy(() -> TransientFileAccessRetry.run(() -> { calls[0]++; throw new AccessDeniedException("x"); }, 5, 1, 1, millis -> { }))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(calls[0]).isEqualTo(5);
    }
}
