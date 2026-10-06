package com.example.editor.standalone.publication;

/** Result of atomically publishing a release and updating its channel pointer. */
public record PublicationResult(PublicationManifest manifest, String manifestPath,
        String channelManifestPath, PublicationValidationReport validation) {
}
