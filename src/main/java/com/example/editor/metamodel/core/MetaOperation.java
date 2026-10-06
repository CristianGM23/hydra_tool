package com.example.editor.metamodel.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Describe la firma neutral de una operación declarada por una clase o interfaz. */
public record MetaOperation(String id, String name, MetaTypeReference returnType, Multiplicity returnMultiplicity, boolean ordered, boolean unique, List<MetaParameter> parameters, List<MetaTypeReference> exceptionTypes, MetaOperationBehaviorKind behaviorKind, Map<String, String> annotations) {
    public MetaOperation {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        if(returnType == null) {
            returnMultiplicity = null;
        } else if(returnMultiplicity == null) {
            returnMultiplicity = Multiplicity.one();
        }
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        exceptionTypes = exceptionTypes == null ? List.of() : List.copyOf(exceptionTypes);
        behaviorKind = behaviorKind == null ? MetaOperationBehaviorKind.UNSPECIFIED : behaviorKind;
        if(annotations == null || annotations.isEmpty()) {
            annotations = Map.of();
        } else {
            annotations = Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
        }
    }

    /** Indica si la operación no declara valor de retorno. */
    /** Descripción: Ejecuta la operación «returnsVoid» correspondiente a la responsabilidad de MetaOperation. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean returnsVoid() {
        return returnType == null;
    }
}
