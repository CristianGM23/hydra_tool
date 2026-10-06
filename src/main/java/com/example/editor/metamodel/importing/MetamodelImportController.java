package com.example.editor.metamodel.importing;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


/**
 * Expone detección, análisis, logs e importación persistente.
 */
@RestController
@RequestMapping("/api/metamodel-imports")
public class MetamodelImportController {

    private final MetamodelImportService service;
    private final MetamodelImportAnalysisJobService analysisJobService;
    private final MetamodelImportStagingService staging;
    private final MetamodelBrowserDirectoryArchiveService browserDirectoryArchive;

    /** Descripción: Inicializa el controlador de importación con el pipeline neutral, staging y archivado seguro de directorios. Entradas: service: servicio de importación; analysisJobService: análisis asíncrono; staging: almacenamiento temporal; browserDirectoryArchive: empaquetador acotado de carpetas. Salida: deja el controlador listo para importar archivos o directorios sin duplicar importadores. */
    /** Descripción: Inicializa una instancia de MetamodelImportController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación; analysisJobService: servicio colaborador utilizado por la operación; staging: dato de entrada necesario para la operación; browserDirectoryArchive: archivo o contenido que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelImportController(MetamodelImportService service, MetamodelImportAnalysisJobService analysisJobService, MetamodelImportStagingService staging, MetamodelBrowserDirectoryArchiveService browserDirectoryArchive) {
        this.service = service;
        this.analysisJobService = analysisJobService;
        this.staging = staging;
        this.browserDirectoryArchive = browserDirectoryArchive;
    }

    @GetMapping("/formats")
    /** Descripción: Obtiene la información correspondiente a «listFormats» dentro de MetamodelImportController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<MetamodelImportFormatResponse> listFormats() {
        return service.listFormats();
    }

    @PostMapping(value = "/analysis-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Crea o prepara el resultado de «startAnalysis» dentro de MetamodelImportController. Entradas: file: archivo o contenido que debe procesarse; format: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; overwrite: dato de entrada necesario para la operación; preserveLayout: dato de entrada necesario para la operación; preserveStyles: dato de entrada necesario para la operación; strict: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportAnalysisJobResponse startAnalysis(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "auto") String format, @RequestParam(defaultValue = "") String targetId, @RequestParam(defaultValue = "false") boolean overwrite, @RequestParam(defaultValue = "true") boolean preserveLayout, @RequestParam(defaultValue = "true") boolean preserveStyles, @RequestParam(defaultValue = "false") boolean strict) {
        MetamodelImportStagedFile staged = staging.stage(file);
        return analysisJobService.start(staged, new MetamodelImportOptions(format, targetId, overwrite, preserveLayout, preserveStyles, strict));
    }

    @GetMapping("/analysis-jobs/{jobId}")
    /** Descripción: Obtiene la información correspondiente a «getAnalysis» dentro de MetamodelImportController. Entradas: jobId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportAnalysisJobResponse getAnalysis(@PathVariable String jobId) {
        return analysisJobService.get(jobId);
    }

    @GetMapping("/analysis-jobs/{jobId}/stages/{stageId}/log")
    /** Descripción: Ejecuta la operación «downloadStageLog» correspondiente a la responsabilidad de MetamodelImportController. Entradas: jobId: identificador que delimita el recurso objetivo; stageId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> downloadStageLog(@PathVariable String jobId, @PathVariable String stageId) {
        return logResponse(analysisJobService.getStageLog(jobId, stageId));
    }

    @GetMapping("/analysis-jobs/{jobId}/logs")
    /** Descripción: Ejecuta la operación «downloadCombinedLog» correspondiente a la responsabilidad de MetamodelImportController. Entradas: jobId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> downloadCombinedLog(@PathVariable String jobId) {
        return logResponse(analysisJobService.getCombinedLog(jobId));
    }

    @PostMapping("/analysis-jobs/{jobId}/import")
    /** Descripción: Ejecuta la operación «importPrepared» correspondiente a la responsabilidad de MetamodelImportController. Entradas: jobId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportSaveResult importPrepared(@PathVariable String jobId) {
        MetamodelPreparedImport prepared = analysisJobService.prepared(jobId);
        return service.importPrepared(prepared.preview(), prepared.options().overwrite());
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de MetamodelImportController. Entradas: file: archivo o contenido que debe procesarse; format: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; overwrite: dato de entrada necesario para la operación; preserveLayout: dato de entrada necesario para la operación; preserveStyles: dato de entrada necesario para la operación; strict: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportPreview preview(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "auto") String format, @RequestParam(defaultValue = "") String targetId, @RequestParam(defaultValue = "false") boolean overwrite, @RequestParam(defaultValue = "true") boolean preserveLayout, @RequestParam(defaultValue = "true") boolean preserveStyles, @RequestParam(defaultValue = "false") boolean strict) {
        MetamodelImportStagedFile staged = staging.stage(file);
        try {
            return service.preview(staged.fileName(), staged.mediaType(), staging.read(staged), new MetamodelImportOptions(format, targetId, overwrite, preserveLayout, preserveStyles, strict));
        } finally {
            staging.delete(staged);
        }
    }

    @PostMapping(value = "/browser-directory", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Importa un directorio seleccionado por el navegador reutilizando exactamente el pipeline seguro de importación de ZIP. Entradas: files: archivos del directorio; paths: rutas relativas; format/targetId/overwrite/preserveLayout/preserveStyles/strict: opciones normales de importación. Salida: devuelve el metamodelo persistido y su previsualización. */
    /** Descripción: Ejecuta la operación «importBrowserDirectory» correspondiente a la responsabilidad de MetamodelImportController. Entradas: files: archivo o contenido que debe procesarse; paths: ruta o ubicación utilizada por la operación; format: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; overwrite: dato de entrada necesario para la operación; preserveLayout: dato de entrada necesario para la operación; preserveStyles: dato de entrada necesario para la operación; strict: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportSaveResult importBrowserDirectory(@RequestPart("files") List<MultipartFile> files, @RequestParam("paths") List<String> paths, @RequestParam(defaultValue = "auto") String format, @RequestParam(defaultValue = "") String targetId, @RequestParam(defaultValue = "false") boolean overwrite, @RequestParam(defaultValue = "true") boolean preserveLayout, @RequestParam(defaultValue = "true") boolean preserveStyles, @RequestParam(defaultValue = "false") boolean strict) {
        byte[] archive = browserDirectoryArchive.archive(files, paths);
        return service.importAndSave("browser-metamodel-directory.zip", "application/zip", archive, new MetamodelImportOptions(format, targetId, overwrite, preserveLayout, preserveStyles, strict));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «importAndSave» correspondiente a la responsabilidad de MetamodelImportController. Entradas: file: archivo o contenido que debe procesarse; format: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; overwrite: dato de entrada necesario para la operación; preserveLayout: dato de entrada necesario para la operación; preserveStyles: dato de entrada necesario para la operación; strict: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportSaveResult importAndSave(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "auto") String format, @RequestParam(defaultValue = "") String targetId, @RequestParam(defaultValue = "false") boolean overwrite, @RequestParam(defaultValue = "true") boolean preserveLayout, @RequestParam(defaultValue = "true") boolean preserveStyles, @RequestParam(defaultValue = "false") boolean strict) {
        MetamodelImportStagedFile staged = staging.stage(file);
        try {
            return service.importAndSave(staged.fileName(), staged.mediaType(), staging.read(staged), new MetamodelImportOptions(format, targetId, overwrite, preserveLayout, preserveStyles, strict));
        } finally {
            staging.delete(staged);
        }
    }

    /** Descripción: Ejecuta la operación «logResponse» correspondiente a la responsabilidad de MetamodelImportController. Entradas: artifact: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ResponseEntity<byte[]> logResponse(MetamodelImportAnalysisLogArtifact artifact) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.fileName() + "\"");
        headers.setContentType(MediaType.parseMediaType(artifact.mediaType()));
        headers.setContentLength(artifact.content().length);
        return ResponseEntity.ok().headers(headers).body(artifact.content());
    }

}
