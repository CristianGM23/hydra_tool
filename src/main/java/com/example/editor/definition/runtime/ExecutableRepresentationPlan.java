package com.example.editor.definition.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.editor.definition.EdgeMapping;
import com.example.editor.definition.EditorTool;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.RepresentationFilterDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.definition.RepresentationLayerDefinition;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Índices inmutables necesarios para ejecutar una representación. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutableRepresentationPlan(String id, String name, RepresentationKind kind,
        List<String> rootClassIds, boolean editable,
        Map<String, List<NodeMapping>> nodeMappingsByClassId,
        Map<String, List<EdgeMapping>> edgeMappingsByRelationId,
        Map<String, List<EdgeMapping>> edgeMappingsBySemanticClassId,
        Map<String, EditorTool> toolsById,
        List<ExecutablePaletteGroup> paletteGroups,
        List<RepresentationLayerDefinition> layers,
        List<RepresentationFilterDefinition> filters,
        Map<String, String> annotations) {

    public ExecutableRepresentationPlan {
        rootClassIds = rootClassIds == null ? List.of() : List.copyOf(rootClassIds);
        nodeMappingsByClassId = immutableLists(nodeMappingsByClassId);
        edgeMappingsByRelationId = immutableLists(edgeMappingsByRelationId);
        edgeMappingsBySemanticClassId = immutableLists(edgeMappingsBySemanticClassId);
        toolsById = immutableMap(toolsById);
        paletteGroups = paletteGroups == null ? List.of() : List.copyOf(paletteGroups);
        layers = layers == null || layers.isEmpty() ? List.of() : List.copyOf(layers);
        filters = filters == null || filters.isEmpty() ? List.of() : List.copyOf(filters);
        annotations = immutableMap(annotations);
    }

    /** Constructor compatible con planes previos que ya soportaban aristas semánticas pero no capas/filtros. */
    /** Descripción: Inicializa una instancia de ExecutableRepresentationPlan con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; rootClassIds: dato de entrada necesario para la operación; editable: dato de entrada necesario para la operación; nodeMappingsByClassId: identificador que delimita el recurso objetivo; edgeMappingsByRelationId: identificador que delimita el recurso objetivo; edgeMappingsBySemanticClassId: identificador que delimita el recurso objetivo; toolsById: identificador que delimita el recurso objetivo; paletteGroups: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExecutableRepresentationPlan(String id, String name, RepresentationKind kind, List<String> rootClassIds,
            boolean editable, Map<String,List<NodeMapping>> nodeMappingsByClassId,
            Map<String,List<EdgeMapping>> edgeMappingsByRelationId, Map<String,List<EdgeMapping>> edgeMappingsBySemanticClassId,
            Map<String,EditorTool> toolsById, List<ExecutablePaletteGroup> paletteGroups, Map<String,String> annotations) {
        this(id,name,kind,rootClassIds,editable,nodeMappingsByClassId,edgeMappingsByRelationId,edgeMappingsBySemanticClassId,
                toolsById,paletteGroups,List.of(),List.of(),annotations);
    }

    /** Constructor compatible con planes previos que solo indexaban relaciones directas. */
    /** Descripción: Inicializa una instancia de ExecutableRepresentationPlan con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; rootClassIds: dato de entrada necesario para la operación; editable: dato de entrada necesario para la operación; nodeMappingsByClassId: identificador que delimita el recurso objetivo; edgeMappingsByRelationId: identificador que delimita el recurso objetivo; toolsById: identificador que delimita el recurso objetivo; paletteGroups: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ExecutableRepresentationPlan(String id, String name, RepresentationKind kind, List<String> rootClassIds,
            boolean editable, Map<String,List<NodeMapping>> nodeMappingsByClassId,
            Map<String,List<EdgeMapping>> edgeMappingsByRelationId, Map<String,EditorTool> toolsById,
            List<ExecutablePaletteGroup> paletteGroups, Map<String,String> annotations) {
        this(id,name,kind,rootClassIds,editable,nodeMappingsByClassId,edgeMappingsByRelationId,Map.of(),toolsById,paletteGroups,
                List.of(),List.of(),annotations);
    }

    /** Capas que deben estar visibles al abrir una representación sin preferencias de sesión. */
    /** Descripción: Ejecuta la operación «defaultActiveLayers» correspondiente a la responsabilidad de ExecutableRepresentationPlan. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<RepresentationLayerDefinition> defaultActiveLayers() {
        return layers.stream().filter(RepresentationLayerDefinition::activeByDefault).toList();
    }

    /** Filtros que pueden ser ejecutados de forma neutral al abrir la representación. */
    /** Descripción: Ejecuta la operación «executableDefaultFilters» correspondiente a la responsabilidad de ExecutableRepresentationPlan. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<RepresentationFilterDefinition> executableDefaultFilters() {
        return filters.stream().filter(RepresentationFilterDefinition::activeByDefault)
                .filter(RepresentationFilterDefinition::executable).toList();
    }

    /** Descripción: Ejecuta la operación «immutableMap» correspondiente a la responsabilidad de ExecutableRepresentationPlan. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static <T> Map<String, T> immutableMap(Map<String, T> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** Descripción: Ejecuta la operación «immutableLists» correspondiente a la responsabilidad de ExecutableRepresentationPlan. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static <T> Map<String, List<T>> immutableLists(Map<String, List<T>> values) {
        if(values == null || values.isEmpty()) return Map.of();
        Map<String, List<T>> copied = new LinkedHashMap<>();
        values.forEach((key, value) -> copied.put(key, value == null ? List.of() : List.copyOf(value)));
        return Collections.unmodifiableMap(copied);
    }
}
