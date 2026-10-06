package com.example.editor.remote.storage;

/** Política de escritura para evitar sobrescrituras remotas accidentales. */
public enum RemoteWriteMode { CREATE_ONLY,UPDATE_ONLY,UPSERT }
