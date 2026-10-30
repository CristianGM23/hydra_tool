package com.example.editor.project;

/** Error estable del módulo de proyectos locales. */
public class ProjectException extends RuntimeException {
    public enum Reason { DISABLED,INVALID_REQUEST,NOT_FOUND,CONFLICT,PRECONDITION_FAILED,STORAGE_ERROR,UNSUPPORTED_MEDIA_TYPE,FILE_TOO_LARGE }
    private final Reason reason;
    private final String publicMessage;
    /** Descripción: Inicializa una instancia de ProjectException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectException(Reason reason,String message) { this(reason,message,"",null); }
    /** Descripción: Inicializa una instancia de ProjectException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectException(Reason reason,String message,Throwable cause) { this(reason,message,"",cause); }
    /** Descripción: Error con un mensaje para el cliente distinto del interno: un fallo del servidor oculta su mensaje interno (rutas, causas) y responde este, que solo debe contener información segura y útil. Entradas: reason, message (interno, para el log), publicMessage, cause. Salida: excepción. */ public ProjectException(Reason reason,String message,String publicMessage,Throwable cause) { super(message,cause); this.reason = reason; this.publicMessage = publicMessage == null ? "" : publicMessage; }
    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de Reason. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() { return reason; }
    /** Descripción: Mensaje público explícito de un fallo del servidor. Entradas: ninguna. Salida: mensaje, vacío si no lo hay. */ public String publicMessage() { return publicMessage; }
}
