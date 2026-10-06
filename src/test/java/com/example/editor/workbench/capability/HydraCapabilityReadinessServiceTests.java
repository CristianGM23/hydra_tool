package com.example.editor.workbench.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionSummary;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelSummary;
import com.example.editor.event.flow.EventFlowApplicationService;
import com.example.editor.event.flow.EventFlowSummary;
import com.example.editor.gamification.GamificationApplicationService;
import com.example.editor.gamification.GamificationCatalogResult;
import com.example.editor.gamification.GamificationSummary;
import com.example.editor.grammar.application.GrammarApplicationService;
import com.example.editor.grammar.application.GrammarLoadResponse;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjection;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.grammar.visualization.GrammarDiagramProjectionStatus;
import com.example.editor.integration.IntegrationDeliveryMode;
import com.example.editor.integration.IntegrationProfileApplicationService;
import com.example.editor.integration.IntegrationProfileSummary;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelLoadResult;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.validation.ValidationModelApplicationService;
import com.example.editor.validation.validation.ValidationModelSummary;

class HydraCapabilityReadinessServiceTests {
    @Test
    void aggregatesPortableLayersWithoutRequiringTheMetamodelDesignerScreen(){
        CanonicalMetamodelApplicationService metamodels = mock(CanonicalMetamodelApplicationService.class);
        ValidationModelApplicationService validations = mock(ValidationModelApplicationService.class);
        EventModelApplicationService events = mock(EventModelApplicationService.class);
        EventFlowApplicationService flows = mock(EventFlowApplicationService.class);
        GamificationApplicationService gamification = mock(GamificationApplicationService.class);
        EditorDefinitionApplicationService editors = mock(EditorDefinitionApplicationService.class);
        IntegrationProfileApplicationService integrations = mock(IntegrationProfileApplicationService.class);
        when(metamodels.findById("people")).thenReturn(new MetamodelLoadResult(new MetaModel("2.1", "people", "People", List.of(), List.of(), List.of(), List.of(), Map.of()), "etag"));
        when(validations.findAll()).thenReturn(List.of(new ValidationModelSummary("validation-people", "people", "1.0", 2, 1, "etag")));
        when(events.list()).thenReturn(List.of(new EventModelSummary("people", "events", 3, "bindings", 2, "e", "b")));
        when(flows.list()).thenReturn(List.of(new EventFlowSummary("flows", "people", 1, 4, "etag")));
        when(gamification.listWithDiagnostics()).thenReturn(new GamificationCatalogResult(
                List.of(new GamificationSummary("people", "game", 2, 1, 1, "e", "b")), List.of()));
        when(editors.list("people")).thenReturn(List.of(new EditorDefinitionSummary("editor", "people", "People editor", 2, 3, 1, "etag")));
        when(integrations.list("people")).thenReturn(List.of(new IntegrationProfileSummary("langium", "people", "Langium", IntegrationTechnology.LANGIUM, IntegrationDeliveryMode.GENERATED_MODULE, 4, 5, "etag")));

        HydraCapabilityReadiness readiness = new HydraCapabilityReadinessService(metamodels, validations, events, flows, gamification, editors, integrations).summarize("people");

        assertThat(readiness.layers()).hasSize(7);
        assertThat(readiness.layers()).extracting(HydraCapabilityLayerSummary::id).containsExactly("METAMODEL", "VALIDATIONS", "EVENTS", "EVENT_FLOWS", "GAMIFICATION", "EDITORS", "INTEGRATIONS");
        assertThat(readiness.configuredLayers()).isEqualTo(7);
        assertThat(readiness.score()).isEqualTo(100);
        assertThat(readiness.recommendedNextSteps()).contains("Validate native targets");
    }
    @Test
    void keepsTheReadinessResultWhenOneGamificationDocumentIsMalformed(){
        CanonicalMetamodelApplicationService metamodels = mock(CanonicalMetamodelApplicationService.class);
        ValidationModelApplicationService validations = mock(ValidationModelApplicationService.class);
        EventModelApplicationService events = mock(EventModelApplicationService.class);
        EventFlowApplicationService flows = mock(EventFlowApplicationService.class);
        GamificationApplicationService gamification = mock(GamificationApplicationService.class);
        EditorDefinitionApplicationService editors = mock(EditorDefinitionApplicationService.class);
        IntegrationProfileApplicationService integrations = mock(IntegrationProfileApplicationService.class);
        when(metamodels.findById("people")).thenReturn(new MetamodelLoadResult(new MetaModel("2.1", "people", "People", List.of(), List.of(), List.of(), List.of(), Map.of()), "etag"));
        when(validations.findAll()).thenReturn(List.of());
        when(events.list()).thenReturn(List.of());
        when(flows.list()).thenReturn(List.of());
        when(gamification.listWithDiagnostics()).thenReturn(GamificationCatalogResult.empty(
                "Skipped invalid gamification document c16e3a29: Could not parse document: data/gamification/c16e3a29.gamification-definitions.json"));
        when(editors.list("people")).thenReturn(List.of());
        when(integrations.list("people")).thenReturn(List.of());

        HydraCapabilityReadiness readiness = new HydraCapabilityReadinessService(metamodels, validations, events,
                flows, gamification, editors, integrations).summarize("people");

        assertThat(readiness.metamodelId()).isEqualTo("people");
        assertThat(readiness.layers()).hasSize(7);
        assertThat(readiness.warnings()).singleElement().asString().contains("Could not parse document");
    }

