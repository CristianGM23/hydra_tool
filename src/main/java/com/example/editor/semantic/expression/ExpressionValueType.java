package com.example.editor.semantic.expression;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Describe el tipo estático inferido o declarado de una expresión. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExpressionValueType(ExpressionValueKind kind, String classId, String enumId, ExpressionValueType elementType) {

    public ExpressionValueType {
        if(kind == null) kind = ExpressionValueKind.ANY;
        classId = classId == null ? "" : classId;
        enumId = enumId == null ? "" : enumId;
        if(kind == ExpressionValueKind.COLLECTION && elementType == null) {
            elementType = scalar(ExpressionValueKind.ANY);
        }
    }

    /** Descripción: Ejecuta la operación «scalar» correspondiente a la responsabilidad de ExpressionValueType. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static ExpressionValueType scalar(ExpressionValueKind kind) {
        return new ExpressionValueType(kind, "", "", null);
    }

    /** Descripción: Ejecuta la operación «object» correspondiente a la responsabilidad de ExpressionValueType. Entradas: classId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static ExpressionValueType object(String classId) {
        return new ExpressionValueType(ExpressionValueKind.OBJECT, classId, "", null);
    }

    /** Descripción: Ejecuta la operación «enumeration» correspondiente a la responsabilidad de ExpressionValueType. Entradas: enumId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static ExpressionValueType enumeration(String enumId) {
        return new ExpressionValueType(ExpressionValueKind.ENUM, "", enumId, null);
    }

    /** Descripción: Ejecuta la operación «collection» correspondiente a la responsabilidad de ExpressionValueType. Entradas: elementType: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static ExpressionValueType collection(ExpressionValueType elementType) {
        return new ExpressionValueType(ExpressionValueKind.COLLECTION, "", "", elementType == null ? scalar(ExpressionValueKind.ANY) : elementType);
    }

    @JsonIgnore
    /** Descripción: Evalúa la condición «isNumeric» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isNumeric() {
        return kind == ExpressionValueKind.INTEGER || kind == ExpressionValueKind.DECIMAL;
    }

    @JsonIgnore
    /** Descripción: Evalúa la condición «isCollection» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isCollection() {
        return kind == ExpressionValueKind.COLLECTION;
    }
}
