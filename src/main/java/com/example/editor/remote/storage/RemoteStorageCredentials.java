package com.example.editor.remote.storage;

/** Credenciales resueltas sin exponer su origen al proveedor ni a la API web. */
public record RemoteStorageCredentials(String token) {
    public RemoteStorageCredentials { token=token==null?"":token.trim(); }
    /** Descripción: Ejecuta la operación «configured» correspondiente a la responsabilidad de RemoteStorageCredentials. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean configured(){return !token.isBlank();}
}
