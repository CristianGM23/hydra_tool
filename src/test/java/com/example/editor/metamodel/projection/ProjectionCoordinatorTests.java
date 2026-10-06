package com.example.editor.metamodel.projection;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.canonical.CanonicalMetamodelRepository;
import com.example.editor.metamodel.canonical.CanonicalMetamodelSummary;
import com.example.editor.metamodel.canonical.StoredMetamodel;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.target.EcoreGenerationMode;
import com.example.editor.metamodel.target.EcoreProjectionConfiguration;
import com.example.editor.metamodel.target.TargetConfigurationDocument;
import com.example.editor.metamodel.target.TargetConfigurationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectionCoordinatorTests {

    private MemoryCanonicalRepository canonicalRepository;
    private MemoryTargetRepository targetRepository;
    private MemoryManifestRepository manifestRepository;
    private RecordingProjection projection;
    private ProjectionCoordinator coordinator;
    private MetaModel metamodel;

    @BeforeEach
    void setUp() {
        metamodel = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "Sample", List.of(new MetaClass("class-root", "Root", MetaClassKind.CLASS, List.of(), List.of(), Map.of())), List.of(), List.of(), List.of(), Map.of());
        canonicalRepository = new MemoryCanonicalRepository(new StoredMetamodel(metamodel, "\"etag-1\"", Path.of("sample.metamodel.json")));
        targetRepository = new MemoryTargetRepository();
        manifestRepository = new MemoryManifestRepository();
        projection = new RecordingProjection();
        coordinator = new ProjectionCoordinator(canonicalRepository, targetRepository, manifestRepository, List.of(projection));
    }

    @Test
    void generatesOnSaveProjectionAndPersistsItsManifest() {
        List<ProjectionResult> results = coordinator.synchronizeAfterSave(metamodel, "\"etag-1\"");

        assertThat(results).singleElement().satisfies(result -> assertThat(result.status()).isEqualTo(ProjectionStatus.UP_TO_DATE));
        assertThat(projection.generateCalls).isEqualTo(1);
        assertThat(manifestRepository.manifest.canonicalEtag()).isEqualTo("\"etag-1\"");
        assertThat(manifestRepository.manifest.projections()).containsKey("ecore");
    }

    @Test
    void createsAndPersistsDefaultTargetConfigurationOnFirstSave() {
        coordinator.synchronizeAfterSave(metamodel, "\"etag-1\"");

        assertThat(targetRepository.document).isNotNull();
        assertThat(targetRepository.document.metamodelId()).isEqualTo("sample");
        assertThat(targetRepository.document.ecore().generationMode()).isEqualTo(EcoreGenerationMode.ON_SAVE);
    }

    @Test
    void requestedTargetConfigurationOverridesDefaultsDuringTheSameSave() {
        TargetConfigurationDocument requested = targets(EcoreGenerationMode.ON_SAVE, true, "imported.ecore");

        ProjectionResult result = coordinator.synchronizeAfterSave(metamodel, "\"etag-1\"", requested).getFirst();

        assertThat(result.status()).isEqualTo(ProjectionStatus.UP_TO_DATE);
        assertThat(targetRepository.document.ecore().fileName()).isEqualTo("imported.ecore");
        assertThat(projection.lastGeneratedConfiguration.ecore().fileName()).isEqualTo("imported.ecore");
    }

    @Test
    void manualProjectionIsMarkedStaleWithoutGenerating() {
        targetRepository.document = targets(EcoreGenerationMode.MANUAL, true, "sample.ecore");

        ProjectionResult result = coordinator.synchronizeAfterSave(metamodel, "\"etag-1\"").getFirst();

        assertThat(result.status()).isEqualTo(ProjectionStatus.STALE);
        assertThat(result.artifactPath()).isEqualTo("sample.ecore");
        assertThat(projection.generateCalls).isZero();
    }

    @Test
    void disabledProjectionIsNotRequested() {
        targetRepository.document = targets(EcoreGenerationMode.DISABLED, false, "sample.ecore");

        ProjectionResult result = coordinator.synchronizeAfterSave(metamodel, "\"etag-1\"").getFirst();

        assertThat(result.status()).isEqualTo(ProjectionStatus.NOT_REQUESTED);
        assertThat(projection.generateCalls).isZero();
    }

    @Test
    void projectionFailureIsReportedWithoutThrowing() {
        projection.throwOnGenerate = true;

        ProjectionResult result = coordinator.synchronizeAfterSave(metamodel, "\"etag-1\"").getFirst();

        assertThat(result.status()).isEqualTo(ProjectionStatus.FAILED);
        assertThat(result.errorMessage()).contains("simulated generation failure");
        assertThat(manifestRepository.manifest.projections().get("ecore").status()).isEqualTo(ProjectionStatus.FAILED);
    }

    @Test
    void missingManifestUsesTheCurrentCanonicalEtag() {
        ProjectionManifest manifest = coordinator.findManifest("sample");

        assertThat(manifest.canonicalEtag()).isEqualTo("\"etag-1\"");
        assertThat(manifest.projections()).isEmpty();
    }

    @Test
    void manualRegenerationRunsEvenWhenGenerationModeIsManual() {
        targetRepository.document = targets(EcoreGenerationMode.MANUAL, true, "sample.ecore");

        ProjectionResult result = coordinator.regenerateEcore("sample");

        assertThat(result.status()).isEqualTo(ProjectionStatus.UP_TO_DATE);
        assertThat(projection.generateCalls).isEqualTo(1);
        assertThat(manifestRepository.manifest.projections().get("ecore")).isEqualTo(result);
    }

    @Test
    void savingEnabledConfigurationMarksProjectionStale() {
        TargetConfigurationDocument updated = coordinator.saveEcoreConfiguration("sample", targets(EcoreGenerationMode.MANUAL, true, "manual.ecore").ecore());

        assertThat(updated.ecore().fileName()).isEqualTo("manual.ecore");
        assertThat(manifestRepository.manifest.projections().get("ecore").status()).isEqualTo(ProjectionStatus.STALE);
    }

    @Test
    void savingDisabledConfigurationMarksProjectionNotRequested() {
        coordinator.saveEcoreConfiguration("sample", targets(EcoreGenerationMode.DISABLED, false, "sample.ecore").ecore());

        assertThat(manifestRepository.manifest.projections().get("ecore").status()).isEqualTo(ProjectionStatus.NOT_REQUESTED);
    }

    @Test
    void changingArtifactNameDeletesThePreviouslyConfiguredArtifact() {
        targetRepository.document = targets(EcoreGenerationMode.ON_SAVE, true, "old.ecore");

        coordinator.saveEcoreConfiguration("sample", targets(EcoreGenerationMode.ON_SAVE, true, "new.ecore").ecore());

        assertThat(projection.deleteCalls).isEqualTo(1);
        assertThat(projection.lastDeletedConfiguration.ecore().fileName()).isEqualTo("old.ecore");
        assertThat(targetRepository.document.ecore().fileName()).isEqualTo("new.ecore");
    }

    @Test
    void keepingTheSameArtifactNameDoesNotDeleteIt() {
        targetRepository.document = targets(EcoreGenerationMode.ON_SAVE, true, "sample.ecore");

        coordinator.saveEcoreConfiguration("sample", targets(EcoreGenerationMode.MANUAL, true, "sample.ecore").ecore());

        assertThat(projection.deleteCalls).isZero();
    }

    @Test
    void deleteRemovesArtifactConfigurationAndManifest() {
        targetRepository.document = targets(EcoreGenerationMode.ON_SAVE, true, "sample.ecore");
        coordinator.synchronizeAfterSave(metamodel, "\"etag-1\"");

        coordinator.deleteFor("sample");

        assertThat(projection.deleteCalls).isEqualTo(1);
        assertThat(targetRepository.document).isNull();
        assertThat(manifestRepository.manifest).isNull();
    }

    @Test
    void missingCanonicalMetamodelIsReportedAsNotFound() {
        canonicalRepository.stored = null;

        assertThatThrownBy(() -> coordinator.findTargets("sample"))
                .isInstanceOf(MetamodelProjectionException.class)
                .extracting(exception -> ((MetamodelProjectionException) exception).getReason())
                .isEqualTo(MetamodelProjectionException.Reason.NOT_FOUND);
    }

    @Test
    void invalidNullEcoreConfigurationIsRejected() {
        assertThatThrownBy(() -> coordinator.saveEcoreConfiguration("sample", null))
                .isInstanceOf(MetamodelProjectionException.class)
                .extracting(exception -> ((MetamodelProjectionException) exception).getReason())
                .isEqualTo(MetamodelProjectionException.Reason.INVALID_REQUEST);
    }

    private TargetConfigurationDocument targets(EcoreGenerationMode mode, boolean enabled, String fileName) {
        EcoreProjectionConfiguration ecore = new EcoreProjectionConfiguration(enabled, mode, true, fileName, "urn:sample", "sample", Map.of(), Map.of(), Map.of());
        return new TargetConfigurationDocument(TargetConfigurationDocument.CURRENT_SCHEMA_VERSION, "sample", ecore, Map.of());
    }

    private static final class RecordingProjection implements MetamodelProjection {
        private int generateCalls;
        private int deleteCalls;
        private boolean throwOnGenerate;
        private TargetConfigurationDocument lastGeneratedConfiguration;
        private TargetConfigurationDocument lastDeletedConfiguration;

        @Override
        public String targetId() {
            return EcoreMirrorService.TARGET_ID;
        }

        @Override
        public ProjectionResult generate(MetaModel metamodel, TargetConfigurationDocument configuration) {
            generateCalls++;
            lastGeneratedConfiguration = configuration;

            if (throwOnGenerate) {
                throw new IllegalStateException("simulated generation failure");
            }

            return new ProjectionResult(targetId(), ProjectionStatus.UP_TO_DATE, configuration.ecore().fileName(), List.of(), "");
        }

        @Override
        public void deleteArtifact(String metamodelId, TargetConfigurationDocument configuration) {
            deleteCalls++;
            lastDeletedConfiguration = configuration;
        }
    }

    private static final class MemoryCanonicalRepository implements CanonicalMetamodelRepository {
        private StoredMetamodel stored;

        private MemoryCanonicalRepository(StoredMetamodel stored) {
            this.stored = stored;
        }

        @Override
        public StoredMetamodel create(MetaModel metamodel) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredMetamodel replace(String id, MetaModel metamodel, String expectedEtag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredMetamodel> findById(String id) {
            return Optional.ofNullable(stored).filter(item -> item.metamodel().id().equals(id));
        }

        @Override
        public List<CanonicalMetamodelSummary> findAll() {
            return List.of();
        }

        @Override
        public boolean exists(String id) {
            return findById(id).isPresent();
        }

        @Override
        public void delete(String id, String expectedEtag) {
            stored = null;
        }
    }

    private static final class MemoryTargetRepository implements TargetConfigurationRepository {
        private TargetConfigurationDocument document;

        @Override
        public Optional<TargetConfigurationDocument> findByMetamodelId(String metamodelId) {
            return Optional.ofNullable(document).filter(item -> item.metamodelId().equals(metamodelId));
        }

        @Override
        public TargetConfigurationDocument save(TargetConfigurationDocument document) {
            this.document = document;
            return document;
        }

        @Override
        public void delete(String metamodelId) {
            document = null;
        }
    }

    private static final class MemoryManifestRepository implements ProjectionManifestRepository {
        private ProjectionManifest manifest;

        @Override
        public Optional<ProjectionManifest> findByMetamodelId(String metamodelId) {
            return Optional.ofNullable(manifest).filter(item -> item.metamodelId().equals(metamodelId));
        }

        @Override
        public ProjectionManifest save(ProjectionManifest manifest) {
            this.manifest = manifest;
            return manifest;
        }

        @Override
        public void delete(String metamodelId) {
            manifest = null;
        }
    }
}
