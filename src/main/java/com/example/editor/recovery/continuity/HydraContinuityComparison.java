package com.example.editor.recovery.continuity;

/** Difference between two retained recovery exercises. */
public record HydraContinuityComparison(String baselineRunId, String candidateRunId,
        long rpoDeltaMinutes, long rtoDeltaMillis, boolean improved,
        String summary) { }
