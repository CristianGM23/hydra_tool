package com.example.editor.audit;

/** SPI de redacción. Un módulo puede proteger nuevas rutas/campos sin modificar el diff genérico. */
public interface AuditRedactionPolicy {
    /** Descripción: Ejecuta la operación «sensitive» correspondiente a la responsabilidad de AuditRedactionPolicy. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean sensitive(String path);
}
