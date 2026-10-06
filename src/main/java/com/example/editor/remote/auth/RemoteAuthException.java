package com.example.editor.remote.auth;

/** Error estable del subsistema de conexiones externas. */
public class RemoteAuthException extends RuntimeException {
    public enum Reason { DISABLED,INVALID_REQUEST,PROVIDER_NOT_CONFIGURED,FLOW_EXPIRED,STATE_MISMATCH,TOKEN_EXCHANGE_FAILED,VAULT_UNAVAILABLE,CONNECTION_NOT_FOUND,REFRESH_REQUIRED,REMOTE_ERROR }
    private final Reason reason;
    /** Descripción: Inicializa una instancia de RemoteAuthException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteAuthException(Reason reason,String message){super(message);this.reason=reason;}
    /** Descripción: Inicializa una instancia de RemoteAuthException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteAuthException(Reason reason,String message,Throwable cause){super(message,cause);this.reason=reason;}
    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de Reason. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason(){return reason;}
}
