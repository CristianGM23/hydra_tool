package com.example.editor.artifact.transaction;

/** Operaciones multi-artefacto que pueden necesitar recuperación tras un fallo. */
public enum HydraArtifactTransactionOperation { CREATE, UPDATE, DELETE, RENAME, MOVE, IMPORT, MIGRATE, SCAN }
