package com.example.editor.definition;
import java.util.List;
/** Herramienta que crea una relación semántica directa o una arista semántica basada en objeto. */
public record EdgeCreationTool(String id, String label, String description, String icon, List<String> representationIds,
        String visibleExpressionId, String enabledExpressionId, String edgeMappingId, String relationId,
        List<String> sourceClassIds, List<String> targetClassIds, boolean reconnectable,
        SemanticEdgeDescriptor semanticEdge) implements EditorTool {
    public EdgeCreationTool {
        id = EditorToolSupport.id(id, "tool.id");
        label = EditorToolSupport.text(label, id);
        description = EditorToolSupport.text(description, "");
        icon = EditorToolSupport.text(icon, "");
        representationIds = EditorToolSupport.ids(representationIds, "representationIds");
        visibleExpressionId = EditorToolSupport.optionalId(visibleExpressionId, "visibleExpressionId");
        enabledExpressionId = EditorToolSupport.optionalId(enabledExpressionId, "enabledExpressionId");
        edgeMappingId = EditorToolSupport.id(edgeMappingId, "edgeMappingId");
        relationId = semanticEdge == null ? EditorToolSupport.id(relationId, "relationId") : EditorToolSupport.optionalId(relationId, "relationId");
        sourceClassIds = EditorToolSupport.ids(sourceClassIds, "sourceClassIds");
        targetClassIds = EditorToolSupport.ids(targetClassIds, "targetClassIds");
    }

    /** Constructor compatible con herramientas de relación directa previas. */
    /** Descripción: Inicializa una instancia de EdgeCreationTool con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; label: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación; icon: dato de entrada necesario para la operación; representationIds: dato de entrada necesario para la operación; visibleExpressionId: identificador que delimita el recurso objetivo; enabledExpressionId: identificador que delimita el recurso objetivo; edgeMappingId: identificador que delimita el recurso objetivo; relationId: identificador que delimita el recurso objetivo; sourceClassIds: dato de entrada necesario para la operación; targetClassIds: dato de entrada necesario para la operación; reconnectable: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EdgeCreationTool(String id, String label, String description, String icon, List<String> representationIds,
            String visibleExpressionId, String enabledExpressionId, String edgeMappingId, String relationId,
            List<String> sourceClassIds, List<String> targetClassIds, boolean reconnectable) {
        this(id, label, description, icon, representationIds, visibleExpressionId, enabledExpressionId, edgeMappingId,
                relationId, sourceClassIds, targetClassIds, reconnectable, null);
    }

    /** Descripción: Ejecuta la operación «semanticObjectBacked» correspondiente a la responsabilidad de EdgeCreationTool. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean semanticObjectBacked() { return semanticEdge != null; }
}
