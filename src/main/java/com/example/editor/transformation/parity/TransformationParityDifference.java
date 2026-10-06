package com.example.editor.transformation.parity;

/** Diferencia semántica acotada detectada durante una comprobación de paridad. */
public record TransformationParityDifference(String path, String expected, String actual) {
    public TransformationParityDifference {
        path = path == null ? "" : path;
        expected = expected == null ? "" : expected;
        actual = actual == null ? "" : actual;
    }
}
