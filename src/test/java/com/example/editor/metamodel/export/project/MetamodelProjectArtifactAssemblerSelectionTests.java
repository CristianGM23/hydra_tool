package com.example.editor.metamodel.export.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.persistence.EditorDefinitionRepository;
import com.example.editor.event.flow.persistence.EventFlowModelRepository;
import com.example.editor.event.persistence.EventBindingModelRepository;
import com.example.editor.event.persistence.EventDefinitionModelRepository;
import com.example.editor.gamification.persistence.GamificationBindingRepository;
import com.example.editor.gamification.persistence.GamificationDefinitionRepository;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelLoadResult;
import com.example.editor.metamodel.behavior.persistence.OperationBehaviorModelRepository;
import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionModelSummary;
import com.example.editor.validation.action.persistence.ActionModelRepository;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelSummary;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.expression.persistence.StoredExpressionModel;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationModelSummary;
import com.example.editor.validation.validation.persistence.ValidationModelRepository;
import tools.jackson.databind.json.JsonMapper;

class MetamodelProjectArtifactAssemblerSelectionTests {
    @Test void allCompatibleIncludesEveryDocumentAndRequiresExplicitEditorWhenTargetNeedsOne(){
        Fixture fixture = fixture();
        var all = fixture.assembler.assemble("people", MetamodelProjectExportOptions.defaults(), false);
        assertThat(all.stream().filter(item -> item.kind() == IntegrationArtifactKind.EXPRESSIONS)).hasSize(2);
        assertThat(all.stream().filter(item -> item.kind() == IntegrationArtifactKind.VALIDATIONS)).hasSize(2);
        assertThat(all.stream().filter(item -> item.kind() == IntegrationArtifactKind.EDITOR_DEFINITION)).isEmpty();
        assertThatThrownBy(() -> fixture.assembler.assemble("people", MetamodelProjectExportOptions.defaults(), true))
                .isInstanceOf(MetamodelDesignException.class).hasMessageContaining("Multiple editor definitions").hasMessageContaining("editorId");
    }

    @Test void explicitSelectionIncludesOnlyRequestedLayersAndChosenEditor(){
        Fixture fixture = fixture();
        var options = new MetamodelProjectExportOptions(true, MetamodelProjectArtifactSelectionMode.EXPLICIT,
                List.of(new MetamodelProjectArtifactRef(IntegrationArtifactKind.VALIDATIONS, "validation-b")), "editor-b", Map.of());
        var result = fixture.assembler.assemble("people", options, true);
        assertThat(result).extracting(item -> item.kind() + ":" + item.documentId())
                .containsExactly("METAMODEL:people", "VALIDATIONS:validation-b", "EDITOR_DEFINITION:editor-b");
    }

    @Test void explicitSelectionRejectsDocumentsFromAnotherMetamodel(){
        Fixture fixture = fixture();
        var options = new MetamodelProjectExportOptions(true, MetamodelProjectArtifactSelectionMode.EXPLICIT,
                List.of(new MetamodelProjectArtifactRef(IntegrationArtifactKind.VALIDATIONS, "foreign")), "", Map.of());
        assertThatThrownBy(() -> fixture.assembler.assemble("people", options, false))
                .isInstanceOf(MetamodelDesignException.class).hasMessageContaining("does not belong to metamodel people");
    }

