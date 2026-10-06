package com.example.editor.definition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Representación visual de una clase en una vista de un editor de modelos. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NodeMapping(String id, String targetClassId, String representationName, EditorNodeShape shape, double defaultWidth,
        double defaultHeight, double minimumWidth, double minimumHeight, String labelExpressionId, String imageAssetPath,
        EditorImageFit imageFit, boolean container, List<String> containmentRelationIds, double containerPadding,
        Map<String, String> styles, List<ConditionalStyleRule> conditionalStyles, Map<String, String> annotations) {
    public NodeMapping {
        id = StableIdentifierPolicy.requireElementId(id, "nodeMapping.id");
        targetClassId = StableIdentifierPolicy.requireElementId(targetClassId, "targetClassId");
        representationName = requireText(representationName, "representationName");
        if(shape == null) shape = EditorNodeShape.ROUNDED_RECTANGLE;
        if(defaultWidth <= 0 || defaultHeight <= 0) throw new IllegalArgumentException("Default node size must be positive");
        if(minimumWidth <= 0 || minimumHeight <= 0) throw new IllegalArgumentException("Minimum node size must be positive");
        if(defaultWidth < minimumWidth || defaultHeight < minimumHeight) throw new IllegalArgumentException("Default node size must not be smaller than the minimum size");
        labelExpressionId = normalizeElementId(labelExpressionId, "labelExpressionId");
        imageAssetPath = normalizeAssetPath(imageAssetPath);
        if(imageFit == null) imageFit = EditorImageFit.CONTAIN;
        containmentRelationIds = normalizeIds(containmentRelationIds, "containmentRelationIds");
        if(containerPadding <= 0) containerPadding = 18;
        styles = immutable(styles);
        conditionalStyles = conditionalStyles == null || conditionalStyles.isEmpty() ? List.of() : List.copyOf(conditionalStyles);
        annotations = immutable(annotations);
    }
    /** Descripción: Ejecuta la operación «imageBacked» correspondiente a la responsabilidad de NodeMapping. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean imageBacked() { return shape == EditorNodeShape.PROJECT_IMAGE; }
    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field) { if(value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank"); return value.trim(); }
    /** Descripción: Ejecuta la operación «normalizeElementId» correspondiente a la responsabilidad de NodeMapping. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeElementId(String value, String field) { if(value == null || value.isBlank()) return ""; return StableIdentifierPolicy.requireElementId(value, field); }
    /** Descripción: Ejecuta la operación «normalizeIds» correspondiente a la responsabilidad de NodeMapping. Entradas: values: colección de elementos que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> normalizeIds(List<String> values, String field) { if(values == null || values.isEmpty()) return List.of(); return values.stream().map(value -> StableIdentifierPolicy.requireElementId(value, field)).distinct().toList(); }
    /** Descripción: Ejecuta la operación «normalizeAssetPath» correspondiente a la responsabilidad de NodeMapping. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeAssetPath(String value) {
        if(value == null || value.isBlank()) return "";
        String normalized = value.trim().replace('\\', '/');
        if(normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*") || normalized.contains("../") || normalized.equals("..")) throw new IllegalArgumentException("imageAssetPath must be a safe project-relative path");
        return normalized;
    }
    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de NodeMapping. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutable(Map<String, String> values) { return values == null || values.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
}
