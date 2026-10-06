package com.example.editor.transformation.export;

/** Archivo generado en memoria por un adaptador de transformación. */
public record TransformationExportArtifact(String path,String content) {
    public TransformationExportArtifact { path=path==null?"":path.trim(); content=content==null?"":content; }
}
