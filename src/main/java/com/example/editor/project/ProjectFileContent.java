package com.example.editor.project;

import java.time.Instant;

/** Contenido y metadatos de un archivo del proyecto. */
public record ProjectFileContent(String relativePath,String name,ArtifactKind artifactKind,FileOwnership ownership,
        String mediaType,String charset,boolean binary,String content,String etag,long size,Instant lastModified,boolean editable) {
    public ProjectFileContent {
        content = content == null ? "" : content;
        charset = charset == null ? "UTF-8" : charset;
        mediaType = mediaType == null ? "application/octet-stream" : mediaType;
        etag = etag == null ? "" : etag;
    }
}
