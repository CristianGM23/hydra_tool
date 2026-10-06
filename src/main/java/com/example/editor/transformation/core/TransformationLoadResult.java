package com.example.editor.transformation.core;

public record TransformationLoadResult(
        TransformationDefinitionDocument transformation,
        String etag,
        TransformationCheckReport checkReport) {
}
