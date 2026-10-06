package com.example.editor.integration.bridge;
import java.util.List;
/** Resultado neutral de escribir un modelo en el host. */
public record HostWriteResult(String hostDocumentId, String revision, List<String> changedPaths) {
    public HostWriteResult {
        hostDocumentId = hostDocumentId == null ? "" : hostDocumentId.trim();
        revision = revision == null ? "" : revision.trim();
        changedPaths = changedPaths == null ? List.of() : List.copyOf(changedPaths);
    }
}
