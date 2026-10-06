package com.example.editor.project;

import java.time.Instant;

/** Entrada ligera del índice de proyecto. */
public record ProjectIndexedArtifact(String relativePath,ArtifactKind artifactKind,FileOwnership ownership,long size,Instant lastModified,String etag,boolean editable) { }
