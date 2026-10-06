package com.example.editor.audit;

/** Acciones base. Se usan Strings para permitir acciones futuras sin modificar un enum central. */
public final class AuditActions {
    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String IMPORT = "IMPORT";
    public static final String EXPORT = "EXPORT";
    public static final String VALIDATE = "VALIDATE";
    public static final String OPEN = "OPEN";
    public static final String SAVE = "SAVE";
    public static final String EXECUTE = "EXECUTE";
    public static final String APPLY = "APPLY";
    public static final String ROLLBACK = "ROLLBACK";
    public static final String NAVIGATE = "NAVIGATE";
    public static final String CONFIGURE = "CONFIGURE";
    public static final String UNDO = "UNDO";
    public static final String REDO = "REDO";
    public static final String CLOSE = "CLOSE";
    /** Descripción: Inicializa una instancia de AuditActions con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private AuditActions() { }
}
