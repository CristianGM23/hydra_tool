package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Explainable result of matching a declarative adapter against a fixture. */
public record UpdateSiteAdapterFixtureResult(boolean validAdapter, boolean matched,
        String resolvedId, String implementation, String neutralTarget,
        List<String> explanations, List<String> errors) {
    public UpdateSiteAdapterFixtureResult {
        explanations = explanations == null ? List.of() : List.copyOf(explanations);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
