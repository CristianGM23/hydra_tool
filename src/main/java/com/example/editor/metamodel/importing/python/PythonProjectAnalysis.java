package com.example.editor.metamodel.importing.python;

import java.util.List;

public record PythonProjectAnalysis(List<PythonTypeSpec> types, List<String> modules, boolean fallbackUsed, List<String> diagnostics) {
    public PythonProjectAnalysis {
        types = types == null ? List.of() : List.copyOf(types);
        modules = modules == null ? List.of() : List.copyOf(modules);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
