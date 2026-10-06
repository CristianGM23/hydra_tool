package com.example.editor.project;

/** Ruta local resuelta para copiarla o mostrarla en el explorador del sistema. */
public record ProjectResolvedPath(String relativePath,String absolutePath,boolean directory,boolean exists) { }
