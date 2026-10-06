package com.example.editor.registry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteMirrorChannel;

/** Federable local catalog entry backed by AV provenance. */
public record HydraRegistryEntry(String entryId, String reportId, String productId, String version,
        String metamodelId, UpdateSiteMirrorChannel channel, List<String> capabilities,
        String attestationSha256, String catalogSignatureSha256, Instant registeredAt,
        Map<String, String> annotations) {
    public HydraRegistryEntry {
        capabilities = capabilities == null ? List.of() : capabilities.stream().filter(value -> value != null && !value.isBlank()).distinct().sorted().toList();
        registeredAt = registeredAt == null ? Instant.now() : registeredAt;
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
