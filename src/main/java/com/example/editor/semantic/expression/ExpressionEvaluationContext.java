package com.example.editor.semantic.expression;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;

/** Contexto neutral utilizado por el evaluador. */
public record ExpressionEvaluationContext(MetaModel metamodel, CanonicalModelDocument model, String selfElementId, Map<String, Object> variables) {
    public ExpressionEvaluationContext {
        if(metamodel == null) throw new IllegalArgumentException("metamodel must not be null");
        if(model == null) throw new IllegalArgumentException("model must not be null");
        selfElementId = selfElementId == null ? "" : selfElementId;
        variables = immutableVariables(variables);
    }

    /** Conserva variables opcionales con valor null sin permitir que el mapa mutable del llamador escape al contexto. */
    /** Descripción: Ejecuta la operación «immutableVariables» correspondiente a la responsabilidad de ExpressionEvaluationContext. Entradas: variables: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, Object> immutableVariables(Map<String, Object> variables) {
        if(variables == null || variables.isEmpty()) return Map.of();
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for(var entry : variables.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "variable name must not be null"), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Descripción: Ejecuta la operación «self» correspondiente a la responsabilidad de ExpressionEvaluationContext. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public CanonicalModelElement self() {
        if(selfElementId.isBlank()) return null;
        return model.findElement(selfElementId).orElseThrow(() -> new ExpressionEvaluationException("Self element was not found: " + selfElementId));
    }

    /** Devuelve el valor actual de evaluación. En una colección puede ser un escalar u objeto. */
    /** Descripción: Ejecuta la operación «current» correspondiente a la responsabilidad de ExpressionEvaluationContext. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Object current() {
        return variables.containsKey("_current") ? variables.get("_current") : self();
    }

    /** Descripción: Ejecuta la operación «withCurrent» correspondiente a la responsabilidad de ExpressionEvaluationContext. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionEvaluationContext withCurrent(Object value) {
        Map<String, Object> next = new java.util.LinkedHashMap<>(variables);
        next.put("_current", value);
        String nextSelf = value instanceof CanonicalModelElement element ? element.id() : selfElementId;
        return new ExpressionEvaluationContext(metamodel, model, nextSelf, next);
    }

    /** Descripción: Ejecuta la operación «withSelf» correspondiente a la responsabilidad de ExpressionEvaluationContext. Entradas: element: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionEvaluationContext withSelf(CanonicalModelElement element) {
        return withCurrent(element);
    }

    /** Descripción: Ejecuta la operación «variable» correspondiente a la responsabilidad de ExpressionEvaluationContext. Entradas: name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Object variable(String name) {
        if(name == null || name.isBlank()) throw new ExpressionEvaluationException("Variable name must not be blank");
        if(!variables.containsKey(name)) throw new ExpressionEvaluationException("Variable was not provided: " + name);
        return variables.get(name);
    }

    /** Descripción: Ejecuta la operación «withVariables» correspondiente a la responsabilidad de ExpressionEvaluationContext. Entradas: additionalVariables: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionEvaluationContext withVariables(Map<String, Object> additionalVariables) {
        Map<String, Object> next = new java.util.LinkedHashMap<>(variables);
        if(additionalVariables != null) next.putAll(additionalVariables);
        return new ExpressionEvaluationContext(metamodel, model, selfElementId, next);
    }
}
