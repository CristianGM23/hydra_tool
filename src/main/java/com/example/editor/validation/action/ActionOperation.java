package com.example.editor.validation.action;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Operación base de una acción declarativa. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SetAttributeOperation.class, name = "SET_ATTRIBUTE"),
    @JsonSubTypes.Type(value = ClearAttributeOperation.class, name = "CLEAR_ATTRIBUTE"),
    @JsonSubTypes.Type(value = CopyAttributeOperation.class, name = "COPY_ATTRIBUTE"),
    @JsonSubTypes.Type(value = AddRelationOperation.class, name = "ADD_RELATION"),
    @JsonSubTypes.Type(value = RemoveRelationOperation.class, name = "REMOVE_RELATION"),
    @JsonSubTypes.Type(value = CreateElementOperation.class, name = "CREATE_ELEMENT"),
    @JsonSubTypes.Type(value = DeleteElementOperation.class, name = "DELETE_ELEMENT"),
    @JsonSubTypes.Type(value = TransformTextOperation.class, name = "TRANSFORM_TEXT"),
    @JsonSubTypes.Type(value = ExecuteActionOperation.class, name = "EXECUTE_ACTION"),
    @JsonSubTypes.Type(value = InvokeOperationOperation.class, name = "INVOKE_OPERATION")
})
public sealed interface ActionOperation permits SetAttributeOperation, ClearAttributeOperation, CopyAttributeOperation, AddRelationOperation, RemoveRelationOperation, CreateElementOperation, DeleteElementOperation, TransformTextOperation, ExecuteActionOperation, InvokeOperationOperation {
    /** Descripción: Ejecuta la operación «operationId» correspondiente a la responsabilidad de ActionOperation. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String operationId();
}
