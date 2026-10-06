package com.example.editor.integration.projection.canonical;

import org.springframework.stereotype.Service;
import com.example.editor.integration.IntegrationProfileApplicationService;
import com.example.editor.integration.compiler.CanonicalIntegrationArtifactContentLoader;
import com.example.editor.integration.projection.ProjectionGenerationResult;

/** Genera proyecciones para la misma selección de artefactos de un perfil de integración. */
@Service
public class CanonicalProjectionApplicationService {
    private final IntegrationProfileApplicationService profiles;
    private final CanonicalIntegrationArtifactContentLoader artifacts;
    private final CanonicalProjectionService projections;
    private final CanonicalProjectionArchiveService archives;

    /** Descripción: Inicializa una instancia de CanonicalProjectionApplicationService con sus dependencias y datos de entrada. Entradas: profiles: archivo o contenido que debe procesarse; artifacts: dato de entrada necesario para la operación; projections: dato de entrada necesario para la operación; archives: archivo o contenido que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalProjectionApplicationService(IntegrationProfileApplicationService profiles,
            CanonicalIntegrationArtifactContentLoader artifacts, CanonicalProjectionService projections,
            CanonicalProjectionArchiveService archives){this.profiles = profiles; this.artifacts = artifacts; this.projections = projections; this.archives = archives;}

    /** Descripción: Crea o prepara el resultado de «generate» dentro de CanonicalProjectionApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; format: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectionGenerationResult generate(String metamodelId, String profileId, CanonicalProjectionFormat format){
        var profile = profiles.find(metamodelId, profileId).profile(); return projections.generate(format, artifacts.load(profile));
    }
    /** Descripción: Ejecuta la operación «archive» correspondiente a la responsabilidad de CanonicalProjectionApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; format: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] archive(String metamodelId, String profileId, CanonicalProjectionFormat format){return archives.create(format, generate(metamodelId, profileId, format));}
}
