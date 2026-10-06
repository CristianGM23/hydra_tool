package com.example.editor.integration.injection;

import java.util.List;
import com.example.editor.integration.InjectionPlan;

/** Previsualización que conserva también los archivos sin cambios y los conflictos de código manual. */
public record TechnologyInjectionPreview(InjectionPlan plan, List<String> unchangedFiles,
        List<String> manualConflictFiles, long generatedBytes) {
    public TechnologyInjectionPreview {
        if(plan == null){throw new IllegalArgumentException("plan is required");}
        unchangedFiles = unchangedFiles == null ? List.of() : List.copyOf(unchangedFiles);
        manualConflictFiles = manualConflictFiles == null ? List.of() : List.copyOf(manualConflictFiles);
    }
}
