package com.example.editor.transformation.web;

import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.runtime.TransformationSynchronizationRequest;

/** Preview de sincronización que mantiene definición y modelos exclusivamente en memoria. */
public record TransformationDraftSynchronizationRequest(
        TransformationDefinitionDocument transformation,
        TransformationSynchronizationRequest synchronization) {
}
