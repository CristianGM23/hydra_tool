package com.example.editor.definition.runtime.session.tool;

import java.util.List;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.runtime.ModelValidationReport;

/** Resultado interno de una herramienta neutral antes de persistir la sesión. */
public record EditorSessionToolOutcome(CanonicalModelDocument document, boolean changed, Object returnValue,
        List<String> messages, ModelValidationReport validationReport) {
    public EditorSessionToolOutcome {
        if(document == null) throw new IllegalArgumentException("document must not be null");
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
    /** Descripción: Ejecuta la operación «unchanged» correspondiente a la responsabilidad de EditorSessionToolOutcome. Entradas: document: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse; messages: dato de entrada necesario para la operación; report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static EditorSessionToolOutcome unchanged(CanonicalModelDocument document, Object value,
            List<String> messages, ModelValidationReport report) {
        return new EditorSessionToolOutcome(document, false, value, messages, report);
    }
}
