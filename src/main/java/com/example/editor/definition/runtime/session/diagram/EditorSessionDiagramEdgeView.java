package com.example.editor.definition.runtime.session.diagram;
import java.util.List;
public record EditorSessionDiagramEdgeView(String relationInstanceId, List<EditorSessionDiagramPoint> points) {
    public EditorSessionDiagramEdgeView {
        relationInstanceId = relationInstanceId == null ? "" : relationInstanceId.trim();
        if(relationInstanceId.isBlank()) throw new IllegalArgumentException("relationInstanceId must not be blank");
        points = points == null ? List.of() : List.copyOf(points);
    }
}
