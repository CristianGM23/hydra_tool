package com.example.editor.metamodel.importing.updatesite;

import java.util.List;
import org.springframework.http.MediaType;
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

/** Entrada local acotada: recepción multipart histórica y análisis previo. */
@RestController
@RequestMapping("/api/update-site-imports")
public class UpdateSiteImportController {
    private final UpdateSiteImportApplicationService service;
    private final UpdateSiteRemoteSnapshotService remoteSnapshotService;
    private final UpdateSiteUploadStagingService uploadStagingService;
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de UpdateSiteImportController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación; remoteSnapshotService: servicio colaborador utilizado por la operación; uploadStagingService: servicio colaborador utilizado por la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteImportController(UpdateSiteImportApplicationService service, UpdateSiteRemoteSnapshotService remoteSnapshotService,
            UpdateSiteUploadStagingService uploadStagingService, JsonMapper mapper) {
        this.service=service; this.remoteSnapshotService=remoteSnapshotService; this.uploadStagingService=uploadStagingService; this.mapper=mapper;
    }

    /** Conserva el multipart histórico aquí para mantener el contrato de staging auditado. */
    @PostMapping(value = "/remote/snapshots/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «importRemoteSnapshot» correspondiente a la responsabilidad de UpdateSiteImportController. Entradas: file: archivo o contenido que debe procesarse; optionsJson: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteRemoteSnapshotResult importRemoteSnapshot(@RequestPart("file") MultipartFile file,
            @RequestPart(value = "options", required = false) String optionsJson) {
        UpdateSiteImportOptions options = optionsJson == null || optionsJson.isBlank()
                ? UpdateSiteImportOptions.defaults() : parse(optionsJson, UpdateSiteImportOptions.class);
        return withStaged(List.of(file), staged -> remoteSnapshotService.importSnapshot(staged.getFirst(), options));
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de UpdateSiteImportController. Entradas: files: archivo o contenido que debe procesarse; targetId: identificador que delimita el recurso objetivo; strict: dato de entrada necesario para la operación; candidateId: identificador que delimita el recurso objetivo; recoverPalettes: dato de entrada necesario para la operación; recoverRepresentations: dato de entrada necesario para la operación; recoverMappings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteImportAnalysis analyze(@RequestPart("files") List<MultipartFile> files,
            @RequestParam(defaultValue = "") String targetId, @RequestParam(defaultValue = "false") boolean strict,
            @RequestParam(defaultValue = "") String candidateId, @RequestParam(defaultValue = "true") boolean recoverPalettes,
            @RequestParam(defaultValue = "true") boolean recoverRepresentations, @RequestParam(defaultValue = "true") boolean recoverMappings) {
        return withStaged(files, staged -> service.analyze(staged, new UpdateSiteImportOptions(targetId, false, strict, candidateId,
                recoverPalettes, recoverRepresentations, recoverMappings, true)));
    }

    /** Descripción: Ejecuta la operación «withStaged» correspondiente a la responsabilidad de UpdateSiteImportController. Entradas: files: archivo o contenido que debe procesarse; operation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <T> T withStaged(List<MultipartFile> files, java.util.function.Function<List<UpdateSiteInputFile>, T> operation) {
        List<UpdateSiteInputFile> staged=uploadStagingService.stage(files);
        try{return operation.apply(staged);}finally{uploadStagingService.cleanup(staged);}
    }
    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de UpdateSiteImportController. Entradas: json: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <T> T parse(String json,Class<T> type){try{return mapper.readValue(json,type);}catch(JacksonException exception){
        throw new MetamodelDesignException(Reason.INVALID_REQUEST,"Invalid update-site request JSON",exception);}}
}
