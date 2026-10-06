package com.example.editor.metamodel.canonical;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.canonical.CanonicalMetamodelRepositoryException.Reason;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba el bloque 1: persistencia canónica, ETag, recuperación y concurrencia.
 */
class JsonCanonicalMetamodelRepositoryTests {

    @TempDir
    Path temporaryDirectory;

    private JsonCanonicalMetamodelRepository repository;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        CanonicalMetamodelProperties properties = new CanonicalMetamodelProperties();
        StorageProperties storage = new StorageProperties(temporaryDirectory.resolve("metamodels"), temporaryDirectory.resolve("models"), temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"), temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        jsonMapper = JsonMapper.builder().build();
        repository = new JsonCanonicalMetamodelRepository(storage, properties, jsonMapper);
    }

    @Test
    void createsCanonicalFileAndReturnsQuotedSha256Etag() {
        StoredMetamodel stored = repository.create(CanonicalMetamodelTestFixtures.valid("sample"));

        assertThat(stored.path()).isRegularFile();
        assertThat(stored.path().getFileName().toString()).isEqualTo("sample.metamodel.json");
        assertThat(stored.etag()).matches("\"[0-9a-f]{64}\"");
    }

    @Test
    void rejectsDuplicateCreationWithoutReplacingExistingContent() {
        repository.create(CanonicalMetamodelTestFixtures.valid("sample", "First", java.util.Map.of()));

        assertThatThrownBy(() -> repository.create(CanonicalMetamodelTestFixtures.valid("sample", "Second", java.util.Map.of())))
            .isInstanceOf(CanonicalMetamodelRepositoryException.class)
            .extracting(exception -> ((CanonicalMetamodelRepositoryException) exception).getReason())
            .isEqualTo(Reason.CONFLICT);

        assertThat(repository.findById("sample").orElseThrow().metamodel().name()).isEqualTo("First");
    }

