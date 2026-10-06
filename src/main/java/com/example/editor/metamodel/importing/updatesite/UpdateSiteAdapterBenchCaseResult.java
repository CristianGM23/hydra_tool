package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Assertion result for one adapter fixture. */
public record UpdateSiteAdapterBenchCaseResult(String name, boolean expectedMatch,
        boolean actualMatch, boolean passed, String resolvedId, String implementation,
        List<String> explanations, List<String> errors) {
    public UpdateSiteAdapterBenchCaseResult {
        explanations = explanations == null ? List.of() : List.copyOf(explanations);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
