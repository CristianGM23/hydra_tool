package com.example.editor.recovery;
/** One file included in an AZ backup manifest. */
public record HydraRecoveryFile(String path,long bytes,String sha256) { }
