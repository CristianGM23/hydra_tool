package com.example.editor.integration.compiler;

import java.util.List;

/** Resultado de volver a importar y verificar un ZIP tecnológico generado por HYDRA. */
public record TechnologyCompilationArchiveImportResult(TechnologyCompilationResult compilation,
        TechnologyCompilationArchiveManifest manifest, List<String> verifiedPaths) {
    public TechnologyCompilationArchiveImportResult {
        if(compilation == null || manifest == null){throw new IllegalArgumentException("compilation and manifest are required");}
        verifiedPaths = verifiedPaths == null ? List.of() : List.copyOf(verifiedPaths);
    }
}
