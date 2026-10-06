package com.example.editor.portability.archive;

/** Fallo controlado al crear, verificar o importar un paquete físico. */
public class CapabilityPackageArchiveException extends RuntimeException {
    public enum Reason { DISABLED, INVALID_PACKAGE, INVALID_ARCHIVE, LIMIT_EXCEEDED, SIGNATURE_INVALID, IO_ERROR }
    private final Reason reason;
    /** Descripción: Inicializa una instancia de CapabilityPackageArchiveException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityPackageArchiveException(Reason reason, String message) { super(message); this.reason = reason; }
    /** Descripción: Inicializa una instancia de CapabilityPackageArchiveException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityPackageArchiveException(Reason reason, String message, Throwable cause) { super(message, cause); this.reason = reason; }
    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de Reason. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() { return reason; }
}
