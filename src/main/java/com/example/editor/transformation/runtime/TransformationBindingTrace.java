package com.example.editor.transformation.runtime;

import com.example.editor.transformation.core.TransformationBindingKind;

/** Evidencia opcional de qué binding pobló una propiedad o relación destino. */
public record TransformationBindingTrace(
        String ruleId,
        String bindingId,
        TransformationBindingKind kind,
        String sourceElementId,
        String targetElementId,
        String sourceFeatureId,
        String targetFeatureId) {
}
