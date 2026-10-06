package com.example.editor.definition.runtime.session.diagram;
public record EditorSessionDiagramNodeView(String elementId, double x, double y, double width, double height, double rotationDegrees) {
    /** Descripción: Mantiene compatibilidad con diagramas creados antes de incorporar la rotación. Entradas: elementId identifica el elemento y x, y, width y height definen su geometría visual. Salida: inicializa el nodo con rotación de cero grados. */
    /** Descripción: Inicializa una instancia de EditorSessionDiagramNodeView con sus dependencias y datos de entrada. Entradas: elementId: identificador que delimita el recurso objetivo; x: dato de entrada necesario para la operación; y: dato de entrada necesario para la operación; width: dato de entrada necesario para la operación; height: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionDiagramNodeView(String elementId, double x, double y, double width, double height) {
        this(elementId, x, y, width, height, 0);
    }
    public EditorSessionDiagramNodeView {
        elementId = elementId == null ? "" : elementId.trim();
        if(elementId.isBlank()) throw new IllegalArgumentException("elementId must not be blank");
        if(width <= 0) width = 180;
        if(height <= 0) height = 96;
        if(!Double.isFinite(rotationDegrees)) rotationDegrees = 0;
        rotationDegrees %= 360;
        if(rotationDegrees < 0) rotationDegrees += 360;
    }
}
