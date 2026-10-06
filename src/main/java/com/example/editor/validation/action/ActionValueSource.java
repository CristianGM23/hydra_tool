package com.example.editor.validation.action;

import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Valor literal, calculado por una expresión o recibido como parámetro. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActionValueSource(Object literal, String expressionId, String parameterName) {
    public ActionValueSource {
        expressionId = expressionId == null ? "" : expressionId;
        parameterName = parameterName == null ? "" : parameterName;
        if(!expressionId.isBlank()) StableIdentifierPolicy.requireElementId(expressionId, "expressionId");
        if(!parameterName.isBlank()) StableIdentifierPolicy.requireElementId(parameterName, "parameterName");
        int configured = (literal != null ? 1 : 0) + (!expressionId.isBlank() ? 1 : 0) + (!parameterName.isBlank() ? 1 : 0);
        if(configured != 1) throw new IllegalArgumentException("exactly one of literal, expressionId or parameterName must be provided");
    }

    /** Descripción: Inicializa una instancia de ActionValueSource con sus dependencias y datos de entrada. Entradas: literal: dato de entrada necesario para la operación; expressionId: identificador que delimita el recurso objetivo. Salida: deja la instancia inicializada y lista para su uso. */ public ActionValueSource(Object literal, String expressionId) {
        this(literal, expressionId, "");
    }

    /** Descripción: Ejecuta la operación «literal» correspondiente a la responsabilidad de ActionValueSource. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static ActionValueSource literal(Object value) { return new ActionValueSource(value, "", ""); }
    /** Descripción: Ejecuta la operación «expression» correspondiente a la responsabilidad de ActionValueSource. Entradas: expressionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static ActionValueSource expression(String expressionId) { return new ActionValueSource(null, expressionId, ""); }
    /** Descripción: Ejecuta la operación «parameter» correspondiente a la responsabilidad de ActionValueSource. Entradas: parameterName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static ActionValueSource parameter(String parameterName) { return new ActionValueSource(null, "", parameterName); }
}
