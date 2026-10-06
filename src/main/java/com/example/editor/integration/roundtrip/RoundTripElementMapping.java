package com.example.editor.integration.roundtrip;

import java.util.List;
import java.util.Map;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.InjectionFileOwnership;

/** Correspondencia persistente entre documentos HYDRA y un archivo nativo generado. */
public record RoundTripElementMapping(String canonicalDocumentId, IntegrationArtifactKind artifactKind,
        String nativePath, List<String> sourceElementIds, String generatedSha256,
        InjectionFileOwnership ownership, Map<String, String> metadata) {
    public RoundTripElementMapping {
        if(canonicalDocumentId == null || canonicalDocumentId.isBlank()){throw new IllegalArgumentException("canonicalDocumentId is required");}
        if(artifactKind == null){throw new IllegalArgumentException("artifactKind is required");}
        if(nativePath == null || nativePath.isBlank()){throw new IllegalArgumentException("nativePath is required");}
        canonicalDocumentId = canonicalDocumentId.trim(); nativePath = nativePath.replace('\\', '/').trim();
        sourceElementIds = sourceElementIds == null ? List.of() : List.copyOf(sourceElementIds);
        generatedSha256 = generatedSha256 == null ? "" : generatedSha256.trim();
        ownership = ownership == null ? InjectionFileOwnership.GENERATED : ownership;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