    @Test
    void storesCanonicalMetamodelWithoutCreatingTargetSidecars() throws Exception {
        MetaModel original = CanonicalMetamodelTestFixtures.valid("sample");
        StoredMetamodel created = repository.create(original);
        MetaModel restored = repository.findById("sample").orElseThrow().metamodel();

        assertThat(created.metamodel().schemaVersion()).isEqualTo(MetaModel.CURRENT_SCHEMA_VERSION);
        assertThat(restored.schemaVersion()).isEqualTo(MetaModel.CURRENT_SCHEMA_VERSION);
        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.name()).isEqualTo(original.name());
        assertThat(restored.classes()).extracting(item -> item.id()).containsExactly("class:root");
        assertThat(Files.readString(created.path())).doesNotContain("artifactName", "namespaceUri", "namespacePrefix");
        assertThat(temporaryDirectory.resolve("metamodels/sample.targets.json")).doesNotExist();
    }

    @Test
    void replacesWhenTheExpectedEtagMatches() {
        StoredMetamodel first = repository.create(CanonicalMetamodelTestFixtures.valid("sample", "First", java.util.Map.of()));
        StoredMetamodel second = repository.replace("sample", CanonicalMetamodelTestFixtures.valid("sample", "Second", java.util.Map.of()), first.etag());

        assertThat(second.metamodel().name()).isEqualTo("Second");
        assertThat(second.etag()).isNotEqualTo(first.etag());
    }

    @Test
    void rejectsReplacementWithoutIfMatch() {
        repository.create(CanonicalMetamodelTestFixtures.valid("sample"));

        assertThatThrownBy(() -> repository.replace("sample", CanonicalMetamodelTestFixtures.valid("sample", "Changed", java.util.Map.of()), null))
            .isInstanceOf(CanonicalMetamodelRepositoryException.class)
            .extracting(exception -> ((CanonicalMetamodelRepositoryException) exception).getReason())
            .isEqualTo(Reason.PRECONDITION_FAILED);
    }

    @Test
    void rejectsReplacementWithStaleEtag() {
        repository.create(CanonicalMetamodelTestFixtures.valid("sample"));

        assertThatThrownBy(() -> repository.replace("sample", CanonicalMetamodelTestFixtures.valid("sample", "Changed", java.util.Map.of()), "\"stale\""))
            .isInstanceOf(CanonicalMetamodelRepositoryException.class)
            .extracting(exception -> ((CanonicalMetamodelRepositoryException) exception).getReason())
            .isEqualTo(Reason.PRECONDITION_FAILED);
    }

    @Test
    void wildcardEtagAllowsControlledInternalReplacement() {
        repository.create(CanonicalMetamodelTestFixtures.valid("sample"));

        StoredMetamodel replaced = repository.replace("sample", CanonicalMetamodelTestFixtures.valid("sample", "Changed", java.util.Map.of()), "*");

        assertThat(replaced.metamodel().name()).isEqualTo("Changed");
    }

    @Test
    void rejectsPathTraversalAndWindowsSeparators() {
        assertThatThrownBy(() -> repository.findById("../outside")).isInstanceOf(CanonicalMetamodelRepositoryException.class);
        assertThatThrownBy(() -> repository.findById("folder\\outside")).isInstanceOf(CanonicalMetamodelRepositoryException.class);
        assertThatThrownBy(() -> repository.findById("C:outside")).isInstanceOf(CanonicalMetamodelRepositoryException.class);
    }

    @Test
    void calculatesTheSameEtagForMapsWithDifferentInsertionOrder() {
        StoredMetamodel first = repository.create(CanonicalMetamodelTestFixtures.validWithOrderedAnnotations("sample", false));
        StoredMetamodel second = repository.replace("sample", CanonicalMetamodelTestFixtures.validWithOrderedAnnotations("sample", true), first.etag());

        assertThat(second.etag()).isEqualTo(first.etag());
    }

    @Test
    void listsCanonicalMetamodelsInStableIdOrder() {
        repository.create(CanonicalMetamodelTestFixtures.valid("zeta"));
        repository.create(CanonicalMetamodelTestFixtures.valid("alpha"));

        assertThat(repository.findAll()).extracting(CanonicalMetamodelSummary::id).containsExactly("alpha", "zeta");
    }

    @Test
    void deletesOnlyWhenTheEtagMatches() {
        StoredMetamodel stored = repository.create(CanonicalMetamodelTestFixtures.valid("sample"));
        repository.delete("sample", stored.etag());

        assertThat(repository.findById("sample")).isEmpty();
    }

    @Test
    void recoversACompleteTemporaryFileAfterInterruptedWrite() throws Exception {
        MetaModel metamodel = CanonicalMetamodelTestFixtures.valid("recovered");
        Path directory = temporaryDirectory.resolve("metamodels");
        Files.createDirectories(directory);
        Path temporary = directory.resolve("recovered.metamodel.json.tmp");
        Files.write(temporary, jsonMapper.writeValueAsBytes(metamodel));

        StoredMetamodel recovered = repository.findById("recovered").orElseThrow();

        assertThat(recovered.metamodel().schemaVersion()).isEqualTo(MetaModel.CURRENT_SCHEMA_VERSION);
        assertThat(recovered.metamodel().id()).isEqualTo(metamodel.id());
        assertThat(recovered.metamodel().classes()).extracting(item -> item.id()).containsExactly("class:root");
        assertThat(directory.resolve("recovered.metamodel.json")).isRegularFile();
        assertThat(directory.resolve("recovered.targets.json")).doesNotExist();
        assertThat(temporary).doesNotExist();
    }

    @Test
    void removesAnInvalidTemporaryFileWithoutPublishingIt() throws Exception {
        Path directory = temporaryDirectory.resolve("metamodels");
        Files.createDirectories(directory);
        Path temporary = directory.resolve("broken.metamodel.json.tmp");
        Files.writeString(temporary, "not-json");

        assertThat(repository.findById("broken")).isEmpty();
        assertThat(temporary).doesNotExist();
    }

    @Test
    void onlyOneConcurrentReplacementCanUseTheSameEtag() throws Exception {
        StoredMetamodel stored = repository.create(CanonicalMetamodelTestFixtures.valid("shared"));
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            List<Callable<Boolean>> tasks = List.of(
                () -> replaceAfter(start, "One", stored.etag()),
                () -> replaceAfter(start, "Two", stored.etag())
            );
            var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();

            for (Callable<Boolean> task : tasks) {
                futures.add(executor.submit(task));
            }

            start.countDown();
            List<Boolean> results = new ArrayList<>();

            for (var future : futures) {
                results.add(future.get());
            }

            assertThat(results).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void differentIdsCanBeCreatedByConcurrentWorkers() throws Exception {
        var executor = Executors.newFixedThreadPool(6);

        try {
            List<Callable<String>> tasks = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> (Callable<String>) () -> repository.create(CanonicalMetamodelTestFixtures.valid("model-" + index)).metamodel().id())
                .toList();
            List<String> ids = new ArrayList<>();

            for (var future : executor.invokeAll(tasks)) {
                ids.add(future.get());
            }

            assertThat(ids).hasSize(20).doesNotHaveDuplicates();
            assertThat(repository.findAll()).hasSize(20);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean replaceAfter(CountDownLatch start, String name, String etag) throws InterruptedException {
        start.await();

        try {
            repository.replace("shared", CanonicalMetamodelTestFixtures.valid("shared", name, java.util.Map.of()), etag);
            return true;
        } catch (CanonicalMetamodelRepositoryException exception) {
            assertThat(exception.getReason()).isEqualTo(Reason.PRECONDITION_FAILED);
            return false;
        }
    }
}
