package com.example.editor.recovery;
/** Corruption/interruption simulation settings. */
public record HydraRecoveryDrillRequest(String backupId,boolean simulateCorruption,boolean restoreToCleanRoot) { }
