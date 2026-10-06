package com.example.editor.remote.storage;

/** Safe, non-secret coordinates derived from one user-facing remote URL. */
public record RemoteStorageLocationResolution(
        String url,
        RemoteStorageProviderType provider,
        String owner,
        String repository,
        String projectId,
        String branch,
        String rootPath,
        String rootItemId,
        String summary
) { }
