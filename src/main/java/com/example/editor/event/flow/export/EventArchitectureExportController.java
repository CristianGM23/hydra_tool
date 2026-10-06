package com.example.editor.event.flow.export;

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
@RequestMapping("/api/canonical-metamodels/{metamodelId}/event-architecture/exports")
public class EventArchitectureExportController {
    private final EventArchitectureExportService service;
    /** Descripción: Inicializa una instancia de EventArchitectureExportController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventArchitectureExportController(EventArchitectureExportService service) { this.service = service; }
    @GetMapping("/{format}") public ResponseEntity<byte[]> export(@PathVariable String metamodelId, @PathVariable String format,
            @RequestParam(defaultValue = "flow") String view) {
        ExportedEventArchitecture result = service.export(metamodelId, format, view);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .body(result.content().getBytes(StandardCharsets.UTF_8));
    }
}
