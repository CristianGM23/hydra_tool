package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Result of consuming the generated mirror with HYDRA's bounded p2 test client. */
public record UpdateSiteP2MirrorValidation(boolean valid, int declaredUnits, int declaredArtifacts,
        int verifiedArtifacts, List<String> missingArtifacts, List<String> diagnostics) {
    public UpdateSiteP2MirrorValidation {
        missingArtifacts = missingArtifacts == null ? List.of() : List.copyOf(missingArtifacts);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
