package com.example.editor.portability.transaction;

import java.util.List;
import com.example.editor.project.ArtifactKind;

/** One ordered document operation inside a portable transaction. */
public record CapabilityLayerTransactionItem(String documentPath, ArtifactKind artifactKind,
        String targetPath, String action, int order, String currentEtag, String expectedEtag,
        String proposedEtag, boolean applicable, List<String> diagnostics) {
    public CapabilityLayerTransactionItem {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
