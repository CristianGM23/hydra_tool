package com.example.editor.validation.persistence;

/** Error estable de la persistencia JSON compartida por validaciones y acciones. */
public class CanonicalDocumentStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public enum Reason { INVALID_REQUEST, NOT_FOUND, CONFLICT, PRECONDITION_FAILED, STORAGE_ERROR }
    private final Reason reason;
    private final boolean malformed;
    /** Descripción: Inicializa una instancia de CanonicalDocumentStoreException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalDocumentStoreException(Reason reason, String message) { this(reason, message, null, false); }
    /** Descripción: Inicializa una instancia de CanonicalDocumentStoreException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalDocumentStoreException(Reason reason, String message, Throwable cause) { this(reason, message, cause, false); }
    /** Descripción: Inicializa el error indicando si el documento existe pero sus bytes no forman el documento esperado. Entradas: reason, message y cause como en los demás constructores; malformed: true cuando el contenido almacenado está corrupto (no es un fallo de E/S). Salida: deja la instancia inicializada. */ public CanonicalDocumentStoreException(Reason reason, String message, Throwable cause, boolean malformed) { super(message, cause); this.reason = reason; this.malformed = malformed; }
    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de Reason. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() { return reason; }
    /** Descripción: Indica si el documento almacenado existe pero está corrupto (JSON no analizable o identificador distinto del nombre del fichero), a diferencia de un fallo de lectura o de permisos. Entradas: ninguna. Salida: true para contenido corrupto. */ public boolean isMalformed() { return malformed; }
}
