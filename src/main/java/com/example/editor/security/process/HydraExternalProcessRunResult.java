package com.example.editor.security.process;

import java.util.List;

/** Resultado neutral de un proceso externo, con salida y diagnósticos acotados. */
public record HydraExternalProcessRunResult(
        HydraExternalProcessRunStatus status,
        Integer exitCode,
        long durationMillis,
        String stdout,
        String stderr,
        List<String> diagnostics) {

    public HydraExternalProcessRunResult {
        if (status == null) throw new IllegalArgumentException("status is required");
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        durationMillis = Math.max(0L, durationMillis);
    }
}
