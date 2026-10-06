package com.example.editor.event.flow.runtime;

import java.time.Instant;

public record EventFlowRuntimeStatus(String metamodelId, boolean active, int flowCount, long processedEvents,
        long emittedEvents, int stateEntries, String lastFailure, Instant updatedAt) { }
