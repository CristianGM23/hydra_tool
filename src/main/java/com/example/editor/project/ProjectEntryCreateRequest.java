package com.example.editor.project;

/** Petición para crear un archivo o directorio. */
public record ProjectEntryCreateRequest(String relativePath,boolean directory,String content) { }
