package com.example.editor.metamodel.importing.updatesite;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Discovery and immutable snapshot lifecycle for remote/repository sources. */
@RestController
@RequestMapping("/api/update-site-imports")
public class UpdateSiteSourceController {
    private final UpdateSiteRemoteRepositoryService remoteRepositoryService;
    private final UpdateSiteRemoteSnapshotService remoteSnapshotService;
    private final UpdateSiteRepositorySourceService repositorySourceService;

    /** Descripción: Inicializa una instancia de UpdateSiteSourceController con sus dependencias y datos de entrada. Entradas: remoteRepositoryService: servicio colaborador utilizado por la operación; remoteSnapshotService: servicio colaborador utilizado por la operación; repositorySourceService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteSourceController(UpdateSiteRemoteRepositoryService remoteRepositoryService,
            UpdateSiteRemoteSnapshotService remoteSnapshotService, UpdateSiteRepositorySourceService repositorySourceService) {
        this.remoteRepositoryService=remoteRepositoryService; this.remoteSnapshotService=remoteSnapshotService; this.repositorySourceService=repositorySourceService;
    }

    @PostMapping(value="/remote/analyze",consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «analyzeRemote» correspondiente a la responsabilidad de UpdateSiteSourceController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteAnalysisResult analyzeRemote(@RequestBody UpdateSiteRemoteAnalysisRequest request){return remoteRepositoryService.analyze(request);}

    @PostMapping(value="/repository/analyze",consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «analyzeRepository» correspondiente a la responsabilidad de UpdateSiteSourceController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRepositorySourceAnalysis analyzeRepository(@RequestBody UpdateSiteRepositorySourceRequest request){return repositorySourceService.analyze(request);}

    @PostMapping(value="/remote/snapshots",consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Crea o prepara el resultado de «createRemoteSnapshot» dentro de UpdateSiteSourceController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteSnapshotResult createRemoteSnapshot(@RequestBody UpdateSiteRemoteAnalysisRequest request){return remoteSnapshotService.create(request);}

    @GetMapping("/remote/snapshots/{snapshotId}")
    /** Descripción: Ejecuta la operación «remoteSnapshot» correspondiente a la responsabilidad de UpdateSiteSourceController. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteSnapshotLock remoteSnapshot(@PathVariable String snapshotId){return remoteSnapshotService.find(snapshotId);}

    @PostMapping(value="/remote/snapshots/{snapshotId}/licenses",consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «reviewRemoteSnapshotLicenses» correspondiente a la responsabilidad de UpdateSiteSourceController. Entradas: snapshotId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteSnapshotLock reviewRemoteSnapshotLicenses(@PathVariable String snapshotId,@RequestBody UpdateSiteRemoteSnapshotLicenseReviewRequest request){return remoteSnapshotService.reviewLicenses(snapshotId,request);}

    @GetMapping("/remote/snapshots/{snapshotId}/export")
    /** Descripción: Ejecuta la operación «exportRemoteSnapshot» correspondiente a la responsabilidad de UpdateSiteSourceController. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> exportRemoteSnapshot(@PathVariable String snapshotId){
        byte[] archive=remoteSnapshotService.exportSnapshot(snapshotId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\""+snapshotId.replaceAll("[^A-Za-z0-9._-]","-")+".hydra-p2-snapshot.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(archive);
    }

    @PostMapping("/remote/snapshots/{snapshotId}/drift")
    /** Descripción: Valida las condiciones necesarias para «checkRemoteSnapshotDrift» y rechaza entradas no válidas. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteSnapshotDriftReport checkRemoteSnapshotDrift(@PathVariable String snapshotId){return remoteSnapshotService.drift(snapshotId);}
}
