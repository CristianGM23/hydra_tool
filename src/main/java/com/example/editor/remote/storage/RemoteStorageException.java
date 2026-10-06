package com.example.editor.remote.storage;

/** Error estable del subsistema de almacenamiento remoto. */
public class RemoteStorageException extends RuntimeException {
    public enum Reason { DISABLED,INVALID_REQUEST,PROFILE_NOT_FOUND,CREDENTIALS_MISSING,NOT_FOUND,CONFLICT,PRECONDITION_FAILED,FILE_TOO_LARGE,CAPACITY_EXCEEDED,REMOTE_ERROR }
    private final Reason reason;
    /** Descripción: Inicializa una instancia de RemoteStorageException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteStorageException(Reason reason,String message) { super(message); this.reason=reason; }
    /** Descripción: Inicializa una instancia de RemoteStorageException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteStorageException(Reason reason,String message,Throwable cause) { super(message,cause); this.reason=reason; }
    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de RemoteStorageException. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() { return reason; }
}
