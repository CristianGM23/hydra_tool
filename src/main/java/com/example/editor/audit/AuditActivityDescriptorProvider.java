package com.example.editor.audit;

import java.util.Collection;

/** SPI para que cada módulo publique su vocabulario visual de auditoría. */
public interface AuditActivityDescriptorProvider {
    /** Descripción: Ejecuta la operación «descriptors» correspondiente a la responsabilidad de AuditActivityDescriptorProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Collection<AuditActivityDescriptor> descriptors();
}
