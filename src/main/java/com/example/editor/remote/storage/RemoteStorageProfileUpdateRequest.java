package com.example.editor.remote.storage;

/** Configuración editable y segura de un repositorio remoto; solo transporta coordenadas no sensibles. */
public record RemoteStorageProfileUpdateRequest(
        String displayName,
        RemoteStorageProviderType provider,
        boolean enabled,
        String baseUrl,
        String owner,
        String repository,
        String branch,
        String projectId,
        String driveId,
        boolean oneDriveAppFolder,
        String repositoryUrl,
        String rootItemId,
        String gitUsername,
        String rootPath,
        String apiVersion,
        long maximumArtifactBytes
) { }
