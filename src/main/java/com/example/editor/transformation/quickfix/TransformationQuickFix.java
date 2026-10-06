package com.example.editor.transformation.quickfix;

import com.example.editor.transformation.core.TransformationDefinitionDocument;

/** Propuesta accionable generada a partir de un diagnóstico de transformación. */
public record TransformationQuickFix(
        String id,
        String diagnosticCode,
        String ruleId,
        String bindingId,
        String title,
        String description,
        boolean safeAutomatic,
        TransformationDefinitionDocument proposedTransformation) {
}
