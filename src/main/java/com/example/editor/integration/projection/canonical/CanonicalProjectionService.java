package com.example.editor.integration.projection.canonical;

import java.util.List;
import org.springframework.stereotype.Service;
import com.example.editor.integration.projection.CanonicalArtifactContent;
import com.example.editor.integration.projection.ProjectionGenerationResult;

/** Fachada de generación e importación de proyecciones documentales. */
@Service
public class CanonicalProjectionService {
    private final CanonicalProjectionAdapterRegistry registry;
    /** Descripción: Inicializa una instancia de CanonicalProjectionService con sus dependencias y datos de entrada. Entradas: registry: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalProjectionService(CanonicalProjectionAdapterRegistry registry){this.registry = registry;}
    /** Descripción: Crea o prepara el resultado de «generate» dentro de CanonicalProjectionService. Entradas: format: dato de entrada necesario para la operación; artifacts: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectionGenerationResult generate(CanonicalProjectionFormat format, List<CanonicalArtifactContent> artifacts){return registry.require(format).generate(artifacts);}
    /** Descripción: Ejecuta la operación «importProjection» correspondiente a la responsabilidad de CanonicalProjectionService. Entradas: format: dato de entrada necesario para la operación; fileName: archivo o contenido que debe procesarse; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public CanonicalProjectionImportResult importProjection(CanonicalProjectionFormat format, String fileName, byte[] content){return registry.require(format).importProjection(fileName, content);}
}
