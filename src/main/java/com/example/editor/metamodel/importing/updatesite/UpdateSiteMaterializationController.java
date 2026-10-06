package com.example.editor.metamodel.importing.updatesite;

import java.util.List;
import java.util.function.Function;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Canonical layer materialization, merge and rollback commands. */
@RestController
@RequestMapping("/api/update-site-imports")
public class UpdateSiteMaterializationController {
    private final UpdateSiteLayerMaterializationService materializationService;
    private final UpdateSiteLayerMergeService layerMergeService;
    private final UpdateSiteRepositorySourceService repositorySourceService;
    private final UpdateSiteRemoteSnapshotService remoteSnapshotService;
    private final UpdateSiteUploadStagingService uploadStagingService;
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de UpdateSiteMaterializationController con sus dependencias y datos de entrada. Entradas: materializationService: servicio colaborador utilizado por la operación; layerMergeService: servicio colaborador utilizado por la operación; repositorySourceService: servicio colaborador utilizado por la operación; remoteSnapshotService: servicio colaborador utilizado por la operación; uploadStagingService: servicio colaborador utilizado por la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteMaterializationController(UpdateSiteLayerMaterializationService materializationService, UpdateSiteLayerMergeService layerMergeService,
            UpdateSiteRepositorySourceService repositorySourceService, UpdateSiteRemoteSnapshotService remoteSnapshotService,
            UpdateSiteUploadStagingService uploadStagingService, JsonMapper mapper) {
        this.materializationService=materializationService; this.layerMergeService=layerMergeService; this.repositorySourceService=repositorySourceService;
        this.remoteSnapshotService=remoteSnapshotService; this.uploadStagingService=uploadStagingService; this.mapper=mapper;
    }

    @PostMapping(value="/materialize", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «materialize» correspondiente a la responsabilidad de UpdateSiteMaterializationController. Entradas: files: archivo o contenido que debe procesarse; requestJson: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteLayerMaterializationResult materialize(@RequestPart("files") List<MultipartFile> files, @RequestPart("request") String requestJson) {
        return withStaged(files, staged -> materializationService.materialize(staged, parse(requestJson, UpdateSiteLayerMaterializationRequest.class)));
    }

    @PostMapping(value="/merge", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Actualiza el estado gestionado por UpdateSiteMaterializationController mediante la operación «merge». Entradas: files: archivo o contenido que debe procesarse; requestJson: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteLayerMergeResult merge(@RequestPart("files") List<MultipartFile> files, @RequestPart("request") String requestJson) {
        return withStaged(files, staged -> layerMergeService.merge(staged, parse(requestJson, UpdateSiteLayerMergeRequest.class)));
    }

    @PostMapping(value="/merge/rollback", consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «rollbackMerge» correspondiente a la responsabilidad de UpdateSiteMaterializationController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteLayerMergeRollbackResult rollbackMerge(@RequestBody UpdateSiteLayerMergeRollbackRequest request) { return layerMergeService.rollback(request); }

    @PostMapping(value="/materialize/rollback", consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de UpdateSiteMaterializationController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteLayerMaterializationRollbackResult rollback(@RequestBody UpdateSiteLayerMaterializationRollbackRequest request) { return materializationService.rollback(request); }

    @PostMapping(value="/repository/snapshots/{snapshotId}/materialize", consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «materializeRepositorySnapshot» correspondiente a la responsabilidad de UpdateSiteMaterializationController. Entradas: snapshotId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteLayerMaterializationResult materializeRepositorySnapshot(@PathVariable String snapshotId, @RequestBody UpdateSiteLayerMaterializationRequest request) {
        return materializationService.materialize(repositorySourceService.inputs(snapshotId),request);
    }

    @PostMapping(value="/repository/snapshots/{snapshotId}/merge", consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Actualiza el estado gestionado por UpdateSiteMaterializationController mediante la operación «mergeRepositorySnapshot». Entradas: snapshotId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteLayerMergeResult mergeRepositorySnapshot(@PathVariable String snapshotId, @RequestBody UpdateSiteLayerMergeRequest request) {
        return layerMergeService.merge(repositorySourceService.inputs(snapshotId),request);
    }

    @PostMapping(value="/remote/snapshots/{snapshotId}/materialize", consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «materializeRemoteSnapshot» correspondiente a la responsabilidad de UpdateSiteMaterializationController. Entradas: snapshotId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteLayerMaterializationResult materializeRemoteSnapshot(@PathVariable String snapshotId, @RequestBody UpdateSiteLayerMaterializationRequest request) {
        return materializationService.materialize(remoteSnapshotService.operations(snapshotId).inputs(),request);
    }

    @PostMapping(value="/remote/snapshots/{snapshotId}/merge", consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Actualiza el estado gestionado por UpdateSiteMaterializationController mediante la operación «mergeRemoteSnapshot». Entradas: snapshotId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteLayerMergeResult mergeRemoteSnapshot(@PathVariable String snapshotId, @RequestBody UpdateSiteLayerMergeRequest request) {
        return layerMergeService.merge(remoteSnapshotService.operations(snapshotId).inputs(),request);
    }

    /** Descripción: Ejecuta la operación «withStaged» correspondiente a la responsabilidad de UpdateSiteMaterializationController. Entradas: files: archivo o contenido que debe procesarse; operation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <T> T withStaged(List<MultipartFile> files, Function<List<UpdateSiteInputFile>,T> operation) {
        List<UpdateSiteInputFile> staged=uploadStagingService.stage(files);
        try{return operation.apply(staged);}finally{uploadStagingService.cleanup(staged);}
    }

    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de UpdateSiteMaterializationController. Entradas: json: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <T> T parse(String json, Class<T> type) {
        try{return mapper.readValue(json,type);}catch(JacksonException exception){
            throw new MetamodelDesignException(Reason.INVALID_REQUEST,"Invalid update-site request JSON",exception);
        }
    }
}
