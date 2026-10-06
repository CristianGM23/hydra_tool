package com.example.editor.metamodel.behavior.web;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.editor.metamodel.behavior.*;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.metamodel.validation.ValidationReport;

/** API de bindings independientes entre firmas, expresiones y acciones. */
@RestController
@RequestMapping("/api/canonical-metamodels")
public class CanonicalOperationBehaviorController {
    private final OperationBehaviorModelApplicationService service;
    /** Descripción: Inicializa una instancia de CanonicalOperationBehaviorController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalOperationBehaviorController(OperationBehaviorModelApplicationService service) { this.service = service; }

    @GetMapping("/operation-behaviors") public List<OperationBehaviorModelSummary> list() { return service.findAll(); }
    @PostMapping("/{metamodelId}/operation-behaviors") public ResponseEntity<OperationBehaviorModelSaveResponse> create(@PathVariable String metamodelId, @RequestBody OperationBehaviorModel model) {
        OperationBehaviorModelSaveResponse response = service.create(metamodelId, model);
        return ResponseEntity.created(URI.create("/api/canonical-metamodels/" + metamodelId + "/operation-behaviors")).header(HttpHeaders.ETAG, response.etag()).body(response);
    }
    @GetMapping("/{metamodelId}/operation-behaviors") public ResponseEntity<OperationBehaviorModel> find(@PathVariable String metamodelId) {
        OperationBehaviorModelLoadResult result = service.find(metamodelId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result.behaviorModel());
    }
    @PutMapping("/{metamodelId}/operation-behaviors") public ResponseEntity<OperationBehaviorModelSaveResponse> replace(@PathVariable String metamodelId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag, @RequestBody OperationBehaviorModel model) {
        OperationBehaviorModelSaveResponse response = service.replace(metamodelId, model, expectedEtag);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(response);
    }
    @PostMapping("/{metamodelId}/operation-behaviors/check") public ValidationReport check(@PathVariable String metamodelId) { return service.check(metamodelId); }
    @GetMapping(value = "/{metamodelId}/operation-behaviors/export", produces = "application/json") public OperationBehaviorModel export(@PathVariable String metamodelId) { return service.find(metamodelId).behaviorModel(); }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @DeleteMapping("/{metamodelId}/operation-behaviors") public ResponseEntity<Void> delete(@PathVariable String metamodelId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag) { service.delete(metamodelId, expectedEtag); return ResponseEntity.status(HttpStatus.NO_CONTENT).build(); }
}
