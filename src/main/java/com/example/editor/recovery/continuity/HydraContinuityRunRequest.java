package com.example.editor.recovery.continuity;

/** Manual execution of one local continuity plan. */
public record HydraContinuityRunRequest(String planId, boolean createFreshBackup) { }
