package com.example.editor.event.web;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.editor.event.*;
/** API conjunta de definiciones y bindings de eventos. */
@RestController
@RequestMapping("/api/canonical-metamodels")
public class CanonicalEventModelController {
    private final EventModelApplicationService service;
    /** Descripción: Inicializa una instancia de CanonicalEventModelController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalEventModelController(EventModelApplicationService service) { this.service = service; }
    @GetMapping("/events") public List<EventModelSummary> list() { return service.list(); }
    @GetMapping("/events/reserved-types") public java.util.Set<String> reservedTypes() { return ReservedEventCatalog.eventTypeIds(); }
    @PostMapping("/{metamodelId}/events")
    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalEventModelController. Entradas: metamodelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EventModelSaveResponse> create(@PathVariable String metamodelId, @RequestBody EventModelRequest request) {
        EventModelSaveResponse result = service.create(metamodelId, request.definitions(), request.bindings());
        return ResponseEntity.created(URI.create("/api/canonical-metamodels/" + metamodelId + "/events"))
                .header("X-Event-Definitions-ETag", result.definitionEtag()).header("X-Event-Bindings-ETag", result.bindingEtag()).body(result);
    }
    @GetMapping("/{metamodelId}/events") public ResponseEntity<EventModelLoadResult> find(@PathVariable String metamodelId) { try{return ResponseEntity.ok(service.find(metamodelId));}catch(EventModelApplicationException exception){if(exception.getReason()==EventModelApplicationException.Reason.NOT_FOUND)return ResponseEntity.notFound().build();throw exception;} }
    @PutMapping("/{metamodelId}/events")
    /** Descripción: Actualiza el estado gestionado por CanonicalEventModelController mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; definitionEtag: dato de entrada necesario para la operación; bindingEtag: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<EventModelSaveResponse> replace(@PathVariable String metamodelId,
            @RequestHeader(value = "If-Match-Definitions", required = false) String definitionEtag,
            @RequestHeader(value = "If-Match-Bindings", required = false) String bindingEtag,
            @RequestBody EventModelRequest request) {
        EventModelSaveResponse result = service.replace(metamodelId, request.definitions(), definitionEtag, request.bindings(), bindingEtag);
        return ResponseEntity.ok().header("X-Event-Definitions-ETag", result.definitionEtag()).header("X-Event-Bindings-ETag", result.bindingEtag()).body(result);
    }
    @PostMapping("/{metamodelId}/events/check") public EventModelCheckReport check(@PathVariable String metamodelId) { return service.check(metamodelId); }
    @DeleteMapping("/{metamodelId}/events") public ResponseEntity<Void> delete(@PathVariable String metamodelId,
            @RequestHeader(value = "If-Match-Definitions", required = false) String definitionEtag,
            @RequestHeader(value = "If-Match-Bindings", required = false) String bindingEtag) { service.delete(metamodelId, definitionEtag, bindingEtag); return ResponseEntity.noContent().build(); }
}
