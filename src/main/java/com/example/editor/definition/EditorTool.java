package com.example.editor.definition;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
/** Herramienta configurable de una paleta de editor de modelos. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NodeCreationTool.class, name = "CREATE_NODE"),
    @JsonSubTypes.Type(value = EdgeCreationTool.class, name = "CREATE_EDGE"),
    @JsonSubTypes.Type(value = OperationEditorTool.class, name = "INVOKE_OPERATION"),
    @JsonSubTypes.Type(value = ActionEditorTool.class, name = "RUN_ACTION"),
    @JsonSubTypes.Type(value = ValidateEditorTool.class, name = "VALIDATE")
})
public sealed interface EditorTool permits NodeCreationTool, EdgeCreationTool, OperationEditorTool, ActionEditorTool, ValidateEditorTool {
    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de EditorTool. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String id();
    /** Descripción: Ejecuta la operación «label» correspondiente a la responsabilidad de EditorTool. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String label();
    /** Descripción: Ejecuta la operación «description» correspondiente a la responsabilidad de EditorTool. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String description();
    /** Descripción: Ejecuta la operación «icon» correspondiente a la responsabilidad de EditorTool. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String icon();
    /** Descripción: Ejecuta la operación «representationIds» correspondiente a la responsabilidad de EditorTool. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> representationIds();
    /** Descripción: Ejecuta la operación «visibleExpressionId» correspondiente a la responsabilidad de EditorTool. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String visibleExpressionId();
    /** Descripción: Ejecuta la operación «enabledExpressionId» correspondiente a la responsabilidad de EditorTool. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String enabledExpressionId();
}
