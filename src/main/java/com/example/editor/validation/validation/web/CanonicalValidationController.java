package com.example.editor.validation.validation.web;

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
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationModelApplicationException;
import com.example.editor.validation.validation.ValidationModelApplicationService;
import com.example.editor.validation.validation.ValidationModelCheckReport;
import com.example.editor.validation.validation.ValidationModelLoadResult;
import com.example.editor.validation.validation.ValidationModelSaveResponse;
import com.example.editor.validation.validation.ValidationModelSummary;

/** API canónica de ValidationModel 1.0. */
@RestController
@RequestMapping("/api/canonical-metamodels")
public class CanonicalValidationController {
    private final ValidationModelApplicationService service;
    /** Descripción: Inicializa una instancia de CanonicalValidationController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalValidationController(ValidationModelApplicationService service) { this.service = service; }

    @GetMapping("/validations")
    /** Descripción: Obtiene la información correspondiente a «list» dentro de CanonicalValidationController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<ValidationModelSummary> list() { return service.findAll(); }

    @PostMapping("/{metamodelId}/validations")
    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalValidationController. Entradas: metamodelId: identificador que delimita el recurso objetivo; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ValidationModelSaveResponse> create(@PathVariable String metamodelId, @RequestBody ValidationModel model) {
        ValidationModelSaveResponse response = service.create(metamodelId, model);
        return ResponseEntity.created(URI.create("/api/canonical-metamodels/" + metamodelId + "/validations")).header(HttpHeaders.ETAG, response.etag()).body(response);
    }

    @GetMapping("/{metamodelId}/validations")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de CanonicalValidationController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ValidationModel> find(@PathVariable String metamodelId) {
        try {
            ValidationModelLoadResult result = service.find(metamodelId);
            return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result.validationModel());
        } catch (ValidationModelApplicationException exception) {
            if (exception.getReason() == ValidationModelApplicationException.Reason.NOT_FOUND) return ResponseEntity.notFound().build();
            throw exception;
        }
    }

    @PutMapping("/{metamodelId}/validations")
    /** Descripción: Actualiza el estado gestionado por CanonicalValidationController mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ValidationModelSaveResponse> replace(@PathVariable String metamodelId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag, @RequestBody ValidationModel model) {
        ValidationModelSaveResponse response = service.replace(metamodelId, model, expectedEtag);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(response);
    }

    @PostMapping("/{metamodelId}/validations/check")
    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationModelCheckReport check(@PathVariable String metamodelId) { return service.check(metamodelId); }

    @DeleteMapping("/{metamodelId}/validations")
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<Void> delete(@PathVariable String metamodelId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag) {
        service.delete(metamodelId, expectedEtag);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
