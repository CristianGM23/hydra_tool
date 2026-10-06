package com.example.editor.integration.projection;
import java.util.List;
import java.util.Set;
import com.example.editor.integration.IntegrationCapability;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.IntegrationTechnology;
/** SPI para PlantUML, BESSER y otros destinos de importación o generación que no son runtimes anfitriones. */
public interface IntegrationProjectionAdapter {
    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de IntegrationProjectionAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ IntegrationTechnology technology();
    /** Descripción: Ejecuta la operación «capabilities» correspondiente a la responsabilidad de IntegrationProjectionAdapter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ Set<IntegrationCapability> capabilities();
    /** Descripción: Ejecuta la operación «analyzeImport» correspondiente a la responsabilidad de IntegrationProjectionAdapter. Entradas: fileName: archivo o contenido que debe procesarse; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ ProjectionImportAnalysis analyzeImport(String fileName, byte[] content);
    /** Descripción: Crea o prepara el resultado de «generate» dentro de IntegrationProjectionAdapter. Entradas: profile: archivo o contenido que debe procesarse; artifacts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ ProjectionGenerationResult generate(IntegrationProfileModel profile, List<CanonicalArtifactContent> artifacts);
}
