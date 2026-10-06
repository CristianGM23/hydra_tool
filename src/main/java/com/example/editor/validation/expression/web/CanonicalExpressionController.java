package com.example.editor.validation.expression.web;

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

import com.example.editor.semantic.expression.ExpressionEvaluationResult;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.expression.ExpressionModelCheckReport;
import com.example.editor.validation.expression.ExpressionModelLoadResult;
import com.example.editor.validation.expression.ExpressionModelSaveResponse;
import com.example.editor.validation.expression.ExpressionModelSummary;
import com.example.editor.validation.model.CanonicalModelDocument;

/** API canónica del ExpressionModel 1.0. */
@RestController
@RequestMapping("/api/canonical-metamodels")
public class CanonicalExpressionController {

    private final ExpressionModelApplicationService service;

    /** Descripción: Inicializa una instancia de CanonicalExpressionController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalExpressionController(ExpressionModelApplicationService service) {
        this.service = service;
    }

    @GetMapping("/expressions")
    /** Descripción: Obtiene la información correspondiente a «list» dentro de CanonicalExpressionController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<ExpressionModelSummary> list() {
        return service.findAll();
    }

    @PostMapping("/{metamodelId}/expressions")
    /** Descripción: Crea o prepara el resultado de «create» dentro de CanonicalExpressionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; expressionModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ExpressionModelSaveResponse> create(@PathVariable String metamodelId, @RequestBody ExpressionModel expressionModel) {
        ExpressionModelSaveResponse response = service.create(metamodelId, expressionModel);
        return ResponseEntity.created(URI.create("/api/canonical-metamodels/" + metamodelId + "/expressions"))
                .header(HttpHeaders.ETAG, response.etag()).body(response);
    }

    @GetMapping("/{metamodelId}/expressions")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de CanonicalExpressionController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ExpressionModel> find(@PathVariable String metamodelId) {
        ExpressionModelLoadResult result = service.find(metamodelId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, result.etag()).body(result.expressionModel());
    }

    @PutMapping("/{metamodelId}/expressions")
    /** Descripción: Actualiza el estado gestionado por CanonicalExpressionController mediante la operación «replace». Entradas: metamodelId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación; expressionModel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<ExpressionModelSaveResponse> replace(@PathVariable String metamodelId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag, @RequestBody ExpressionModel expressionModel) {
        ExpressionModelSaveResponse response = service.replace(metamodelId, expressionModel, expectedEtag);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, response.etag()).body(response);
    }

    @PostMapping("/{metamodelId}/expressions/check")
    /** Descripción: Valida las condiciones necesarias para «check» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionModelCheckReport check(@PathVariable String metamodelId) {
        return service.check(metamodelId);
    }

    @PostMapping("/{metamodelId}/expressions/{expressionId}/evaluate")
    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de CanonicalExpressionController. Entradas: metamodelId: identificador que delimita el recurso objetivo; expressionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExpressionEvaluationResult evaluate(@PathVariable String metamodelId, @PathVariable String expressionId, @RequestBody EvaluateExpressionRequest request) {
        return service.evaluate(metamodelId, expressionId, request.model(), request.selfElementId());
    }

    @DeleteMapping("/{metamodelId}/expressions")
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<Void> delete(@PathVariable String metamodelId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag) {
        service.delete(metamodelId, expectedEtag);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    public record EvaluateExpressionRequest(CanonicalModelDocument model, String selfElementId) {
    }
}
