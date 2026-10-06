package com.example.editor.standalone.publication;

/** Immutable published artifact metadata used by update clients. */
public record PublicationArtifact(String platform, String fileName, String mediaType,
        long size, String sha256, String downloadUrl, String signatureFile) {
}
