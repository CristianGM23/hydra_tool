package com.example.editor.gamification.export;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/canonical-metamodels/{metamodelId}/gamification/exports")
public class GamificationExportController {
    private final GamificationExportService service;
    /** Descripción: Inicializa una instancia de GamificationExportController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GamificationExportController(GamificationExportService service) { this.service = service; }
    @GetMapping("/{format}")
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de GamificationExportController. Entradas: metamodelId: identificador que delimita el recurso objetivo; format: dato de entrada necesario para la operación; view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> export(@PathVariable String metamodelId, @PathVariable String format,
            @RequestParam(defaultValue = "rules") String view) {
        ExportedGamificationArtifact result = service.export(metamodelId, format, view);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .body(result.content().getBytes(StandardCharsets.UTF_8));
    }
}
