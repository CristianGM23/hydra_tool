package com.example.editor.metamodel.importing.python;

import java.util.List;

public record PythonTypeSpec(String name, String qualifiedName, String module, String sourcePath, List<String> bases,
        List<String> decorators, List<PythonFieldSpec> fields, List<PythonMethodSpec> methods, List<String> enumLiterals) {
    public PythonTypeSpec {
        bases = bases == null ? List.of() : List.copyOf(bases);
        decorators = decorators == null ? List.of() : List.copyOf(decorators);
        fields = fields == null ? List.of() : List.copyOf(fields);
        methods = methods == null ? List.of() : List.copyOf(methods);
        enumLiterals = enumLiterals == null ? List.of() : List.copyOf(enumLiterals);
    }
}
