package com.example.editor.metamodel.registry;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.emf.EmfResourceSetFactory;
import com.example.editor.emf.MetamodelLoader;
import com.example.editor.metamodel.MetamodelIntrospectionService;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelSummary;
import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.projection.EcoreMirrorService;
import com.example.editor.metamodel.projection.ProjectionCoordinator;
import com.example.editor.metamodel.projection.ProjectionManifest;
import com.example.editor.metamodel.projection.ProjectionResult;
import com.example.editor.metamodel.projection.ProjectionStatus;
import com.example.editor.metamodel.target.TargetConfigurationDocument;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba que el registro runtime se reconstruye exclusivamente desde el
 * catálogo canónico y desde proyecciones Ecore disponibles.
 */
class MetamodelRegistryServiceTests {

    @TempDir
    Path temporaryDirectory;

    private StorageProperties storageProperties;
    private StubCanonicalService canonicalService;
    private StubProjectionCoordinator projectionCoordinator;
    private MetamodelRegistryService registryService;

    @BeforeEach
    void setUp() throws IOException {
        storageProperties = createStorageProperties();
        Files.createDirectories(storageProperties.metamodels());
        canonicalService = new StubCanonicalService();
        projectionCoordinator = new StubProjectionCoordinator();
        registryService = new MetamodelRegistryService(
            storageProperties,
            new MetamodelLoader(new EmfResourceSetFactory()),
            new MetamodelIntrospectionService(),
            canonicalService,
            projectionCoordinator
        );
    }

