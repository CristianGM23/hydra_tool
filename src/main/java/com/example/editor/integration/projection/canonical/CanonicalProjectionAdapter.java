package com.example.editor.integration.projection.canonical;

import java.util.List;
import com.example.editor.integration.projection.CanonicalArtifactContent;
import com.example.editor.integration.projection.ProjectionGenerationResult;

/** SPI neutral para proyecciones documentales con importación parcial o exacta. */
public interface CanonicalProjectionAdapter {
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de CanonicalProjectionAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ CanonicalProjectionFormat format();
    /** Descripción: Crea o prepara el resultado de «generate» dentro de CanonicalProjectionAdapter. Entradas: artifacts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ ProjectionGenerationResult generate(List<CanonicalArtifactContent> artifacts);
    /** Descripción: Ejecuta la operación «importProjection» correspondiente a la responsabilidad de CanonicalProjectionAdapter. Entradas: fileName: archivo o contenido que debe procesarse; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ CanonicalProjectionImportResult importProjection(String fileName, byte[] content);
}
