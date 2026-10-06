package com.example.editor.project.recovery;

import java.util.List;

/** Recurso inventariado. Ningún fichero válido desaparece silenciosamente. */
public record ExternalProjectResource(String path, ExternalProjectResourceKind kind, long size,
        String sha256, List<String> technologies, String purpose) {
    public ExternalProjectResource {
        path = path == null ? "" : path;
        kind = kind == null ? ExternalProjectResourceKind.PROJECT_RESOURCE : kind;
        sha256 = sha256 == null ? "" : sha256;
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
        purpose = purpose == null ? "" : purpose;
    }
}