    @Test
    void initializesFromCanonicalCatalogAndUpToDateEcoreProjection() throws Exception {
        publish("sample", "/metamodels/sample.ecore", ProjectionStatus.UP_TO_DATE, true);

        registryService.initialize();

        assertThat(registryService.list()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("sample");
            assertThat(item.fileName()).isEqualTo("sample.ecore");
            assertThat(item.descriptor().packages().get(0).name()).isEqualTo("sample");
        });
        assertThat(registryService.status("sample").runtimeAvailable()).isTrue();
    }

    @Test
    void ignoresOrphanEcoreFilesWithoutCanonicalMetamodel() throws Exception {
        copyResource("/metamodels/sample.ecore", "orphan.ecore");

        registryService.initialize();

        assertThat(registryService.list()).isEmpty();
        assertThat(registryService.statuses()).isEmpty();
    }

    @Test
    void listsRuntimeMetamodelsSortedByCanonicalId() throws Exception {
        publish("zeta", "/metamodels/sample.ecore", ProjectionStatus.UP_TO_DATE, true);
        publish("alpha", "/metamodels/introspection.ecore", ProjectionStatus.GENERATED_WITH_WARNINGS, true);

        registryService.initialize();

        assertThat(registryService.list()).extracting(RegisteredMetamodel::id).containsExactly("alpha", "zeta");
        assertThat(registryService.statuses()).extracting(MetamodelRegistryService.RuntimeMetamodelStatus::metamodelId).containsExactly("alpha", "zeta");
    }

    @Test
    void keepsStaleProjectionOutOfRuntimeAndReportsItsStatus() throws Exception {
        publish("sample", "/metamodels/sample.ecore", ProjectionStatus.STALE, true);

        registryService.initialize();

        assertThat(registryService.list()).isEmpty();
        assertThat(registryService.status("sample").projectionStatus()).isEqualTo(ProjectionStatus.STALE);
        assertThat(registryService.status("sample").runtimeAvailable()).isFalse();
        assertThatThrownBy(() -> registryService.find("sample"))
            .isInstanceOf(MetamodelRegistryException.class)
            .hasMessageContaining("not available for the EMF runtime");
    }

    @Test
    void reportsMissingProjectionFileWithoutScanningOtherEcoreFiles() throws Exception {
        publish("sample", "/metamodels/sample.ecore", ProjectionStatus.UP_TO_DATE, false);
        copyResource("/metamodels/introspection.ecore", "unrelated.ecore");

        registryService.initialize();

        assertThat(registryService.list()).isEmpty();
        assertThat(registryService.status("sample").projectionStatus()).isEqualTo(ProjectionStatus.STALE);
        assertThat(registryService.status("sample").message()).contains("projection file is missing");
    }

    @Test
    void rejectsUnknownMetamodelId() {
        registryService.initialize();

        assertThatThrownBy(() -> registryService.find("missing"))
            .isInstanceOf(MetamodelRegistryException.class)
            .hasMessage("Metamodel was not found: missing");
    }

    private void publish(String id, String resourceName, ProjectionStatus status, boolean createProjectionFile) throws Exception {
        MetaModel model = CanonicalMetamodelTestFixtures.valid(id);
        TargetConfigurationDocument targets = TargetConfigurationDocument.defaultsFor(model);
        String fileName = id + ".ecore";

        if(createProjectionFile) {
            copyResource(resourceName, fileName);
        }

        canonicalService.add(new CanonicalMetamodelSummary(id, model.name(), model.schemaVersion(), "\"etag-" + id + "\""));
        projectionCoordinator.putTargets(id, targets);
        ProjectionResult ecore = new ProjectionResult(EcoreMirrorService.TARGET_ID, status, fileName, List.of(), "");
        projectionCoordinator.putManifest(id, new ProjectionManifest(ProjectionManifest.CURRENT_SCHEMA_VERSION, id, "\"etag-" + id + "\"", Instant.parse("2026-07-25T00:00:00Z"), Map.of(EcoreMirrorService.TARGET_ID, ecore)));
    }

    private StorageProperties createStorageProperties() {
        return new StorageProperties(
            temporaryDirectory.resolve("metamodels"),
            temporaryDirectory.resolve("models"),
            temporaryDirectory.resolve("notations"),
            temporaryDirectory.resolve("validations"),
            temporaryDirectory.resolve("quickfixes"),
            temporaryDirectory.resolve("generators"),
            temporaryDirectory.resolve("assets"),
            temporaryDirectory.resolve("exports"),
            true
        );
    }

    private void copyResource(String resourceName, String destinationName) throws IOException, URISyntaxException {
        Path source = getResourcePath(resourceName);
        Path destination = storageProperties.metamodels().resolve(destinationName);
        Files.copy(source, destination);
    }

    private Path getResourcePath(String resourceName) throws URISyntaxException {
        java.net.URL resourceUrl = Objects.requireNonNull(getClass().getResource(resourceName), "Test resource was not found");
        return Path.of(resourceUrl.toURI());
    }

    private static final class StubCanonicalService extends CanonicalMetamodelApplicationService {
        private final List<CanonicalMetamodelSummary> summaries = new java.util.ArrayList<>();

        private StubCanonicalService() {
            super(null, new NeutralMetamodelValidator());
        }

        private void add(CanonicalMetamodelSummary summary) {
            summaries.add(summary);
        }

        @Override
        public List<CanonicalMetamodelSummary> findAll() {
            return List.copyOf(summaries);
        }
    }

    private static final class StubProjectionCoordinator extends ProjectionCoordinator {
        private final Map<String, TargetConfigurationDocument> targets = new LinkedHashMap<>();
        private final Map<String, ProjectionManifest> manifests = new LinkedHashMap<>();

        private StubProjectionCoordinator() {
            super(null, null, null, List.of());
        }

        private void putTargets(String id, TargetConfigurationDocument document) {
            targets.put(id, document);
        }

        private void putManifest(String id, ProjectionManifest manifest) {
            manifests.put(id, manifest);
        }

        @Override
        public TargetConfigurationDocument findTargets(String metamodelId) {
            return targets.get(metamodelId);
        }

        @Override
        public ProjectionManifest findManifest(String metamodelId) {
            return manifests.get(metamodelId);
        }
    }
}
