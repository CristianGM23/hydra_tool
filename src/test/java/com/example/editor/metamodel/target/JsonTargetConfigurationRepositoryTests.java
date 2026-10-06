package com.example.editor.metamodel.target;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.canonical.CanonicalMetamodelRepositoryException;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonTargetConfigurationRepositoryTests {

    @TempDir
    Path temporaryDirectory;

    private Path metamodelDirectory;
    private JsonMapper mapper;
    private JsonTargetConfigurationRepository repository;

    @BeforeEach
    void setUp() {
        metamodelDirectory = temporaryDirectory.resolve("metamodels");
        StorageProperties storage = new StorageProperties(metamodelDirectory, temporaryDirectory.resolve("models"), temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"), temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        mapper = JsonMapper.builder().findAndAddModules().build();
        repository = new JsonTargetConfigurationRepository(storage, mapper);
    }

    @Test
    void savesAndLoadsTheConfiguration() {
        TargetConfigurationDocument saved = repository.save(document("sample", "sample.ecore"));

        assertThat(saved).isEqualTo(document("sample", "sample.ecore"));
        assertThat(repository.findByMetamodelId("sample")).contains(saved);
    }

    @Test
    void usesASeparateTargetsFile() {
        repository.save(document("sample", "sample.ecore"));

        assertThat(metamodelDirectory.resolve("sample.targets.json")).isRegularFile();
    }

    @Test
    void replacesAnExistingConfigurationWithoutLeavingATemporaryFile() {
        repository.save(document("sample", "first.ecore"));
        repository.save(document("sample", "second.ecore"));

        assertThat(repository.findByMetamodelId("sample").orElseThrow().ecore().fileName()).isEqualTo("second.ecore");
        assertThat(metamodelDirectory.resolve("sample.targets.json.tmp")).doesNotExist();
    }

    @Test
    void recoversACompleteTemporaryConfiguration() throws Exception {
        Files.createDirectories(metamodelDirectory);
        Files.write(metamodelDirectory.resolve("sample.targets.json.tmp"), mapper.writeValueAsBytes(document("sample", "sample.ecore")));

        assertThat(repository.findByMetamodelId("sample")).contains(document("sample", "sample.ecore"));
        assertThat(metamodelDirectory.resolve("sample.targets.json")).isRegularFile();
        assertThat(metamodelDirectory.resolve("sample.targets.json.tmp")).doesNotExist();
    }

    @Test
    void removesAnInvalidTemporaryConfiguration() throws Exception {
        Files.createDirectories(metamodelDirectory);
        Files.writeString(metamodelDirectory.resolve("sample.targets.json.tmp"), "invalid");

        assertThat(repository.findByMetamodelId("sample")).isEmpty();
        assertThat(metamodelDirectory.resolve("sample.targets.json.tmp")).doesNotExist();
    }

    @Test
    void rejectsATemporaryConfigurationWithAnotherId() throws Exception {
        Files.createDirectories(metamodelDirectory);
        Files.write(metamodelDirectory.resolve("sample.targets.json.tmp"), mapper.writeValueAsBytes(document("other", "other.ecore")));

        assertThat(repository.findByMetamodelId("sample")).isEmpty();
        assertThat(metamodelDirectory.resolve("sample.targets.json.tmp")).doesNotExist();
    }

    @Test
    void rejectsUnsupportedTargetConfigurationVersions() throws Exception {
        Files.createDirectories(metamodelDirectory);
        Files.writeString(metamodelDirectory.resolve("sample.targets.json"), "{\"schemaVersion\":\"99.0\",\"metamodelId\":\"sample\",\"additionalTargets\":{}}");

        assertThatThrownBy(() -> repository.findByMetamodelId("sample"))
                .isInstanceOf(CanonicalMetamodelRepositoryException.class)
                .hasMessageContaining("Could not parse target configuration");
    }

    @Test
    void rejectsPathTraversalAndWindowsSeparators() {
        assertThatThrownBy(() -> repository.findByMetamodelId("../outside")).isInstanceOf(CanonicalMetamodelRepositoryException.class);
        assertThatThrownBy(() -> repository.findByMetamodelId("folder\\outside")).isInstanceOf(CanonicalMetamodelRepositoryException.class);
    }

    @Test
    void deletesTheConfigurationAndItsTemporaryFile() throws Exception {
        repository.save(document("sample", "sample.ecore"));
        Files.writeString(metamodelDirectory.resolve("sample.targets.json.tmp"), "temporary");

        repository.delete("sample");

        assertThat(metamodelDirectory.resolve("sample.targets.json")).doesNotExist();
        assertThat(metamodelDirectory.resolve("sample.targets.json.tmp")).doesNotExist();
    }

    @Test
    void returnsEmptyWhenNoConfigurationExists() {
        assertThat(repository.findByMetamodelId("sample")).isEmpty();
    }

    private TargetConfigurationDocument document(String id, String fileName) {
        EcoreProjectionConfiguration ecore = new EcoreProjectionConfiguration(true, EcoreGenerationMode.ON_SAVE, true, fileName, "urn:" + id, id, Map.of(), Map.of(), Map.of());
        return new TargetConfigurationDocument(TargetConfigurationDocument.CURRENT_SCHEMA_VERSION, id, ecore, Map.of());
    }
}
