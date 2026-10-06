package com.example.editor.remote.storage;

/** Vista segura de un perfil remoto editable desde HYDRA. */
public record RemoteStorageProfileConfiguration(
        String id,
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
        long maximumArtifactBytes,
        boolean managedOverride
) { }
