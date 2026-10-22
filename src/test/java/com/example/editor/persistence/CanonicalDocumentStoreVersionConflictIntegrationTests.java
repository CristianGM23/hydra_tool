package com.example.editor.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException.Reason;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.json.JsonMapper;

/**
 * x_remote_repository_exchange.p1 - documents are stored with a version mark (ETag) so that clashes are
 * detected instead of silently losing changes.
 *
 * Real {@link CanonicalDocumentStore} (local atomic JSON implementation built by the real factory) over a
 * {@code @TempDir}. The oracle is independent: the file is read from disk and the SHA-256 ETag is recomputed here.
 */
class CanonicalDocumentStoreVersionConflictIntegrationTests {
    private static final String ID = "doc-conflict";

    @TempDir
    Path temporary;

    private CanonicalDocumentStore<TestDocument> newStore() {
        return new LocalCanonicalDocumentStoreFactory().create(new CanonicalDocumentStoreSpec<>("test.conflict.documents",
                temporary, ".conflict.json", true, JsonMapper.builder().findAndAddModules().build(), TestDocument.class,
                TestDocument::id));
    }

    private Path file() {
        return temporary.resolve(ID + ".conflict.json");
    }

    private static String sha256Etag(byte[] content) throws Exception {
        return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) + "\"";
    }

    @Test
    @DisplayName("x_remote_repository_exchange.p1: v1 is stored with an ETag equal to the SHA-256 of the bytes on disk")
    void storedVersionMarkMatchesTheBytesOnDisk() throws Exception {
        StoredCanonicalDocument<TestDocument> created = newStore().create(new TestDocument(ID, "v1"));

        byte[] raw = Files.readAllBytes(file());
        assertThat(new String(raw, StandardCharsets.UTF_8)).contains("\"v1\"").contains(ID);
        assertThat(created.etag()).isEqualTo(sha256Etag(raw));
    }

    @Test
    @DisplayName("x_remote_repository_exchange.p1: replacing with an obsolete version is rejected and the disk stays intact")
    void obsoleteVersionIsRejectedAndFileIsIntact() throws Exception {
        CanonicalDocumentStore<TestDocument> store = newStore();
        StoredCanonicalDocument<TestDocument> v1 = store.create(new TestDocument(ID, "v1"));
        StoredCanonicalDocument<TestDocument> v2 = store.replace(ID, new TestDocument(ID, "v2"), v1.etag());
        byte[] afterV2 = Files.readAllBytes(file());
        assertThat(v2.etag()).isNotEqualTo(v1.etag()).isEqualTo(sha256Etag(afterV2));

        assertThatThrownBy(() -> store.replace(ID, new TestDocument(ID, "stale-write"), v1.etag()))
                .isInstanceOf(CanonicalDocumentStoreException.class)
                .satisfies(e -> assertThat(((CanonicalDocumentStoreException) e).getReason()).isEqualTo(Reason.PRECONDITION_FAILED));
        assertThatThrownBy(() -> store.replace(ID, new TestDocument(ID, "no-etag"), null))
                .isInstanceOf(CanonicalDocumentStoreException.class)
                .satisfies(e -> assertThat(((CanonicalDocumentStoreException) e).getReason()).isEqualTo(Reason.PRECONDITION_FAILED));
        assertThatThrownBy(() -> store.delete(ID, v1.etag()))
                .isInstanceOf(CanonicalDocumentStoreException.class)
                .satisfies(e -> assertThat(((CanonicalDocumentStoreException) e).getReason()).isEqualTo(Reason.PRECONDITION_FAILED));

        assertThat(Files.readAllBytes(file())).isEqualTo(afterV2);
        assertThat(new String(Files.readAllBytes(file()), StandardCharsets.UTF_8)).doesNotContain("stale-write", "no-etag");
        assertThat(Files.exists(temporary.resolve(ID + ".conflict.json.tmp"))).isFalse();
    }

    @Test
    @DisplayName("x_remote_repository_exchange.p1: two concurrent writers with the same expected version - exactly one wins")
    void twoConcurrentWritersWithTheSameExpectedVersionOnlyOneWins() throws Exception {
        CanonicalDocumentStore<TestDocument> store = newStore();
        String v1Etag = store.create(new TestDocument(ID, "v1")).etag();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<String>> writers = new ArrayList<>();
            for (String value : List.of("writer-a", "writer-b")) {
                writers.add(() -> {
                    start.await();
                    try {
                        store.replace(ID, new TestDocument(ID, value), v1Etag);
                        return "OK:" + value;
                    } catch (CanonicalDocumentStoreException e) {
                        return e.getReason().name() + ":" + value;
                    }
                });
            }
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> writer : writers) futures.add(pool.submit(writer));
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                try {
                    outcomes.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    throw new AssertionError("writer failed unexpectedly", e.getCause());
                }
            }

            List<String> winners = outcomes.stream().filter(o -> o.startsWith("OK:")).toList();
            List<String> losers = outcomes.stream().filter(o -> o.startsWith("PRECONDITION_FAILED:")).toList();
            assertThat(winners).hasSize(1);
            assertThat(losers).hasSize(1);

            String winningValue = winners.get(0).substring("OK:".length());
            byte[] raw = Files.readAllBytes(file());
            String json = new String(raw, StandardCharsets.UTF_8);
            assertThat(json).contains("\"" + winningValue + "\"");
            assertThat(json).doesNotContain(losers.get(0).substring("PRECONDITION_FAILED:".length()));
            assertThat(newStore().find(ID).orElseThrow().etag()).isEqualTo(sha256Etag(raw));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("x_remote_repository_exchange.p1: a fresh store instance rereads the stored version and its ETag")
    void freshInstanceRereadsTheStoredVersion() throws Exception {
        CanonicalDocumentStore<TestDocument> first = newStore();
        StoredCanonicalDocument<TestDocument> v1 = first.create(new TestDocument(ID, "v1"));
        StoredCanonicalDocument<TestDocument> v2 = first.replace(ID, new TestDocument(ID, "v2"), v1.etag());

        CanonicalDocumentStore<TestDocument> second = newStore();
        StoredCanonicalDocument<TestDocument> reread = second.find(ID).orElseThrow();
        assertThat(reread.document()).isEqualTo(new TestDocument(ID, "v2"));
        assertThat(reread.etag()).isEqualTo(v2.etag()).isEqualTo(sha256Etag(Files.readAllBytes(file())));
        assertThat(second.listIds()).containsExactly(ID);

        // The version read by the fresh instance is the only one that can replace the document now.
        assertThatThrownBy(() -> second.replace(ID, new TestDocument(ID, "late"), v1.etag()))
                .isInstanceOf(CanonicalDocumentStoreException.class);
        StoredCanonicalDocument<TestDocument> v3 = second.replace(ID, new TestDocument(ID, "v3"), reread.etag());
        assertThat(v3.etag()).isNotEqualTo(v2.etag());
        assertThat(new String(Files.readAllBytes(file()), StandardCharsets.UTF_8)).contains("\"v3\"");
    }

    private record TestDocument(String id, String value) { }
}
