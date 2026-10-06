package com.example.editor.validation.action.web;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionModelApplicationException;
import com.example.editor.validation.action.ActionModelApplicationService;
import com.example.editor.validation.action.ActionModelCheckReport;
import com.example.editor.validation.action.ActionModelLoadResult;
import com.example.editor.validation.action.ActionModelSaveResponse;
import com.example.editor.validation.action.ActionModelSummary;

/** API canónica de ActionModel 1.0. */
@RestController
@RequestMapping("/api/canonical-metamodels")
public class CanonicalActionController {
    private final ActionModelApplicationService service;
    /** Descripción: Inicializa una instancia de CanonicalActionController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalActionController(ActionModelApplicationService service) { this.service = service; }

    @GetMapping("/actions")
    /** Descripción: Obtiene la información correspondiente a «list» dentro de CanonicalActionController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<ActionModelSummary> list() { return service.findAll(); }

    @PostMapping("/{metamodelId}/actions")
    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalActionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ActionModelSaveResponse> create(@PathVariable String metamodelId, @RequestBody ActionModel model) {
        ActionModelSaveResponse response = service.create(metamodelId, model);
        return ResponseEntity.created(URI.create("/api/canonical-metamodels/" + metamodelId + "/actions")).header(HttpHeaders.ETAG, response.etag()).body(response);
    }

    @GetMapping("/{metamodelId}/actions")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de CanonicalActionController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ActionModel> find(@PathVariable String metamodelId) {
        try {
            ActionModelLoadResult result = service.find(metamodelId);
            return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result.actionModel());
        } catch (ActionModelApplicationException exception) {
            if (exception.getReason() == ActionModelApplicationException.Reason.NOT_FOUND) return ResponseEntity.notFound().build();
            throw exception;
        }
    }

    @PutMapping("/{metamodelId}/actions")
    /** Descripción: Actualiza el estado gestionado por CanonicalActionController mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ActionModelSaveResponse> replace(@PathVariable String metamodelId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag, @RequestBody ActionModel model) {
        ActionModelSaveResponse response = service.replace(metamodelId, model, expectedEtag);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(response);
    }

    @PostMapping("/{metamodelId}/actions/check")
    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ActionModelCheckReport check(@PathVariable String metamodelId) { return service.check(metamodelId); }

    @DeleteMapping("/{metamodelId}/actions")
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<Void> delete(@PathVariable String metamodelId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag) {
        service.delete(metamodelId, expectedEtag);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
