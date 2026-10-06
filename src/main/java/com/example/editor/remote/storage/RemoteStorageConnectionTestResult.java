package com.example.editor.remote.storage;

import java.time.Instant;

/** Safe result of a non-destructive remote repository connectivity check. */
public record RemoteStorageConnectionTestResult(
        String profileId,
        RemoteStorageProviderType provider,
        boolean connected,
        boolean credentialsConfigured,
        String message,
        Instant testedAt
) { }
