package com.example.editor.metamodel.importing.python;

import java.util.List;

public record PythonMethodSpec(String name, String returnType, List<PythonParameterSpec> parameters, List<String> decorators,
        boolean asynchronous, boolean hasBody) {
    public PythonMethodSpec {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        decorators = decorators == null ? List.of() : List.copyOf(decorators);
    }
}
