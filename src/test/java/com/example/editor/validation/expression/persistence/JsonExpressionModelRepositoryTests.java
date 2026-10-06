package com.example.editor.validation.expression.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.semantic.expression.ExpressionTestFixtures;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonExpressionModelRepositoryTests {

    @TempDir
    Path temporary;

    private JsonExpressionModelRepository repository;

    @BeforeEach
    void setUp() {
        StorageProperties storage = new StorageProperties(
                temporary.resolve("metamodels"), temporary.resolve("models"), temporary.resolve("notations"), temporary.resolve("validations"),
                temporary.resolve("quickfixes"), temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        repository = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), JsonMapper.builder().findAndAddModules().build());
    }

    @Test
    void createsReadsAndCalculatesDeterministicEtag() {
        ExpressionModel model = ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge());
        StoredExpressionModel created = repository.create(model);
        StoredExpressionModel loaded = repository.findById(model.id()).orElseThrow();
        assertThat(loaded.expressionModel()).isEqualTo(model);
        assertThat(loaded.etag()).isEqualTo(created.etag()).startsWith("\"").endsWith("\"");
        assertThat(Files.isRegularFile(temporary.resolve("validations/library.expressions.json"))).isTrue();
    }

    @Test
    void rejectsDuplicateCreate() {
        ExpressionModel model = ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge());
        repository.create(model);
        assertThatThrownBy(() -> repository.create(model)).isInstanceOf(ExpressionModelRepositoryException.class)
                .extracting("reason").isEqualTo(ExpressionModelRepositoryException.Reason.CONFLICT);
    }

    @Test
    void replaceRequiresIfMatch() {
        ExpressionModel model = ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge());
        repository.create(model);
        assertThatThrownBy(() -> repository.replace(model.id(), model, ""))
                .isInstanceOf(ExpressionModelRepositoryException.class)
                .extracting("reason").isEqualTo(ExpressionModelRepositoryException.Reason.PRECONDITION_FAILED);
    }

    @Test
    void replaceRejectsStaleEtag() {
        ExpressionModel model = ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge());
        repository.create(model);
        assertThatThrownBy(() -> repository.replace(model.id(), model, "\"stale\""))
                .isInstanceOf(ExpressionModelRepositoryException.class)
                .extracting("reason").isEqualTo(ExpressionModelRepositoryException.Reason.PRECONDITION_FAILED);
    }

    @Test
    void replacesWithCurrentEtag() {
        ExpressionModel model = ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge());
        StoredExpressionModel created = repository.create(model);
        ExpressionModel changed = new ExpressionModel("1.0", "library", "library", model.expressions(), Map.of("version", "2"));
        StoredExpressionModel replaced = repository.replace("library", changed, created.etag());
        assertThat(replaced.expressionModel().annotations()).containsEntry("version", "2");
        assertThat(replaced.etag()).isNotEqualTo(created.etag());
    }

    @Test
    void deleteRequiresCurrentEtagAndRemovesFile() {
        ExpressionModel model = ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge());
        StoredExpressionModel created = repository.create(model);
        repository.delete(model.id(), created.etag());
        assertThat(repository.findById(model.id())).isEmpty();
    }

    @Test
    void listsStoredDocuments() {
        repository.create(ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge()));
        assertThat(repository.findAll()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo("library");
            assertThat(summary.expressionCount()).isEqualTo(1);
        });
    }

    @Test
    void concurrentReplacementsWithTheSameEtagAllowExactlyOneWriter() throws Exception {
        ExpressionModel model = ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge());
        StoredExpressionModel created = repository.create(model);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ExpressionModelRepositoryException.Reason>> futures = new ArrayList<>();
        try {
            for(int index = 0; index < 3; index++) {
                int writer = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if(!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent writers did not start in time");
                    ExpressionModel changed = new ExpressionModel("1.0", "library", "library", model.expressions(), Map.of("writer", Integer.toString(writer)));
                    try {
                        repository.replace("library", changed, created.etag());
                        return null;
                    } catch(ExpressionModelRepositoryException exception) {
                        return exception.getReason();
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<ExpressionModelRepositoryException.Reason> outcomes = new ArrayList<>();
            for(Future<ExpressionModelRepositoryException.Reason> future : futures) outcomes.add(future.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(java.util.Objects::isNull).hasSize(1);
            assertThat(outcomes).filteredOn(ExpressionModelRepositoryException.Reason.PRECONDITION_FAILED::equals).hasSize(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void lifecycleCleanupRemainsAvailableWhenFeatureIsDisabled() throws Exception {
        ExpressionModel model = ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge());
        repository.create(model);
        ExpressionModelProperties disabled = new ExpressionModelProperties();
        disabled.setEnabled(false);
        StorageProperties storage = new StorageProperties(
                temporary.resolve("metamodels"), temporary.resolve("models"), temporary.resolve("notations"), temporary.resolve("validations"),
                temporary.resolve("quickfixes"), temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        JsonExpressionModelRepository cleanupRepository = new JsonExpressionModelRepository(storage, disabled, JsonMapper.builder().findAndAddModules().build());
        assertThat(cleanupRepository.exists("library")).isTrue();
        cleanupRepository.deleteIfExists("library");
        assertThat(Files.exists(temporary.resolve("validations/library.expressions.json"))).isFalse();
    }

    @Test
    void rejectsUnsafeIds() {
        assertThatThrownBy(() -> repository.findById("../library")).isInstanceOf(ExpressionModelRepositoryException.class)
                .extracting("reason").isEqualTo(ExpressionModelRepositoryException.Reason.INVALID_REQUEST);
    }
}
