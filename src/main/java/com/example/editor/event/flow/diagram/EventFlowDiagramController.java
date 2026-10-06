package com.example.editor.event.flow.diagram;

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
@RequestMapping("/api/canonical-metamodels/{metamodelId}/event-flows/diagram")
public class EventFlowDiagramController {
    private final EventFlowDiagramService service;
    /** Descripción: Inicializa una instancia de EventFlowDiagramController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowDiagramController(EventFlowDiagramService service) { this.service = service; }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EventFlowDiagramController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public EventFlowDiagramLoadResult find(@PathVariable String metamodelId) { return service.find(metamodelId); }
    /** Descripción: Actualiza el estado gestionado por EventFlowDiagramController mediante la operación «save». Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @PutMapping public ResponseEntity<EventFlowDiagramLoadResult> save(@PathVariable String metamodelId,
            @RequestHeader(value = "If-Match", required = false) String etag, @RequestBody EventFlowDiagramDocument diagram) {
        EventFlowDiagramLoadResult result = service.save(metamodelId, diagram, etag);
        return ResponseEntity.ok().eTag(result.etag()).body(result);
    }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @DeleteMapping public ResponseEntity<Void> delete(@PathVariable String metamodelId,
            @RequestHeader(value = "If-Match", required = false) String etag) { service.delete(metamodelId, etag); return ResponseEntity.noContent().build(); }
}
