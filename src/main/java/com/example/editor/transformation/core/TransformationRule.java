package com.example.editor.transformation.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.editor.semantic.expression.ExpressionDefinition;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransformationRule(
        String id,
        String name,
        boolean enabled,
        String sourceEndpointId,
        String sourceTypeId,
        String sourceAlias,
        ExpressionDefinition guard,
        List<TransformationTargetPattern> targets,
        List<TransformationBinding> bindings,
        Map<String, String> annotations,
        TransformationRuleDirection direction,
        String correspondenceId) {

    public TransformationRule {
        id = text(id);
        name = text(name);
        sourceEndpointId = text(sourceEndpointId);
        sourceTypeId = text(sourceTypeId);
        sourceAlias = text(sourceAlias);
        targets = targets == null ? List.of() : List.copyOf(targets);
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        annotations = immutable(annotations);
        direction = direction == null ? TransformationRuleDirection.FORWARD : direction;
        correspondenceId = text(correspondenceId);
        if(correspondenceId.isBlank()) correspondenceId = id;
    }

    /** Backward-compatible constructor: historical rules are forward rules and own their correspondence. */
    /** Descripción: Inicializa una instancia de TransformationRule con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; enabled: dato de entrada necesario para la operación; sourceEndpointId: identificador que delimita el recurso objetivo; sourceTypeId: identificador que delimita el recurso objetivo; sourceAlias: dato de entrada necesario para la operación; guard: dato de entrada necesario para la operación; targets: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationRule(
            String id, String name, boolean enabled, String sourceEndpointId, String sourceTypeId, String sourceAlias,
            ExpressionDefinition guard, List<TransformationTargetPattern> targets, List<TransformationBinding> bindings,
            Map<String, String> annotations) {
        this(id, name, enabled, sourceEndpointId, sourceTypeId, sourceAlias, guard, targets, bindings, annotations,
                TransformationRuleDirection.FORWARD, id);
    }

    /** Descripción: Normaliza texto de una regla. Entradas: value: texto opcional. Salida: texto no nulo sin espacios extremos. */
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TransformationRule. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }

    /** Descripción: Copia las anotaciones de una regla. Entradas: values: mapa opcional. Salida: mapa inmutable. */
    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de TransformationRule. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutable(Map<String, String> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
