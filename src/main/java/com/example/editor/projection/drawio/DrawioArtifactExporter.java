package com.example.editor.projection.drawio;

/** SPI extensible: cada módulo registra su propia proyección sin modificar un exportador central. */
public interface DrawioArtifactExporter<T> {
    /** Descripción: Ejecuta la operación «artifactType» correspondiente a la responsabilidad de DrawioArtifactExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String artifactType();
    /** Descripción: Ejecuta la operación «sourceType» correspondiente a la responsabilidad de DrawioArtifactExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Class<T> sourceType();
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de DrawioArtifactExporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ String export(T source, DrawioExportOptions options);
}
