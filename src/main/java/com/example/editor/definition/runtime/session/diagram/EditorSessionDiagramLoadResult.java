package com.example.editor.definition.runtime.session.diagram;
public record EditorSessionDiagramLoadResult(EditorSessionDiagramDocument diagram, String etag) {
    public EditorSessionDiagramLoadResult { etag = etag == null ? "" : etag; }
}
