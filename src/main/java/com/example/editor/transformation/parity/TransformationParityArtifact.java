package com.example.editor.transformation.parity;

/** Evidencia textual producida por un runtime M2T externo. */
public record TransformationParityArtifact(String path, String content) {
    public TransformationParityArtifact {
        path = path == null ? "" : path;
        content = content == null ? "" : content;
    }
}
