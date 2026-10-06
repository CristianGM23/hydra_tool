package com.example.editor.standalone.distribution;

import java.util.List;
import java.util.Map;

/** Complete validation report; valid is true only when no ERROR diagnostic exists. */
public record StandaloneDistributionValidationReport(boolean valid,
        List<StandaloneDistributionDiagnostic> diagnostics, Map<String, String> metadata) {
    public StandaloneDistributionValidationReport {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        boolean expected = diagnostics.stream().noneMatch(item -> item.severity() == StandaloneDistributionDiagnosticSeverity.ERROR);
        if(valid != expected){throw new IllegalArgumentException("valid must reflect ERROR diagnostics");}
    }
}
