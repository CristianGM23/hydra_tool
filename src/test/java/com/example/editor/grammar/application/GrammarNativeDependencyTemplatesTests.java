package com.example.editor.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.grammar.core.GrammarExecutionMode;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.core.GrammarToolchainProfile;

/** BLQ-GR-NATIVE: the node_modules templates are content-addressed, verified on every restore, isolated by key, bounded and safe under concurrency. */
class GrammarNativeDependencyTemplatesTests {
    @TempDir Path directory;

    @Test void aStoredTemplateIsRestoredByteForByte() throws Exception {
        Path installed = installed("a", Map.of("langium/package.json", "{\"name\":\"langium\"}", "langium/lib/index.js", "export {};", ".bin/langium.cmd", "@node langium"));
        var templates = templates(4, 1 << 20);

        assertThat(templates.store("k1", installed)).isTrue();
        Path workspace = Files.createDirectories(directory.resolve("ws"));
        var restored = templates.restore("k1", workspace);

        assertThat(restored).isPresent();
        assertThat(restored.get().files()).isEqualTo(3);
        assertThat(tree(workspace.resolve("node_modules"))).isEqualTo(tree(installed.resolve("node_modules")));
    }

    @Test void aTamperedTemplateIsNeverRestoredAndIsDiscarded() throws Exception {
        var templates = templates(4, 1 << 20);
        templates.store("k1", installed("a", Map.of("pkg/index.js", "good")));
        Path stored = directory.resolve("templates/k1/node_modules/pkg/index.js");
        Files.writeString(stored, "evil", StandardCharsets.UTF_8);
        Path workspace = Files.createDirectories(directory.resolve("ws"));

        assertThat(templates.restore("k1", workspace)).isEmpty();
        assertThat(workspace.resolve("node_modules")).doesNotExist();
        assertThat(directory.resolve("templates/k1/template.json")).doesNotExist();
    }

    @Test void templatesAreNeverSharedBetweenKeys() throws Exception {
        var templates = templates(4, 1 << 20);
        templates.store("k1", installed("a", Map.of("pkg/index.js", "one")));
        Path workspace = Files.createDirectories(directory.resolve("ws"));

        assertThat(templates.restore("k2", workspace)).isEmpty();
        assertThat(workspace.resolve("node_modules")).doesNotExist();
    }

    @Test void theKeyFollowsTheLockfileAndTheToolchainProfile() {
        String lock = "{\"lockfileVersion\":3,\"packages\":{}}";
        assertThat(GrammarNativeDependencyTemplates.key(profile("langium-4.3", "22"), lock)).isEqualTo(GrammarNativeDependencyTemplates.key(profile("langium-4.3", "22"), lock));
        assertThat(GrammarNativeDependencyTemplates.key(profile("langium-4.3", "22"), lock + " ")).isNotEqualTo(GrammarNativeDependencyTemplates.key(profile("langium-4.3", "22"), lock));
        assertThat(GrammarNativeDependencyTemplates.key(profile("langium-4.4", "22"), lock)).isNotEqualTo(GrammarNativeDependencyTemplates.key(profile("langium-4.3", "22"), lock));
        assertThat(GrammarNativeDependencyTemplates.key(profile("langium-4.3", "24"), lock)).isNotEqualTo(GrammarNativeDependencyTemplates.key(profile("langium-4.3", "22"), lock));
    }

    @Test void theNumberOfTemplatesIsBoundedAndTheLeastRecentlyUsedGoesFirst() throws Exception {
        var templates = templates(2, 1 << 20);
        templates.store("k1", installed("a", Map.of("p/a.js", "1")));
        templates.store("k2", installed("b", Map.of("p/b.js", "2")));
        Files.setLastModifiedTime(directory.resolve("templates/k1/template.json"), FileTime.from(Instant.now().minusSeconds(60)));
        Files.setLastModifiedTime(directory.resolve("templates/k2/template.json"), FileTime.from(Instant.now().minusSeconds(120)));
        templates.restore("k1", Files.createDirectories(directory.resolve("ws1")));

        assertThat(templates.store("k3", installed("c", Map.of("p/c.js", "3")))).isTrue();

        assertThat(templates.entries()).extracting(entry -> entry.directory().getFileName().toString()).containsExactlyInAnyOrder("k1", "k3");
    }

