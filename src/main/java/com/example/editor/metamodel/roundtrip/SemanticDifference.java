package com.example.editor.metamodel.roundtrip;

/** Diferencia estable entre dos proyecciones semánticas del mismo metamodelo. */
public record SemanticDifference(String path, String code, String expected, String actual, boolean lossy) {
    public SemanticDifference {
        path = path == null ? "" : path;
        code = code == null ? "" : code;
        expected = expected == null ? "" : expected;
        actual = actual == null ? "" : actual;
    }
}
