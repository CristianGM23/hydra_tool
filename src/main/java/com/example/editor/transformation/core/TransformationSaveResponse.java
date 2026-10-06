package com.example.editor.transformation.core;

public record TransformationSaveResponse(
        TransformationDefinitionDocument transformation,
        TransformationCheckReport checkReport,
        String etag) {
}
