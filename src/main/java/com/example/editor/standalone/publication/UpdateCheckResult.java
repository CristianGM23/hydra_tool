package com.example.editor.standalone.publication;

/** Resolved update information for one product, channel and platform. */
public record UpdateCheckResult(boolean updateAvailable, String currentVersion,
        String availableVersion, PublicationArtifact artifact, String message) {
}
