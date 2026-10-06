package com.example.editor.metamodel.projection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.canonical.CanonicalMetamodelRepositoryException;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonProjectionManifestRepositoryTests {

    @TempDir
    Path temporaryDirectory;

    private Path metamodelDirectory;
    private JsonMapper mapper;
    private JsonProjectionManifestRepository repository;

    @BeforeEach
    void setUp() {
        metamodelDirectory = temporaryDirectory.resolve("metamodels");
        StorageProperties storage = new StorageProperties(metamodelDirectory, temporaryDirectory.resolve("models"), temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"), temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        mapper = JsonMapper.builder().findAndAddModules().build();
        repository = new JsonProjectionManifestRepository(storage, mapper);
    }

    @Test
    void savesAndLoadsTheProjectionManifest() {
        ProjectionManifest manifest = manifest("sample", "\"etag-1\"");

        assertThat(repository.save(manifest)).isEqualTo(manifest);
        assertThat(repository.findByMetamodelId("sample")).contains(manifest);
    }

    @Test
    void replacesTheManifestWithoutLeavingATemporaryFile() {
        repository.save(manifest("sample", "\"etag-1\""));
        repository.save(manifest("sample", "\"etag-2\""));

        assertThat(repository.findByMetamodelId("sample").orElseThrow().canonicalEtag()).isEqualTo("\"etag-2\"");
        assertThat(metamodelDirectory.resolve("sample.projections.json.tmp")).doesNotExist();
    }

    @Test
    void recoversACompleteTemporaryManifest() throws Exception {
        Files.createDirectories(metamodelDirectory);
        ProjectionManifest expected = manifest("sample", "\"etag-1\"");
        Files.write(metamodelDirectory.resolve("sample.projections.json.tmp"), mapper.writeValueAsBytes(expected));

        assertThat(repository.findByMetamodelId("sample")).contains(expected);
        assertThat(metamodelDirectory.resolve("sample.projections.json")).isRegularFile();
    }

    @Test
    void removesAnInvalidTemporaryManifest() throws Exception {
        Files.createDirectories(metamodelDirectory);
        Files.writeString(metamodelDirectory.resolve("sample.projections.json.tmp"), "invalid");

        assertThat(repository.findByMetamodelId("sample")).isEmpty();
        assertThat(metamodelDirectory.resolve("sample.projections.json.tmp")).doesNotExist();
    }

    @Test
    void rejectsATemporaryManifestWithAnotherId() throws Exception {
        Files.createDirectories(metamodelDirectory);
        Files.write(metamodelDirectory.resolve("sample.projections.json.tmp"), mapper.writeValueAsBytes(manifest("other", "\"etag\"")));

        assertThat(repository.findByMetamodelId("sample")).isEmpty();
    }

    @Test
    void rejectsUnsupportedManifestVersions() throws Exception {
        Files.createDirectories(metamodelDirectory);
        Files.writeString(metamodelDirectory.resolve("sample.projections.json"), "{\"schemaVersion\":\"99.0\",\"metamodelId\":\"sample\",\"canonicalEtag\":\"x\",\"projections\":{}}");

        assertThatThrownBy(() -> repository.findByMetamodelId("sample"))
                .isInstanceOf(CanonicalMetamodelRepositoryException.class)
                .hasMessageContaining("Could not parse projection manifest");
    }

    @Test
    void rejectsUnsafeIdentifiers() {
        assertThatThrownBy(() -> repository.findByMetamodelId("../outside")).isInstanceOf(CanonicalMetamodelRepositoryException.class);
        assertThatThrownBy(() -> repository.findByMetamodelId("folder\\outside")).isInstanceOf(CanonicalMetamodelRepositoryException.class);
    }

    @Test
    void deletesManifestAndTemporaryFile() throws Exception {
        repository.save(manifest("sample", "\"etag\""));
        Files.writeString(metamodelDirectory.resolve("sample.projections.json.tmp"), "temporary");

        repository.delete("sample");

        assertThat(metamodelDirectory.resolve("sample.projections.json")).doesNotExist();
        assertThat(metamodelDirectory.resolve("sample.projections.json.tmp")).doesNotExist();
    }

    @Test
    void returnsEmptyWhenNoManifestExists() {
        assertThat(repository.findByMetamodelId("sample")).isEmpty();
    }

    private ProjectionManifest manifest(String id, String etag) {
        ProjectionResult result = new ProjectionResult("ecore", ProjectionStatus.UP_TO_DATE, id + ".ecore", java.util.List.of(), "");
        return new ProjectionManifest(ProjectionManifest.CURRENT_SCHEMA_VERSION, id, etag, Instant.parse("2026-07-24T12:00:00Z"), Map.of("ecore", result));
    }
}
