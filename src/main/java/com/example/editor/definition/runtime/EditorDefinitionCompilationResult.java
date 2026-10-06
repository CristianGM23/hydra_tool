package com.example.editor.definition.runtime;

import com.example.editor.definition.EditorDefinitionCheckReport;

/** Resultado trazable de compilar una definición de editor. */
public record EditorDefinitionCompilationResult(ExecutableEditorPlan plan,
        EditorDefinitionCheckReport checkReport, boolean cacheHit) {
}
