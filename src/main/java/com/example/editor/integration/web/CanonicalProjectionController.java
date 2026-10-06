package com.example.editor.integration.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.example.editor.integration.projection.canonical.CanonicalProjectionApplicationService;
import com.example.editor.integration.projection.canonical.CanonicalProjectionFormat;
import com.example.editor.integration.projection.canonical.CanonicalProjectionImportResult;
import com.example.editor.integration.projection.canonical.CanonicalProjectionService;

/** API de exportación e importación parcial de PlantUML y Draw.io. */
@RestController
@RequestMapping({
    "/api/canonical-metamodels/{metamodelId}/integrations/{profileId}/projections",
    "/api/semantic-contexts/{metamodelId}/integrations/{profileId}/projections"
})
public class CanonicalProjectionController {
    private final CanonicalProjectionApplicationService application;
    private final CanonicalProjectionService projections;
    /** Descripción: Inicializa una instancia de CanonicalProjectionController con sus dependencias y datos de entrada. Entradas: application: dato de entrada necesario para la operación; projections: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalProjectionController(CanonicalProjectionApplicationService application, CanonicalProjectionService projections){this.application = application; this.projections = projections;}

    @PostMapping(value = "/{format}/export", produces = "application/zip")
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de CanonicalProjectionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; format: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> export(@PathVariable String metamodelId, @PathVariable String profileId,
            @PathVariable CanonicalProjectionFormat format){
        byte[] content = application.archive(metamodelId, profileId, format); HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(profileId + "-" + format.name().toLowerCase() + ".zip").build());
        return ResponseEntity.ok().headers(headers).contentLength(content.length).body(content);
    }

    @PostMapping(value = "/{format}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    /** Descripción: Ejecuta la operación «importProjection» correspondiente a la responsabilidad de CanonicalProjectionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; profileId: identificador que delimita el recurso objetivo; format: dato de entrada necesario para la operación; file: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public CanonicalProjectionImportResult importProjection(@PathVariable String metamodelId, @PathVariable String profileId,
            @PathVariable CanonicalProjectionFormat format, @RequestParam("file") MultipartFile file) throws java.io.IOException {
        return projections.importProjection(format, file.getOriginalFilename(), file.getBytes());
    }
}
