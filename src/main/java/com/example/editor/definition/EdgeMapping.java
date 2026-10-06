package com.example.editor.definition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Representación visual de una relación semántica directa o de una arista semántica basada en objeto. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EdgeMapping(String id, String relationId, String sourceClassId, String targetClassId, EditorEdgeLineType lineType,
        String labelExpressionId, Map<String, String> styles, List<ConditionalStyleRule> conditionalStyles,
        Map<String, String> annotations, SemanticEdgeDescriptor semanticEdge) {
    public EdgeMapping {
        id = StableIdentifierPolicy.requireElementId(id, "edgeMapping.id");
        relationId = normalizeRelationId(relationId, semanticEdge);
        sourceClassId = StableIdentifierPolicy.requireElementId(sourceClassId, "sourceClassId");
        targetClassId = StableIdentifierPolicy.requireElementId(targetClassId, "targetClassId");
        if(lineType == null) lineType = EditorEdgeLineType.SMOOTH_STEP;
        labelExpressionId = normalizeElementId(labelExpressionId, "labelExpressionId");
        styles = immutable(styles);
        conditionalStyles = conditionalStyles == null ? List.of() : List.copyOf(conditionalStyles);
        annotations = immutable(annotations);
    }

    /** Constructor compatible con mappings de referencia directa previos. */
    /** Descripción: Inicializa una instancia de EdgeMapping con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; relationId: identificador que delimita el recurso objetivo; sourceClassId: identificador que delimita el recurso objetivo; targetClassId: identificador que delimita el recurso objetivo; lineType: dato de entrada necesario para la operación; labelExpressionId: identificador que delimita el recurso objetivo; styles: dato de entrada necesario para la operación; conditionalStyles: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EdgeMapping(String id, String relationId, String sourceClassId, String targetClassId, EditorEdgeLineType lineType,
            String labelExpressionId, Map<String,String> styles, List<ConditionalStyleRule> conditionalStyles,
            Map<String,String> annotations) {
        this(id, relationId, sourceClassId, targetClassId, lineType, labelExpressionId, styles, conditionalStyles,
                annotations, null);
    }

    /** Descripción: Ejecuta la operación «semanticObjectBacked» correspondiente a la responsabilidad de EdgeMapping. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean semanticObjectBacked() { return semanticEdge != null; }

    /** Descripción: Ejecuta la operación «normalizeRelationId» correspondiente a la responsabilidad de EdgeMapping. Entradas: value: valor de entrada que debe procesarse; semanticEdge: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeRelationId(String value, SemanticEdgeDescriptor semanticEdge) {
        if(value == null || value.isBlank()) {
            if(semanticEdge != null) return "";
            return StableIdentifierPolicy.requireElementId(value, "relationId");
        }
        return StableIdentifierPolicy.requireElementId(value, "relationId");
    }
    /** Descripción: Ejecuta la operación «normalizeElementId» correspondiente a la responsabilidad de EdgeMapping. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeElementId(String value, String field) { if(value == null || value.isBlank()) return ""; return StableIdentifierPolicy.requireElementId(value, field); }
    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de EdgeMapping. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutable(Map<String, String> values) { return values == null || values.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
}
