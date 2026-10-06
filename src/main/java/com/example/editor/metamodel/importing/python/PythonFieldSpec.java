package com.example.editor.metamodel.importing.python;

import java.util.List;

public record PythonFieldSpec(String name, String type, String defaultValue, List<String> decorators, boolean classVariable) {
    public PythonFieldSpec { decorators = decorators == null ? List.of() : List.copyOf(decorators); }
}
