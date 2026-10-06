package com.example.editor.security.process;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Parámetros neutrales y acotados para lanzar un proceso externo sin shell. */
public record HydraExternalProcessRunRequest(
        Path workingDirectory,
        List<String> command,
        int timeoutSeconds,
        Map<String, String> environment,
        int maximumOutputBytes,
        int outputJoinTimeoutSeconds,
        long terminationGraceMillis,
        long terminationForceWaitMillis,
        int terminationMaximumPasses,
        long treeObservationIntervalMillis) {

    public HydraExternalProcessRunRequest {
        command = command == null ? List.of() : List.copyOf(command);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        timeoutSeconds = Math.max(1, timeoutSeconds);
        maximumOutputBytes = Math.max(1, maximumOutputBytes);
        outputJoinTimeoutSeconds = Math.max(1, outputJoinTimeoutSeconds);
        terminationGraceMillis = Math.max(0L, terminationGraceMillis);
        terminationForceWaitMillis = Math.max(0L, terminationForceWaitMillis);
        terminationMaximumPasses = Math.max(1, terminationMaximumPasses);
        treeObservationIntervalMillis = Math.max(1L, treeObservationIntervalMillis);
    }
}