    private Fixture fixture(){
        var canonical = mock(CanonicalMetamodelApplicationService.class);
        when(canonical.findById("people")).thenReturn(new MetamodelLoadResult(CanonicalMetamodelTestFixtures.valid("people"), "metamodel-etag"));
        var expressionRepo = mock(ExpressionModelRepository.class); var validationRepo = mock(ValidationModelRepository.class); var actionRepo = mock(ActionModelRepository.class);
        var operationRepo = mock(OperationBehaviorModelRepository.class); var eventRepo = mock(EventDefinitionModelRepository.class); var eventBindingRepo = mock(EventBindingModelRepository.class);
        var flowRepo = mock(EventFlowModelRepository.class); var gamificationRepo = mock(GamificationDefinitionRepository.class); var gamificationBindingRepo = mock(GamificationBindingRepository.class);
        var editorRepo = mock(EditorDefinitionRepository.class); var mapper = mock(JsonMapper.class); var derived = mock(DefaultDerivedEditorDefinitionFactory.class);
        when(mapper.writeValueAsBytes(any())).thenReturn("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        ExpressionModel expA = mock(ExpressionModel.class); when(expA.id()).thenReturn("expr-a"); ExpressionModel expB = mock(ExpressionModel.class); when(expB.id()).thenReturn("expr-b");
        when(expressionRepo.findAll()).thenReturn(List.of(new ExpressionModelSummary("expr-a", "people", "1", 1, "ea"), new ExpressionModelSummary("expr-b", "people", "1", 1, "eb")));
        when(expressionRepo.findById("expr-a")).thenReturn(Optional.of(new StoredExpressionModel(expA, "ea", Path.of("a"))));
        when(expressionRepo.findById("expr-b")).thenReturn(Optional.of(new StoredExpressionModel(expB, "eb", Path.of("b"))));

        ValidationModel valA = mock(ValidationModel.class); when(valA.id()).thenReturn("validation-a"); ValidationModel valB = mock(ValidationModel.class); when(valB.id()).thenReturn("validation-b");
        when(validationRepo.findAll()).thenReturn(List.of(new ValidationModelSummary("validation-a", "people", "1", 1, 1, "va"), new ValidationModelSummary("validation-b", "people", "1", 1, 1, "vb"), new ValidationModelSummary("foreign", "other", "1", 1, 1, "vf")));
        when(validationRepo.findById("validation-a")).thenReturn(Optional.of(new StoredCanonicalDocument<>(valA, "va", Path.of("va"))));
        when(validationRepo.findById("validation-b")).thenReturn(Optional.of(new StoredCanonicalDocument<>(valB, "vb", Path.of("vb"))));

        when(actionRepo.findAll()).thenReturn(List.<ActionModelSummary>of()); when(operationRepo.findAll()).thenReturn(List.of());
        when(eventRepo.findByMetamodelId("people")).thenReturn(Optional.empty()); when(eventBindingRepo.findByMetamodelId("people")).thenReturn(Optional.empty()); when(flowRepo.findByMetamodelId("people")).thenReturn(Optional.empty());
        when(gamificationRepo.find("people")).thenReturn(Optional.empty()); when(gamificationBindingRepo.find("people")).thenReturn(Optional.empty());

        EditorDefinitionModel editorA = mock(EditorDefinitionModel.class); when(editorA.id()).thenReturn("editor-a"); when(editorA.name()).thenReturn("Editor A"); when(editorA.metamodelId()).thenReturn("people");
        EditorDefinitionModel editorB = mock(EditorDefinitionModel.class); when(editorB.id()).thenReturn("editor-b"); when(editorB.name()).thenReturn("Editor B"); when(editorB.metamodelId()).thenReturn("people");
        when(editorRepo.listIds()).thenReturn(List.of("editor-a", "editor-b"));
        when(editorRepo.findById("editor-a")).thenReturn(Optional.of(new StoredCanonicalDocument<>(editorA, "eda", Path.of("eda"))));
        when(editorRepo.findById("editor-b")).thenReturn(Optional.of(new StoredCanonicalDocument<>(editorB, "edb", Path.of("edb"))));

        return new Fixture(new MetamodelProjectArtifactAssembler(canonical, expressionRepo, validationRepo, actionRepo, operationRepo, eventRepo,
                eventBindingRepo, flowRepo, gamificationRepo, gamificationBindingRepo, editorRepo, derived, mapper));
    }

    private record Fixture(MetamodelProjectArtifactAssembler assembler) { }
}
