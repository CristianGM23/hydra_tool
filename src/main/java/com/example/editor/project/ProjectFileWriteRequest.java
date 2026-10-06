package com.example.editor.project;

/** Petición de escritura optimista de un archivo textual o binario Base64. */
public record ProjectFileWriteRequest(String content,String expectedEtag,boolean binary) { }
