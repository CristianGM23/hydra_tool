package com.example.editor.transformation.testing;

/** Artefacto textual esperado por un caso de prueba M2T. */
public record TransformationExpectedArtifact(String path, String content) {
    public TransformationExpectedArtifact {
        path = path == null ? "" : path.trim();
        content = content == null ? "" : content;
    }
}