    @Test void aTemplateOverTheByteLimitIsNotStored() throws Exception {
        var templates = templates(4, 16);
        assertThat(templates.store("k1", installed("a", Map.of("p/big.js", "x".repeat(64))))).isFalse();
        assertThat(templates.entries()).isEmpty();
        try(Stream<Path> left = Files.list(directory.resolve("templates"))) { assertThat(left).isEmpty(); }
    }

    @Test void disabledTemplatesNeverTouchTheDisk() throws Exception {
        var templates = new GrammarNativeDependencyTemplates(directory.resolve("templates"), false, 4, 1 << 20);
        assertThat(templates.store("k1", installed("a", Map.of("p/a.js", "1")))).isFalse();
        assertThat(templates.restore("k1", Files.createDirectories(directory.resolve("ws")))).isEmpty();
        assertThat(directory.resolve("templates")).doesNotExist();
    }

    @Test void concurrentRestoresAndStoresNeverExposeAPartialTree() throws Exception {
        Map<String, String> files = new TreeMap<>();
        for(int i = 0; i < 200; i++) files.put("pkg" + (i % 10) + "/f" + i + ".js", "content-" + i);
        Path installed = installed("a", files);
        var templates = templates(4, 1 << 22);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Map<String, String>>> restores = new ArrayList<>();
        try {
            List<Future<Boolean>> stores = new ArrayList<>();
            for(int i = 0; i < 2; i++) stores.add(pool.submit(() -> { start.await(); return templates.store("k1", installed); }));
            for(int i = 0; i < 4; i++) {
                Path workspace = Files.createDirectories(directory.resolve("ws" + i));
                restores.add(pool.submit(() -> {
                    start.await();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                    while(System.nanoTime() < deadline) {
                        if(templates.restore("k1", workspace).isPresent()) return tree(workspace.resolve("node_modules"));
                        assertThat(workspace.resolve("node_modules")).as("a failed restore leaves no partial tree").doesNotExist();
                        Thread.sleep(10);
                    }
                    return Map.of("timeout", "no template after 20 s");
                }));
            }
            start.countDown();
            assertThat(stores.stream().filter(future -> { try { return future.get(30, TimeUnit.SECONDS); } catch(Exception e) { throw new IllegalStateException(e); } }).count()).isEqualTo(1);
            Map<String, String> expected = tree(installed.resolve("node_modules"));
            for(Future<Map<String, String>> restore : restores) {
                Map<String, String> restored = restore.get(30, TimeUnit.SECONDS);
                assertThat(restored).as("restored %d files: %s", restored.size(), restored.keySet().stream().limit(3).toList()).isEqualTo(expected);
            }
        } finally {
            pool.shutdownNow();
        }
        try(Stream<Path> left = Files.list(directory.resolve("templates"))) { assertThat(left.map(path -> path.getFileName().toString())).containsExactly("k1"); }
    }

    private GrammarNativeDependencyTemplates templates(int entries, long bytes) {
        return new GrammarNativeDependencyTemplates(directory.resolve("templates"), true, entries, bytes);
    }

    private Path installed(String name, Map<String, String> files) throws Exception {
        Path workspace = Files.createDirectories(directory.resolve("installed-" + name));
        for(var file : files.entrySet()) {
            Path target = workspace.resolve("node_modules").resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
        }
        return workspace;
    }

    private static Map<String, String> tree(Path root) throws Exception {
        Map<String, String> result = new TreeMap<>();
        try(Stream<Path> walk = Files.walk(root)) {
            for(Path path : walk.filter(Files::isRegularFile).toList()) result.put(root.relativize(path).toString().replace('\\', '/'), Files.readString(path));
        }
        return result;
    }

    private static GrammarToolchainProfile profile(String id, String node) {
        return new GrammarToolchainProfile(id, GrammarTechnology.LANGIUM, "4.3.0", "4.3.0", "", node, "npm", "TypeScript", false, 1000, 60_000,
                128 * 1024 * 1024L, GrammarExecutionMode.INTERNAL, Map.of());
    }
}
