package com.example.editor.definition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Vista o representación disponible dentro de un editor de modelos. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepresentationDefinition(String id, String name, RepresentationKind kind, List<String> rootClassIds,
        List<NodeMapping> nodeMappings, List<EdgeMapping> edgeMappings, List<String> toolIds,
        List<RepresentationLayerDefinition> layers, List<RepresentationFilterDefinition> filters, boolean editable,
        Map<String, String> annotations) {
    public RepresentationDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "representation.id");
        name = requireText(name, "representation.name");
        if(kind == null) kind = RepresentationKind.DIAGRAM;
        rootClassIds = normalizeIds(rootClassIds, "rootClassIds");
        nodeMappings = nodeMappings == null ? List.of() : List.copyOf(nodeMappings);
        edgeMappings = edgeMappings == null ? List.of() : List.copyOf(edgeMappings);
        toolIds = normalizeIds(toolIds, "toolIds");
        layers = layers == null || layers.isEmpty() ? List.of() : List.copyOf(layers);
        filters = filters == null || filters.isEmpty() ? List.of() : List.copyOf(filters);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }

    /** Constructor compatible con definiciones previas sin capas/filtros neutrales explícitos. */
    /** Descripción: Inicializa una instancia de RepresentationDefinition con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; rootClassIds: dato de entrada necesario para la operación; nodeMappings: dato de entrada necesario para la operación; edgeMappings: dato de entrada necesario para la operación; toolIds: dato de entrada necesario para la operación; editable: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RepresentationDefinition(String id, String name, RepresentationKind kind, List<String> rootClassIds,
            List<NodeMapping> nodeMappings, List<EdgeMapping> edgeMappings, List<String> toolIds, boolean editable,
            Map<String,String> annotations) {
        this(id, name, kind, rootClassIds, nodeMappings, edgeMappings, toolIds, List.of(), List.of(), editable, annotations);
    }

    /** Devuelve los identificadores de mappings que pertenecen a capas activas por defecto. Los mappings sin capa permanecen activos. */
    /** Descripción: Ejecuta la operación «defaultActiveMappingIds» correspondiente a la responsabilidad de RepresentationDefinition. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public java.util.Set<String> defaultActiveMappingIds() {
        java.util.Set<String> referenced = new java.util.LinkedHashSet<>();
        java.util.Set<String> active = new java.util.LinkedHashSet<>();
        for(RepresentationLayerDefinition layer : layers) {
            referenced.addAll(layer.nodeMappingIds()); referenced.addAll(layer.edgeMappingIds());
            if(layer.activeByDefault()) { active.addAll(layer.nodeMappingIds()); active.addAll(layer.edgeMappingIds()); }
        }
        nodeMappings.stream().map(NodeMapping::id).filter(id -> !referenced.contains(id)).forEach(active::add);
        edgeMappings.stream().map(EdgeMapping::id).filter(id -> !referenced.contains(id)).forEach(active::add);
        return java.util.Collections.unmodifiableSet(active);
    }

    /** Devuelve las herramientas que pertenecen a capas activas por defecto; las no clasificadas permanecen activas. */
    /** Descripción: Ejecuta la operación «defaultActiveLayerToolIds» correspondiente a la responsabilidad de RepresentationDefinition. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public java.util.Set<String> defaultActiveLayerToolIds() {
        java.util.Set<String> referenced = new java.util.LinkedHashSet<>();
        java.util.Set<String> active = new java.util.LinkedHashSet<>();
        for(RepresentationLayerDefinition layer : layers) {
            referenced.addAll(layer.toolIds());
            if(layer.activeByDefault()) active.addAll(layer.toolIds());
        }
        toolIds.stream().filter(id -> !referenced.contains(id)).forEach(active::add);
        return java.util.Collections.unmodifiableSet(active);
    }

    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field) { if(value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank"); return value.trim(); }
    /** Descripción: Ejecuta la operación «normalizeIds» correspondiente a la responsabilidad de RepresentationDefinition. Entradas: values: colección de elementos que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> normalizeIds(List<String> values, String field) { if(values == null) return List.of(); return values.stream().map(value -> StableIdentifierPolicy.requireElementId(value, field)).toList(); }
}
