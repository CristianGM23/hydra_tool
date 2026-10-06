package com.example.editor.transformation.web;

/** Texto de entrada para una ejecución T2M persistida. */
public record TransformationTextRequest(String text) {
    public TransformationTextRequest { text=text==null?"":text; }
}
