package com.example.editor.metamodel.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorCapabilityManifest;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.EditorDefinitionProperties;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.definition.ToolPaletteDefinition;
import com.example.editor.definition.persistence.JsonEditorDefinitionRepository;
import com.example.editor.event.EventBindingModel;
import com.example.editor.event.EventDefinitionModel;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.integration.InjectionPolicy;
import com.example.editor.integration.InjectionStrategy;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.IntegrationArtifactSelection;
import com.example.editor.integration.IntegrationBuildSystem;
import com.example.editor.integration.IntegrationDeliveryMode;
import com.example.editor.integration.IntegrationFeature;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.IntegrationProfileProperties;
import com.example.editor.integration.IntegrationTarget;
import com.example.editor.integration.IntegrationTargetKind;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.persistence.JsonIntegrationProfileRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelLoadResult;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.design.MetamodelDiagramStorageService;
import com.example.editor.metamodel.deletion.MetamodelDependentArtifactCleanupService;
import com.example.editor.metamodel.projection.ProjectionCoordinator;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.target.TargetConfigurationDocument;
import com.example.editor.validation.action.persistence.ActionModelRepository;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.validation.persistence.ValidationModelRepository;

import tools.jackson.databind.json.JsonMapper;

class MetamodelLifecycleExtendedArtifactsTests {
    @TempDir Path temporary;

    private CanonicalMetamodelApplicationService canonicalService;
    private ProjectionCoordinator projectionCoordinator;
    private MetamodelDiagramStorageService diagramStorageService;
    private MetamodelRegistryService registryService;
    private JsonEventDefinitionModelRepository eventDefinitions;
    private JsonEventBindingModelRepository eventBindings;
    private JsonEditorDefinitionRepository editors;
    private JsonIntegrationProfileRepository integrations;
    private ExpressionModelRepository expressions;
    private ValidationModelRepository validations;
    private ActionModelRepository actions;
    private StorageProperties storage;
    private MetamodelDependentArtifactCleanupService dependentCleanup;
    private MetamodelDeletionJournalService deletionJournals;
    private MetamodelLifecycleService service;

    @BeforeEach void setUp() {
        storage = new StorageProperties(
                temporary.resolve("metamodels"), temporary.resolve("models"), temporary.resolve("notations"),
                temporary.resolve("validations"), temporary.resolve("quickfixes"), temporary.resolve("generators"),
                temporary.resolve("assets"), temporary.resolve("exports"), true);
        var mapper = JsonMapper.builder().findAndAddModules().build();
        EventModelProperties eventProperties = new EventModelProperties();
        eventProperties.setDirectory(temporary.resolve("events"));
        EditorDefinitionProperties editorProperties = new EditorDefinitionProperties();
        editorProperties.setDirectory(temporary.resolve("editors"));
        IntegrationProfileProperties integrationProperties = new IntegrationProfileProperties();
        integrationProperties.setDirectory(temporary.resolve("integrations"));
        eventDefinitions = new JsonEventDefinitionModelRepository(eventProperties, mapper);
        eventBindings = new JsonEventBindingModelRepository(eventProperties, mapper);
        editors = new JsonEditorDefinitionRepository(editorProperties, mapper);
        integrations = new JsonIntegrationProfileRepository(integrationProperties, mapper);

        canonicalService = mock(CanonicalMetamodelApplicationService.class);
        projectionCoordinator = mock(ProjectionCoordinator.class);
        diagramStorageService = mock(MetamodelDiagramStorageService.class);
        registryService = mock(MetamodelRegistryService.class);
        expressions = mock(ExpressionModelRepository.class);
        validations = mock(ValidationModelRepository.class);
        actions = mock(ActionModelRepository.class);
        dependentCleanup = mock(MetamodelDependentArtifactCleanupService.class);
        deletionJournals = new MetamodelDeletionJournalService(storage, mapper);
        when(dependentCleanup.referencedArtifactFiles("people")).thenReturn(List.of());
        when(expressions.fileName("people")).thenReturn("people.expressions.json");
        when(validations.fileName("people")).thenReturn("people.validations.json");
        when(actions.fileName("people")).thenReturn("people.actions.json");
        when(canonicalService.findById("people")).thenReturn(new MetamodelLoadResult(CanonicalMetamodelTestFixtures.valid("people"), "etag"));
        when(projectionCoordinator.findTargets("people")).thenReturn(new TargetConfigurationDocument("1.0", "people", null, Map.of()));

        service = new MetamodelLifecycleService(storage, new CanonicalMetamodelProperties(), canonicalService,
                projectionCoordinator, diagramStorageService, registryService, mapper, expressions, validations, actions,
                eventDefinitions, eventBindings, eventProperties, editorProperties, integrationProperties, dependentCleanup, deletionJournals);
    }

