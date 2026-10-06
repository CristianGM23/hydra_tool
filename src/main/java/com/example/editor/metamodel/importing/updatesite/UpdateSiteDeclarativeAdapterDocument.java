package com.example.editor.metamodel.importing.updatesite;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Documento portable *.hydra-adapter.json validado contra el contrato actual. */
public record UpdateSiteDeclarativeAdapterDocument(String schemaVersion,
        UpdateSiteExtensionAdapterDescriptor adapter, Map<String, String> annotations) {
    public UpdateSiteDeclarativeAdapterDocument {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion.trim();
        annotations = annotations == null || annotations.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
}
