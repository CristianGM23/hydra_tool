package com.example.editor.metamodel.export;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone las descargas configurables de metamodelos.
 */
@RestController
@RequestMapping("/api/metamodels/{metamodelId}")
public class MetamodelExportController {

    private final MetamodelExportService service;

    /** Descripción: Inicializa una instancia de MetamodelExportController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelExportController(MetamodelExportService service) {
        this.service = service;
    }

    @GetMapping("/export-formats")
    /** Descripción: Obtiene la información correspondiente a «listFormats» dentro de MetamodelExportController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<MetamodelExportFormatResponse> listFormats(@PathVariable String metamodelId) {
        return service.listFormats();
    }

    @GetMapping("/exports/{format}")
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de MetamodelExportController. Entradas: metamodelId: identificador que delimita el recurso objetivo; format: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> export(@PathVariable String metamodelId, @PathVariable String format) {
        return response(service.export(metamodelId, format));
    }

    @PostMapping("/exports")
    /** Descripción: Ejecuta la operación «exportConfigured» correspondiente a la responsabilidad de MetamodelExportController. Entradas: metamodelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> exportConfigured(@PathVariable String metamodelId, @RequestBody MetamodelExportRequest request) {
        return response(service.export(metamodelId, request.format(), request.options()));
    }

    @PostMapping("/exports/bundle")
    /** Descripción: Ejecuta la operación «exportBundle» correspondiente a la responsabilidad de MetamodelExportController. Entradas: metamodelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> exportBundle(@PathVariable String metamodelId, @RequestBody MetamodelBundleExportRequest request) {
        return response(service.exportBundle(metamodelId, request.formats(), request.options()));
    }

    /** Descripción: Ejecuta la operación «response» correspondiente a la responsabilidad de MetamodelExportController. Entradas: artifact: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ResponseEntity<byte[]> response(MetamodelExportArtifact artifact) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.fileName() + "\"");
        headers.setContentType(MediaType.parseMediaType(artifact.mediaType()));
        headers.setContentLength(artifact.content().length);
        return ResponseEntity.ok().headers(headers).body(artifact.content());
    }
}
