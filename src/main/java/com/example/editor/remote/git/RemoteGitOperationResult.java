package com.example.editor.remote.git;

import java.time.Instant;
import java.util.List;

/** Resultado seguro de una operación Git, sin secretos ni argumentos sensibles. */
public record RemoteGitOperationResult(
        RemoteGitOperation operation,
        String profileId,
        boolean success,
        boolean initialized,
        String currentBranch,
        String remoteName,
        String remoteBranch,
        int ahead,
        int behind,
        boolean mergeInProgress,
        List<String> conflictedFiles,
        List<String> changedFiles,
        String message,
        Instant executedAt
) { }
