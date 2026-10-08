package com.example.editor.metamodel.roundtrip;

/** Incidencia de representabilidad que conserva estructura pero puede requerir configuración nativa externa. */
public record TextualSemanticIssue(String code, String path, String message, boolean blocksNativeRuntime) {
    public TextualSemanticIssue {
        code = code == null ? "" : code;
        path = path == null ? "" : path;
        message = message == null ? "" : message;
    }
}
