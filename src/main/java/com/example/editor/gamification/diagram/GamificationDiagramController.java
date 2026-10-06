package com.example.editor.gamification.diagram;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/canonical-metamodels/{metamodelId}/gamification/diagram")
public class GamificationDiagramController {
    private final GamificationDiagramService service;
    /** Descripción: Inicializa una instancia de GamificationDiagramController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GamificationDiagramController(GamificationDiagramService service) { this.service = service; }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de GamificationDiagramController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public GamificationDiagramLoadResult find(@PathVariable String metamodelId) { return service.find(metamodelId); }
    /** Descripción: Actualiza el estado gestionado por GamificationDiagramController mediante la operación «save». Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @PutMapping public ResponseEntity<GamificationDiagramLoadResult> save(@PathVariable String metamodelId,
            @RequestHeader(value = "If-Match", required = false) String etag, @RequestBody GamificationDiagramDocument diagram) {
        GamificationDiagramLoadResult result = service.save(metamodelId, diagram, etag);
        return ResponseEntity.ok().eTag(result.etag()).body(result);
    }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @DeleteMapping public ResponseEntity<Void> delete(@PathVariable String metamodelId,
            @RequestHeader(value = "If-Match", required = false) String etag) {
        service.delete(metamodelId, etag);
        return ResponseEntity.noContent().build();
    }
}
