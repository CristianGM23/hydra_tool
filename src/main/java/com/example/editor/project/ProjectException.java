package com.example.editor.project;

/** Error estable del módulo de proyectos locales. */
public class ProjectException extends RuntimeException {
    public enum Reason { DISABLED,INVALID_REQUEST,NOT_FOUND,CONFLICT,PRECONDITION_FAILED,STORAGE_ERROR,UNSUPPORTED_MEDIA_TYPE,FILE_TOO_LARGE }
    private final Reason reason;
    /** Descripción: Inicializa una instancia de ProjectException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectException(Reason reason,String message) { super(message); this.reason = reason; }
    /** Descripción: Inicializa una instancia de ProjectException con sus dependencias y datos de entrada. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ProjectException(Reason reason,String message,Throwable cause) { super(message,cause); this.reason = reason; }
    /** Descripción: Obtiene la información correspondiente a «getReason» dentro de Reason. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Reason getReason() { return reason; }
}
