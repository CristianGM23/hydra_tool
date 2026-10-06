package com.example.editor.audit;

/** Diferencia semántica neutral. Los textos visibles se generan al consultar/exportar. */
public record AuditChange(String path, AuditChangeKind kind, Object beforeValue, Object afterValue) {
    public AuditChange {
        path = path == null || path.isBlank() ? "$" : path;
        kind = kind == null ? AuditChangeKind.CHANGED : kind;
    }
}
