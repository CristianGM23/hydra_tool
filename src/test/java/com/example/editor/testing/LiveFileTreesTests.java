package com.example.editor.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveFileTreesTests {
    @TempDir Path temporary;

    @Test
    void listsTheRegularFilesInPathOrderAndNotTheDirectories() throws Exception {
        Files.createDirectories(temporary.resolve("b/c"));
        Files.writeString(temporary.resolve("b/c/two.json"), "2");
        Files.writeString(temporary.resolve("a.json"), "1");

        assertThat(LiveFileTrees.regularFiles(temporary)).containsExactly(temporary.resolve("a.json"), temporary.resolve("b/c/two.json"));
        assertThat(LiveFileTrees.regularFiles(temporary.resolve("missing"))).isEmpty();
    }

    @Test
    void entriesThatVanishWhileTheTreeIsWalkedAreSkipped() throws Exception {
        Path audit = Files.createDirectories(temporary.resolve("audit"));
        Path kept = Files.writeString(temporary.resolve("kept.metamodel.json"), "{}");
        AtomicBoolean running = new AtomicBoolean(true);
        // the same churn as an audit partition rewrite: a temporary file appears and is removed again
        Thread churn = new Thread(() -> {
            for (long i = 0; running.get(); i++) {
                try {
                    Path rewrite = audit.resolve("2026-11-02.jsonl.rewrite-" + i + ".tmp");
                    Files.writeString(rewrite, "x");
                    Files.deleteIfExists(rewrite);
                } catch (java.io.IOException ignored) {
                    // the walker never deletes anything here
                }
            }
        });
        churn.start();
        try {
            for (int walk = 0; walk < 2_000; walk++) {
                assertThat(LiveFileTrees.regularFiles(temporary)).contains(kept);
            }
        } finally {
            running.set(false);
            churn.join();
        }
    }

    @Test
    void deletesTheWholeTreeIncludingTheRoot() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("root/x/y"));
        Files.writeString(root.resolve("f.txt"), "f");

        LiveFileTrees.deleteRecursively(temporary.resolve("root"));

        assertThat(temporary.resolve("root")).doesNotExist();
        LiveFileTrees.deleteRecursively(temporary.resolve("root"));
    }
}
