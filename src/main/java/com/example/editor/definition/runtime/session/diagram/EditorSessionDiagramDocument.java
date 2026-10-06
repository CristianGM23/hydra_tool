package com.example.editor.definition.runtime.session.diagram;
import java.util.List;
import com.example.editor.validation.contract.StableIdentifierPolicy;
public record EditorSessionDiagramDocument(String schemaVersion, String id, String metamodelId, String editorId,
        String representationId, List<EditorSessionDiagramNodeView> nodes, List<EditorSessionDiagramEdgeView> edges) {
    public EditorSessionDiagramDocument {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        if(!"1.0".equals(schemaVersion)) throw new IllegalArgumentException("Unsupported editor session diagram schema version: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "editorSessionDiagram.id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "editorSessionDiagram.metamodelId");
        editorId = StableIdentifierPolicy.requireDocumentId(editorId, "editorSessionDiagram.editorId");
        representationId = representationId == null ? "" : representationId.trim();
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
