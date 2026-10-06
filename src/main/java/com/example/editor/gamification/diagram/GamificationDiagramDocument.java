package com.example.editor.gamification.diagram;

import java.util.List;
import com.example.editor.gamification.GamificationContractVersions;
import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Documento visual de gamificación. La geometría no forma parte de la semántica ni del ledger. */
public record GamificationDiagramDocument(String schemaVersion, String id, String metamodelId,
        String definitionModelId, String bindingModelId, List<GamificationDiagramNodeView> nodes,
        List<GamificationDiagramEdgeView> edges) {
    public GamificationDiagramDocument {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = GamificationContractVersions.DIAGRAM;
        if(!GamificationContractVersions.DIAGRAM.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported gamification diagram schemaVersion");
        id = StableIdentifierPolicy.requireDocumentId(id, "gamificationDiagram.id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "gamificationDiagram.metamodelId");
        definitionModelId = StableIdentifierPolicy.requireDocumentId(definitionModelId, "gamificationDiagram.definitionModelId");
        bindingModelId = StableIdentifierPolicy.requireDocumentId(bindingModelId, "gamificationDiagram.bindingModelId");
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
