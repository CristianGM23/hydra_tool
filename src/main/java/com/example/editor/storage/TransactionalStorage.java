package com.example.editor.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Contrato neutral para documentos pequeños/medianos que requieren lectura
 * acotada, versión optimista y sustitución atómica cuando el proveedor la admita.
 */
public interface TransactionalStorage {
    /** Descripción: Obtiene la información correspondiente a «read» dentro de TransactionalStorage. Entradas: relativePath: ruta o ubicación utilizada por la operación; maxBytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StorageDocument> read(Path relativePath, long maxBytes) throws IOException;
    /** Descripción: Actualiza el estado gestionado por TransactionalStorage mediante la operación «writeAtomically». Entradas: relativePath: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse; expectedVersion: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StorageDocument writeAtomically(Path relativePath, byte[] content, String expectedVersion) throws IOException;
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: relativePath: ruta o ubicación utilizada por la operación; expectedVersion: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean delete(Path relativePath, String expectedVersion) throws IOException;
    /** Descripción: Evalúa la condición «exists» sin modificar el estado externo. Entradas: relativePath: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean exists(Path relativePath);
}