    @Test void reportsAndDeletesEventAndEditorDefinitions() {
        eventDefinitions.create(new EventDefinitionModel("1.0", "people-events", "people", "course.people", List.of(), Map.of()));
        eventBindings.create(new EventBindingModel("1.0", "people-event-bindings", "people", "people-events", "", List.of(), Map.of()));
        editors.create(editor("people-main-editor"));
        editors.create(editor("people-review-editor"));
        integrations.create(integration("people-plantuml-integration"));

        MetamodelDeletionImpact impact = service.analyzeDeletion("people");
        assertThat(impact.eventDefinitionExists()).isTrue();
        assertThat(impact.eventBindingExists()).isTrue();
        assertThat(impact.editorDefinitionFiles()).containsExactly("people-main-editor.editor.json", "people-review-editor.editor.json");
        assertThat(impact.integrationProfileFiles()).containsExactly("people-plantuml-integration.integration.json");

        MetamodelDeletionResult result = service.delete("people", true, true, "etag");
        assertThat(result.pendingFiles()).isEmpty();
        assertThat(eventDefinitions.exists("people")).isFalse();
        assertThat(eventBindings.exists("people")).isFalse();
        assertThat(editors.exists("people-main-editor")).isFalse();
        assertThat(editors.exists("people-review-editor")).isFalse();
        assertThat(integrations.exists("people-plantuml-integration")).isFalse();
        verify(canonicalService).delete("people", "etag");
        verify(registryService).initialize();
        verify(dependentCleanup).cleanup("people", true, true);
    }


    @Test void residualCleanupFailureDoesNotReportThePrimaryMetamodelDeletionAsFailed() throws Exception {
        when(validations.exists("people")).thenReturn(true);
        Files.createDirectories(storage.validations());
        Files.writeString(storage.validations().resolve("people.validations.json"), "{}");
        doThrow(new IllegalStateException("disk busy")).when(validations).deleteIfExists("people");

        MetamodelDeletionResult result = service.delete("people", true, true, "etag");

        verify(canonicalService).delete("people", "etag");
        assertThat(result.pendingFiles()).contains("validations/people.validations.json");
    }

    @Test void includesNewLayerArtifactsInPendingFilesAndRecoversThemWithoutRepeatingPrimaryDelete() {
        String dependent = "transformations/people-to-audit.transformation.json";
        when(dependentCleanup.referencedArtifactFiles("people")).thenReturn(List.of(dependent));
        when(dependentCleanup.managedArtifactExists(dependent)).thenReturn(true, false);

        MetamodelDeletionResult first = service.delete("people", true, true, "etag");
        assertThat(first.pendingFiles()).containsExactly(dependent);
        assertThat(deletionJournals.find("people").orElseThrow().status())
                .isEqualTo(MetamodelDeletionJournal.Status.RECOVERY_REQUIRED);

        MetamodelDeletionResult recovered = service.retryPendingCleanup("people");
        assertThat(recovered.pendingFiles()).isEmpty();
        verify(canonicalService).delete("people", "etag");
        verify(dependentCleanup).deleteManagedReferences(List.of(dependent));
        assertThat(deletionJournals.find("people").orElseThrow().status())
                .isEqualTo(MetamodelDeletionJournal.Status.COMPLETED);
    }

    @Test void recoversPreparedJournalOnlyWhenCanonicalFileIsAlreadyAbsent() {
        String dependent = "transformations/people-to-audit.transformation.json";
        deletionJournals.begin("people", true, true, List.of(dependent));
        when(dependentCleanup.managedArtifactExists(dependent)).thenReturn(true, false);

        MetamodelDeletionResult recovered = service.retryPendingCleanup("people");

        assertThat(recovered.pendingFiles()).isEmpty();
        verify(canonicalService, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        assertThat(deletionJournals.find("people").orElseThrow().status())
                .isEqualTo(MetamodelDeletionJournal.Status.COMPLETED);
    }

    private IntegrationProfileModel integration(String id) {
        IntegrationTarget target = new IntegrationTarget(IntegrationTechnology.PLANTUML,
                IntegrationTargetKind.DIAGRAM_PROJECTION, "", "people-docs", "", IntegrationBuildSystem.NONE,
                List.of(), List.of(), "", Map.of());
        InjectionPolicy policy = new InjectionPolicy(InjectionStrategy.GENERATE_PROJECTION, "docs/diagrams", "",
                true, true, true, false, false, true);
        return new IntegrationProfileModel("1.0", id, "people", "PlantUML", target,
                IntegrationDeliveryMode.PROJECTION_ONLY, Set.of(IntegrationFeature.PROJECT_EXPORT),
                List.of(new IntegrationArtifactSelection(IntegrationArtifactKind.METAMODEL, "people", true)),
                List.of(), policy, Set.of(), Map.of());
    }

    private EditorDefinitionModel editor(String id) {
        var representation = new RepresentationDefinition("representation:" + id, "Main", RepresentationKind.DIAGRAM,
                List.of(), List.of(), List.of(), List.of(), true, Map.of());
        return new EditorDefinitionModel("1.0", id, "people", id, List.of(representation),
                new ToolPaletteDefinition(List.of(), List.of()), List.of(),
                new EditorCapabilityManifest(Set.of(EditorCapability.MODEL_EDITING)), Map.of());
    }
}
