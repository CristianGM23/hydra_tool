package com.example.editor.definition;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Campo configurable de un formulario generado. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FormFieldDefinition(String id, String featureId, String label, FormFieldWidget widget, boolean required, boolean readOnly,
        String visibleExpressionId, String enabledExpressionId, Object defaultValue, String helpText) {
    public FormFieldDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "formField.id");
        featureId = StableIdentifierPolicy.requireElementId(featureId, "featureId");
        label = label == null || label.isBlank() ? id : label.trim();
        if(widget == null) widget = FormFieldWidget.TEXT;
        visibleExpressionId = optionalId(visibleExpressionId, "visibleExpressionId");
        enabledExpressionId = optionalId(enabledExpressionId, "enabledExpressionId");
        helpText = helpText == null ? "" : helpText.trim();
    }
    /** Descripción: Ejecuta la operación «optionalId» correspondiente a la responsabilidad de FormFieldDefinition. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String optionalId(String value, String field) { return value == null || value.isBlank() ? "" : StableIdentifierPolicy.requireElementId(value, field); }
}
