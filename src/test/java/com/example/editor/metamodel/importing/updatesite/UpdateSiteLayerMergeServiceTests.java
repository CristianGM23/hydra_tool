package com.example.editor.metamodel.importing.updatesite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionLoadResult;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateSiteLayerMergeServiceTests {
    private record Element(String id, String value) { }

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final UpdateSiteLayerMergeService service = new UpdateSiteLayerMergeService(null, null, null,
            new UpdateSiteImportProperties(), mapper);

    private UpdateSiteLayerMergeRequest request(UpdateSiteMergeDecision defaultDecision, Map<String, UpdateSiteMergeDecision> decisions) {
        return new UpdateSiteLayerMergeRequest("library", "library-editor", "etag", "candidate", false, false,
                UpdateSiteLayerSelection.all(), defaultDecision, decisions);
    }

    private List<Element> merge(UpdateSiteLayerMergeRequest request, List<UpdateSiteLayerMergeItem> items) {
        return service.mergeList("element", "element", List.of(new Element("a", "existing"), new Element("only-existing", "x")),
                List.of(new Element("a", "recovered"), new Element("only-recovered", "y")), Element::id, Element::value, request, items);
    }

    @Test
    void requiresAnEtagBeforeAnyPersistentMerge() {
        EditorDefinitionApplicationService editorService = mock(EditorDefinitionApplicationService.class);
        when(editorService.find("library", "library-editor"))
                .thenReturn(new EditorDefinitionLoadResult(null, "etag-current", null));
        UpdateSiteLayerMergeService withEditors = new UpdateSiteLayerMergeService(null, null, editorService,
                new UpdateSiteImportProperties(), JsonMapper.builder().findAndAddModules().build());
        UpdateSiteLayerMergeRequest request = new UpdateSiteLayerMergeRequest("library", "library-editor", "",
                "candidate", false, true, UpdateSiteLayerSelection.all(),
                UpdateSiteMergeDecision.KEEP_EXISTING, Map.of());

        assertThatThrownBy(() -> withEditors.merge(List.of(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedEtag");
    }

    @Test
    void aCollisionWithoutAnExplicitDecisionStaysPendingEvenWhenADefaultDecisionIsSent() {
        for (UpdateSiteMergeDecision defaultDecision : UpdateSiteMergeDecision.values()) {
            List<UpdateSiteLayerMergeItem> items = new ArrayList<>();
            UpdateSiteLayerMergeRequest request = request(defaultDecision, Map.of());

            List<Element> result = merge(request, items);

            assertThat(service.unresolvedConflicts(request, items)).as("default=" + defaultDecision).containsExactly("element:a");
            // Mientras esté pendiente se previsualiza conservando lo existente: nunca se aplica lo recuperado en silencio.
            assertThat(result).contains(new Element("a", "existing")).doesNotContain(new Element("a", "recovered"));
        }
    }

    @Test
    void anExplicitDecisionResolvesTheCollisionAndIsRecorded() {
        for (UpdateSiteMergeDecision decision : List.of(UpdateSiteMergeDecision.KEEP_EXISTING, UpdateSiteMergeDecision.USE_RECOVERED, UpdateSiteMergeDecision.SKIP)) {
            List<UpdateSiteLayerMergeItem> items = new ArrayList<>();
            UpdateSiteLayerMergeRequest request = request(UpdateSiteMergeDecision.KEEP_EXISTING, Map.of("element:a", decision));

            List<Element> result = merge(request, items);

            assertThat(service.unresolvedConflicts(request, items)).isEmpty();
            service.requireKnownDecisionKeys(request, items);
            assertThat(service.appliedDecisions(request, items)).containsExactly(Map.entry("element:a", decision.name()));
            assertThat(result.stream().filter(value -> value.id().equals("a")).map(Element::value).toList())
                    .isEqualTo(switch (decision) { case KEEP_EXISTING -> List.of("existing"); case USE_RECOVERED -> List.of("recovered"); case SKIP -> List.<String>of(); });
        }
    }

    @Test
    void elementsWithoutACollisionKeepTheirDefaultsAndNeedNoDecision() {
        List<UpdateSiteLayerMergeItem> items = new ArrayList<>();
        UpdateSiteLayerMergeRequest request = request(null, Map.of("element:a", UpdateSiteMergeDecision.KEEP_EXISTING));

        List<Element> result = merge(request, items);

        assertThat(service.unresolvedConflicts(request, items)).isEmpty();
        assertThat(result).contains(new Element("only-existing", "x"), new Element("only-recovered", "y"));
    }

    @Test
    void anExplicitDecisionThatMakesNoSenseForTheElementIsRejectedInsteadOfBeingReinterpreted() {
        assertThatThrownBy(() -> merge(request(null, Map.of("element:only-existing", UpdateSiteMergeDecision.USE_RECOVERED)), new ArrayList<>()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("USE_RECOVERED is not valid");
        assertThatThrownBy(() -> merge(request(null, Map.of("element:only-recovered", UpdateSiteMergeDecision.KEEP_EXISTING)), new ArrayList<>()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("KEEP_EXISTING is not valid");
    }

    @Test
    void decisionsThatMatchNoElementOfTheMergeAreRejected() {
        List<UpdateSiteLayerMergeItem> items = new ArrayList<>();
        UpdateSiteLayerMergeRequest request = request(null, Map.of("element:a", UpdateSiteMergeDecision.SKIP, "element:ghost", UpdateSiteMergeDecision.SKIP));
        merge(request, items);

        assertThatThrownBy(() -> service.requireKnownDecisionKeys(request, items))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("element:ghost");
    }

    @Test
    void nullOrUnknownDecisionValuesAreRejectedByTheContract() {
        Map<String, UpdateSiteMergeDecision> withNull = new java.util.HashMap<>();
        withNull.put("element:a", null);
        assertThatThrownBy(() -> request(null, withNull)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must not be null");
        // Este flujo no tiene MERGE (la fusión ya es por elemento): el valor se rechaza al leer la solicitud.
        assertThatThrownBy(() -> mapper.readValue("{\"targetMetamodelId\":\"m\",\"decisions\":{\"element:a\":\"MERGE\"}}", UpdateSiteLayerMergeRequest.class))
                .isInstanceOf(RuntimeException.class);
    }
}