    @Test
    void keepsGrammarFirstSemanticContextStableWhenGrammarSourcesChange(){
        CanonicalMetamodelApplicationService metamodels = mock(CanonicalMetamodelApplicationService.class);
        GrammarApplicationService grammars = mock(GrammarApplicationService.class);
        GrammarClassDiagramProjectionService projection = mock(GrammarClassDiagramProjectionService.class);
        ValidationModelApplicationService validations = mock(ValidationModelApplicationService.class);
        EventModelApplicationService events = mock(EventModelApplicationService.class);
        EventFlowApplicationService flows = mock(EventFlowApplicationService.class);
        GamificationApplicationService gamification = mock(GamificationApplicationService.class);
        EditorDefinitionApplicationService editors = mock(EditorDefinitionApplicationService.class);
        IntegrationProfileApplicationService integrations = mock(IntegrationProfileApplicationService.class);

        GrammarDefinitionModel first = grammar("grammar-orders", "entry Model: elements+=Element*;");
        GrammarDefinitionModel second = grammar("grammar-orders", "entry Model: elements+=Element+;");
        MetaModel inferred = new MetaModel("2.1", "inferred:grammar-orders", "Orders", List.of(), List.of(), List.of(), List.of(), Map.of());
        GrammarClassDiagramProjection projected = new GrammarClassDiagramProjection("grammar-orders", 1, "a", "a",
                GrammarDiagramProjectionStatus.COMPLETE, inferred, List.of(), List.of(), List.of(), "p", Map.of());
        when(grammars.find("grammar-orders")).thenReturn(new GrammarLoadResponse(first, "e1"), new GrammarLoadResponse(second, "e2"));
        when(projection.project(org.mockito.ArgumentMatchers.any(GrammarDefinitionModel.class),
                org.mockito.ArgumentMatchers.isNull())).thenReturn(projected);
        when(validations.findAll()).thenReturn(List.of());
        when(events.list()).thenReturn(List.of());
        when(flows.list()).thenReturn(List.of());
        when(gamification.listWithDiagnostics()).thenReturn(new GamificationCatalogResult(List.of(), List.of()));
        when(editors.list("grammar.grammar-orders")).thenReturn(List.of());
        when(integrations.list("grammar.grammar-orders")).thenReturn(List.of());

        HydraCapabilityReadinessService service = new HydraCapabilityReadinessService(metamodels, grammars, projection,
                validations, events, flows, gamification, editors, integrations);
        HydraCapabilityReadiness before = service.summarizeGrammar("grammar-orders");
        HydraCapabilityReadiness after = service.summarizeGrammar("grammar-orders");

        assertThat(before.semanticContextId()).isEqualTo("grammar.grammar-orders");
        assertThat(after.semanticContextId()).isEqualTo(before.semanticContextId());
    }

    private static GrammarDefinitionModel grammar(String id,String content){
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium", content, "", true, false);
        return new GrammarDefinitionModel("1.0", id, "Orders", GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST,
                "", "langium-default", "main", "Model", List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
    }

}
