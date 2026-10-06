package com.example.editor.metamodel.importing.updatesite;

import java.util.List;
import java.util.function.Function;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Recovery/import commands separated from source discovery and layer materialization. */
@RestController
@RequestMapping("/api/update-site-imports")
public class UpdateSiteRecoveryController {
    private final UpdateSiteImportApplicationService service;
    private final UpdateSiteRepositorySourceService repositorySourceService;
    private final UpdateSiteRemoteSnapshotService remoteSnapshotService;
    private final UpdateSiteUploadStagingService uploadStagingService;
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de UpdateSiteRecoveryController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación; repositorySourceService: servicio colaborador utilizado por la operación; remoteSnapshotService: servicio colaborador utilizado por la operación; uploadStagingService: servicio colaborador utilizado por la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteRecoveryController(UpdateSiteImportApplicationService service, UpdateSiteRepositorySourceService repositorySourceService,
            UpdateSiteRemoteSnapshotService remoteSnapshotService, UpdateSiteUploadStagingService uploadStagingService, JsonMapper mapper) {
        this.service=service; this.repositorySourceService=repositorySourceService; this.remoteSnapshotService=remoteSnapshotService;
        this.uploadStagingService=uploadStagingService; this.mapper=mapper;
    }

    @PostMapping(value="/reconcile", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «reconcile» correspondiente a la responsabilidad de UpdateSiteRecoveryController. Entradas: files: archivo o contenido que debe procesarse; requestJson: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteReconciliationResult reconcile(@RequestPart("files") List<MultipartFile> files, @RequestPart("request") String requestJson) {
        return withStaged(files, staged -> service.reconcile(staged, parse(requestJson, UpdateSiteReconciliationRequest.class)));
    }

    @PostMapping(value="/import", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «importAndSave» correspondiente a la responsabilidad de UpdateSiteRecoveryController. Entradas: files: archivo o contenido que debe procesarse; targetId: identificador que delimita el recurso objetivo; overwrite: dato de entrada necesario para la operación; strict: dato de entrada necesario para la operación; candidateId: identificador que delimita el recurso objetivo; recoverPalettes: dato de entrada necesario para la operación; recoverRepresentations: dato de entrada necesario para la operación; recoverMappings: dato de entrada necesario para la operación; persistRecoveryPackage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteImportResult importAndSave(@RequestPart("files") List<MultipartFile> files,
            @RequestParam(defaultValue="") String targetId, @RequestParam(defaultValue="false") boolean overwrite,
            @RequestParam(defaultValue="false") boolean strict, @RequestParam String candidateId,
            @RequestParam(defaultValue="true") boolean recoverPalettes, @RequestParam(defaultValue="true") boolean recoverRepresentations,
            @RequestParam(defaultValue="true") boolean recoverMappings, @RequestParam(defaultValue="true") boolean persistRecoveryPackage) {
        return withStaged(files, staged -> service.importAndSave(staged, new UpdateSiteImportOptions(targetId, overwrite, strict, candidateId,
                recoverPalettes, recoverRepresentations, recoverMappings, persistRecoveryPackage)));
    }

    @PostMapping(value="/repository/snapshots/{snapshotId}/import", consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «importRepositorySnapshot» correspondiente a la responsabilidad de UpdateSiteRecoveryController. Entradas: snapshotId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteImportResult importRepositorySnapshot(@PathVariable String snapshotId, @RequestBody UpdateSiteImportOptions options) {
        return repositorySourceService.importSnapshot(snapshotId,options);
    }

    @PostMapping(value="/repository/snapshots/{snapshotId}/reconcile", consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «reconcileRepositorySnapshot» correspondiente a la responsabilidad de UpdateSiteRecoveryController. Entradas: snapshotId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteReconciliationResult reconcileRepositorySnapshot(@PathVariable String snapshotId, @RequestBody UpdateSiteReconciliationRequest request) {
        return repositorySourceService.reconcileSnapshot(snapshotId,request);
    }

    @PostMapping(value="/remote/snapshots/{snapshotId}/import", consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «importRemoteSnapshot» correspondiente a la responsabilidad de UpdateSiteRecoveryController. Entradas: snapshotId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteImportResult importRemoteSnapshot(@PathVariable String snapshotId, @RequestBody UpdateSiteImportOptions options) {
        UpdateSiteRemoteSnapshotOperations snapshot=remoteSnapshotService.operations(snapshotId);
        return service.importAndSave(snapshot.inputs(),options==null?UpdateSiteImportOptions.defaults():options);
    }

    @PostMapping(value="/remote/snapshots/{snapshotId}/reconcile", consumes=MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «reconcileRemoteSnapshot» correspondiente a la responsabilidad de UpdateSiteRecoveryController. Entradas: snapshotId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteReconciliationResult reconcileRemoteSnapshot(@PathVariable String snapshotId, @RequestBody UpdateSiteReconciliationRequest request) {
        return service.reconcile(remoteSnapshotService.operations(snapshotId).inputs(),request);
    }

    /** Descripción: Ejecuta la operación «withStaged» correspondiente a la responsabilidad de UpdateSiteRecoveryController. Entradas: files: archivo o contenido que debe procesarse; operation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <T> T withStaged(List<MultipartFile> files, Function<List<UpdateSiteInputFile>,T> operation) {
        List<UpdateSiteInputFile> staged=uploadStagingService.stage(files);
        try{return operation.apply(staged);}finally{uploadStagingService.cleanup(staged);}
    }

    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de UpdateSiteRecoveryController. Entradas: json: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <T> T parse(String json, Class<T> type) {
        try{return mapper.readValue(json,type);}catch(JacksonException exception){
            throw new MetamodelDesignException(Reason.INVALID_REQUEST,"Invalid update-site request JSON",exception);
        }
    }
}
