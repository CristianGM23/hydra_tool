package com.example.editor.standalone;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API del primer generador web independiente de HYDRA. */
@RestController
@RequestMapping("/api/standalone-editors")
public class StandaloneEditorController {
    private final StandaloneEditorGeneratorService service;
    /** Descripción: Inicializa una instancia de StandaloneEditorController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public StandaloneEditorController(StandaloneEditorGeneratorService service) { this.service = service; }
    @PostMapping(path = "/generate", produces = "application/zip")
    /** Descripción: Crea o prepara el resultado de «generate» dentro de StandaloneEditorController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> generate(@RequestBody StandaloneEditorGenerationRequest request) {
        StandaloneEditorGenerationResult result = service.generate(request);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.fileName()).build().toString())
                .header("X-HYDRA-Standalone-SHA256", result.sha256()).body(result.archive());
    }
}
