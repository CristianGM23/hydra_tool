package com.example.editor.metamodel.importing.updatesite;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Analysis commands for reproducible repository and offline remote snapshots. */
@RestController
@RequestMapping("/api/update-site-imports")
public class UpdateSiteAnalysisController {
    private final UpdateSiteRepositorySourceService repositorySourceService;
    private final UpdateSiteRemoteSnapshotService remoteSnapshotService;

    /** Descripción: Inicializa una instancia de UpdateSiteAnalysisController con sus dependencias y datos de entrada. Entradas: repositorySourceService: servicio colaborador utilizado por la operación; remoteSnapshotService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteAnalysisController(UpdateSiteRepositorySourceService repositorySourceService, UpdateSiteRemoteSnapshotService remoteSnapshotService) {
        this.repositorySourceService=repositorySourceService; this.remoteSnapshotService=remoteSnapshotService;
    }

    @PostMapping(value="/repository/snapshots/{snapshotId}/analyze", consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «analyzeRepositorySnapshot» correspondiente a la responsabilidad de UpdateSiteAnalysisController. Entradas: snapshotId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteImportAnalysis analyzeRepositorySnapshot(@PathVariable String snapshotId, @RequestBody UpdateSiteImportOptions options) {
        return repositorySourceService.analyzeSnapshot(snapshotId,options);
    }

    @PostMapping(value="/remote/snapshots/analyze", consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «analyzeRemoteSnapshot» correspondiente a la responsabilidad de UpdateSiteAnalysisController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteImportAnalysis analyzeRemoteSnapshot(@RequestBody UpdateSiteRemoteSnapshotAnalyzeRequest request) {
        return remoteSnapshotService.analyzeOffline(request);
    }
}
