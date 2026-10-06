package com.example.editor.remote.storage;

/**
 * Contenido remoto acompañado de su versión. El array es un buffer interno de
 * transferencia de vida corta; no se cachea ni se expone como estado mutable.
 * Evitar copias defensivas aquí impide multiplicar el pico de heap en archivos grandes.
 */
public record RemoteArtifact(RemoteArtifactDescriptor descriptor,byte[] content) {
    public RemoteArtifact { content=content==null?new byte[0]:content; }
    /** Descripción: Ejecuta la operación «contentLength» correspondiente a la responsabilidad de RemoteArtifact. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int contentLength() { return content.length; }
}
