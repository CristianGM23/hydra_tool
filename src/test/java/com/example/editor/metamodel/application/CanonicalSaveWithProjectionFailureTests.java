package com.example.editor.metamodel.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.canonical.CanonicalMetamodelJsonCodec;
import com.example.editor.metamodel.projection.JsonProjectionManifestRepository;
import com.example.editor.metamodel.projection.MetamodelProjection;
import com.example.editor.metamodel.projection.ProjectionCoordinator;
import com.example.editor.metamodel.projection.ProjectionResult;
import com.example.editor.metamodel.projection.ProjectionStatus;
import com.example.editor.metamodel.target.JsonTargetConfigurationRepository;
import com.example.editor.metamodel.target.TargetConfigurationDocument;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalSaveWithProjectionFailureTests {

    @TempDir
    Path temporaryDirectory;

    private Path metamodelDirectory;
    private CanonicalMetamodelApplicationService service;
    private JsonCanonicalMetamodelRepository repository;
    private JsonProjectionManifestRepository manifestRepository;

    @BeforeEach
    void setUp() {
        metamodelDirectory = temporaryDirectory.resolve("metamodels");
        StorageProperties storage = new StorageProperties(metamodelDirectory, temporaryDirectory.resolve("models"), temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"), temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        CanonicalMetamodelProperties properties = new CanonicalMetamodelProperties();
        var targetRepository = new JsonTargetConfigurationRepository(storage, mapper);
        repository = new JsonCanonicalMetamodelRepository(storage, properties, mapper, new CanonicalMetamodelJsonCodec(mapper), targetRepository);
        manifestRepository = new JsonProjectionManifestRepository(storage, mapper);
        MetamodelProjection failingProjection = new MetamodelProjection() {
            @Override
            public String targetId() {
                return "ecore";
            }

            @Override
            public ProjectionResult generate(MetaModel metamodel, TargetConfigurationDocument configuration) {
                throw new IllegalStateException("simulated Ecore failure");
            }

            @Override
            public void deleteArtifact(String metamodelId, TargetConfigurationDocument configuration) {
                // No artifact was generated.
            }
        };
        ProjectionCoordinator coordinator = new ProjectionCoordinator(repository, targetRepository, manifestRepository, List.of(failingProjection));
        service = new CanonicalMetamodelApplicationService(repository, new NeutralMetamodelValidator(), coordinator);
    }

    @Test
    void projectionFailureDoesNotRollBackCanonicalCreation() {
        MetamodelSaveResponse response = service.create(model("sample", "Sample"));

        assertThat(response.metamodel().schemaVersion()).isEqualTo(MetaModel.CURRENT_SCHEMA_VERSION);
        assertThat(response.projections()).singleElement().satisfies(result -> assertThat(result.status()).isEqualTo(ProjectionStatus.FAILED));
        assertThat(repository.findById("sample")).isPresent();
        assertThat(metamodelDirectory.resolve("sample.metamodel.json")).isRegularFile();
    }

    @Test
    void failedProjectionIsPersistedInManifestWithCanonicalEtag() {
        MetamodelSaveResponse response = service.create(model("sample", "Sample"));

        var manifest = manifestRepository.findByMetamodelId("sample").orElseThrow();
        assertThat(manifest.canonicalEtag()).isEqualTo(response.etag());
        assertThat(manifest.projections().get("ecore").status()).isEqualTo(ProjectionStatus.FAILED);
        assertThat(manifest.projections().get("ecore").errorMessage()).contains("simulated Ecore failure");
    }

    @Test
    void projectionFailureDoesNotRollBackCanonicalReplacement() {
        MetamodelSaveResponse created = service.create(model("sample", "First"));
        MetamodelSaveResponse replaced = service.replace("sample", model("sample", "Second"), created.etag());

        assertThat(replaced.metamodel().name()).isEqualTo("Second");
        assertThat(replaced.etag()).isNotEqualTo(created.etag());
        assertThat(replaced.projections()).singleElement().satisfies(result -> assertThat(result.status()).isEqualTo(ProjectionStatus.FAILED));
        assertThat(repository.findById("sample").orElseThrow().metamodel().name()).isEqualTo("Second");
    }

    @Test
    void targetConfigurationStillExistsWhenProjectionFails() {
        service.create(model("sample", "Sample"));

        assertThat(Files.exists(metamodelDirectory.resolve("sample.targets.json"))).isTrue();
    }

    private MetaModel model(String id, String name) {
        MetaClass root = new MetaClass("class-root", "Root", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, id, name, List.of(root), List.of(), List.of(), List.of(), Map.of());
    }
}
