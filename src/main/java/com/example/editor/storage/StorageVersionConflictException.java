package com.example.editor.storage;

import java.io.IOException;

/** Conflicto de versión optimista en un proveedor de almacenamiento. */
public class StorageVersionConflictException extends IOException {
    /** Descripción: Inicializa una instancia de StorageVersionConflictException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public StorageVersionConflictException(String message) { super(message); }
}
