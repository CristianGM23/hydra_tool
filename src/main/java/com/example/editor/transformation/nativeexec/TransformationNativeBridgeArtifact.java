package com.example.editor.transformation.nativeexec;

/** One logical M2T artifact and the workspace-relative file containing its bytes. */
public record TransformationNativeBridgeArtifact(String path, String file) {
    public TransformationNativeBridgeArtifact {
        path = path == null ? "" : path.trim();
        file = file == null ? "" : file.trim();
    }
}
