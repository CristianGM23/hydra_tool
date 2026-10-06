package com.example.editor.artifact.transaction;

/** Estado persistido de una transacción de artefactos. */
public enum HydraArtifactTransactionStatus { PREPARED, RUNNING, RECOVERY_REQUIRED, COMPLETED, ABORTED }
