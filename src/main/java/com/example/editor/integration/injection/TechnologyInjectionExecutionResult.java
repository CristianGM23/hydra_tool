package com.example.editor.integration.injection;

import java.util.List;
import com.example.editor.integration.InjectionManifest;

/** Resultado confirmado de aplicar o revertir una integración tecnológica. */
public record TechnologyInjectionExecutionResult(InjectionManifest manifest, List<String> writtenFiles,
        List<String> deletedFiles, List<String> restoredFiles) {
    public TechnologyInjectionExecutionResult {
        if(manifest == null){throw new IllegalArgumentException("manifest is required");}
        writtenFiles = writtenFiles == null ? List.of() : List.copyOf(writtenFiles);
        deletedFiles = deletedFiles == null ? List.of() : List.copyOf(deletedFiles);
        restoredFiles = restoredFiles == null ? List.of() : List.copyOf(restoredFiles);
    }
}
