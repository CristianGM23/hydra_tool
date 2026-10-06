package com.example.editor.definition.runtime.session.tool;

import java.util.List;
import com.example.editor.definition.runtime.session.EditorSessionView;
import com.example.editor.validation.runtime.ModelValidationReport;

/** Respuesta pública que combina la vista actualizada y el resultado funcional de la herramienta. */
public record EditorSessionToolExecutionResponse(EditorSessionView view, Object returnValue,
        List<String> messages, ModelValidationReport validationReport) {
    public EditorSessionToolExecutionResponse {
        if(view == null) throw new IllegalArgumentException("view must not be null");
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
