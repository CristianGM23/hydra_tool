package com.example.editor.event.flow.web;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.event.flow.EventFlowApplicationService;
import com.example.editor.event.flow.EventFlowApplicationException;
import com.example.editor.event.flow.EventFlowCheckReport;
import com.example.editor.event.flow.EventFlowLoadResult;
import com.example.editor.event.flow.EventFlowModel;
import com.example.editor.event.flow.EventFlowSaveResponse;
import com.example.editor.event.flow.EventFlowSummary;

/** API REST de contratos EventFlowModel. */
@RestController
@RequestMapping("/api/canonical-metamodels")
public class CanonicalEventFlowController {
    private final EventFlowApplicationService service;
    /** Descripción: Inicializa una instancia de CanonicalEventFlowController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalEventFlowController(EventFlowApplicationService service) { this.service = service; }
    @GetMapping("/event-flows") public List<EventFlowSummary> list() { return service.list(); }
    @PostMapping("/{metamodelId}/event-flows/check") public EventFlowCheckReport checkDraft(@PathVariable String metamodelId, @RequestBody EventFlowModel model) { return service.checkDraft(metamodelId, model); }
    @PostMapping("/{metamodelId}/event-flows")
    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalEventFlowController. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EventFlowSaveResponse> create(@PathVariable String metamodelId, @RequestBody EventFlowModel model) {
        EventFlowSaveResponse result = service.create(metamodelId, model);
        return ResponseEntity.created(URI.create("/api/canonical-metamodels/" + metamodelId + "/event-flows"))
                .eTag(result.etag()).body(result);
    }
    @GetMapping("/{metamodelId}/event-flows") public ResponseEntity<EventFlowLoadResult> find(@PathVariable String metamodelId) { try{return ResponseEntity.ok(service.find(metamodelId));}catch(EventFlowApplicationException exception){if(exception.getReason()==EventFlowApplicationException.Reason.NOT_FOUND)return ResponseEntity.notFound().build();throw exception;} }
    @GetMapping("/{metamodelId}/event-flows/optional") public ResponseEntity<EventFlowLoadResult> findOptional(@PathVariable String metamodelId) {
        return service.findOptional(metamodelId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PutMapping("/{metamodelId}/event-flows")
    /** Descripción: Actualiza el estado gestionado por CanonicalEventFlowController mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EventFlowSaveResponse> replace(@PathVariable String metamodelId,
            @RequestHeader(value = "If-Match", required = false) String etag, @RequestBody EventFlowModel model) {
        EventFlowSaveResponse result = service.replace(metamodelId, model, etag);
        return ResponseEntity.ok().eTag(result.etag()).body(result);
    }
    @PostMapping("/{metamodelId}/event-flows/check-saved") public EventFlowCheckReport check(@PathVariable String metamodelId) { return service.check(metamodelId); }
    @DeleteMapping("/{metamodelId}/event-flows") public ResponseEntity<Void> delete(@PathVariable String metamodelId,
            @RequestHeader(value = "If-Match", required = false) String etag) { service.delete(metamodelId, etag); return ResponseEntity.noContent().build(); }
}
