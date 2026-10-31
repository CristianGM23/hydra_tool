package com.example.editor.metamodel.canonical;

/**
 * Representa un fallo del repositorio canónico sin acoplar la persistencia a HTTP.
 */
public class CanonicalMetamodelRepositoryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    /** Descripción: Inicializa una instancia de CanonicalMetamodelRepositoryException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalMetamodelRepositoryException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** Descripción: Inicializa una instancia de CanonicalMetamodelRepositoryException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalMetamodelRepositoryException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de Reason. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() {
        return reason;
    }

    /** Descripción: Mensaje público de un metamodelo almacenado corrupto, sin rutas ni detalles internos (D-MM-CORRUPT-CONTRATO). Entradas: metamodelId: id del metamodelo. Salida: texto para el cliente. */
    public static String corruptPublicMessage(String metamodelId) {
        return "The stored canonical metamodel '" + metamodelId + "' cannot be used: its stored representation is not a valid canonical metamodel. "
                + "The stored files were left unchanged; restore the metamodel from a backup or from the project history. The details were recorded in the server log.";
    }

    public enum Reason {
        INVALID_REQUEST,
        NOT_FOUND,
        CONFLICT,
        PRECONDITION_FAILED,
        STORAGE_ERROR,
        /** The stored document exists but is not a valid canonical metamodel: a resource state, not a failure of the storage. */
        CORRUPT_CONFIGURATION
    }
}
