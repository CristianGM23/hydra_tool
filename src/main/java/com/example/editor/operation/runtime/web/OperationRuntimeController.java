package com.example.editor.operation.runtime.web;

import org.springframework.web.bind.annotation.*;
import com.example.editor.operation.runtime.*;

/** API utilizada por botones, formularios, paleta y menús del editor de modelos. */
@RestController
public class OperationRuntimeController {
    private final OperationRuntimeService service;
    /** Descripción: Inicializa una instancia de OperationRuntimeController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public OperationRuntimeController(OperationRuntimeService service) { this.service = service; }
    @GetMapping("/api/canonical-metamodels/{metamodelId}/operation-runtime") public OperationRuntimeCatalog catalog(@PathVariable String metamodelId) { return service.catalog(metamodelId); }
    @PostMapping("/api/models/{modelId}/operations/{operationId}") public OperationInvocationResult invoke(@PathVariable String modelId, @PathVariable String operationId, @RequestBody OperationInvocationRequest request) { return service.invoke(modelId, operationId, request); }
    @PostMapping("/api/models/{modelId}/operations/undo") public OperationInvocationResult undo(@PathVariable String modelId) { return service.undo(modelId); }
}
