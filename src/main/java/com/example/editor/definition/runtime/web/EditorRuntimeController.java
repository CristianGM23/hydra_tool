package com.example.editor.definition.runtime.web;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.definition.runtime.EditorDefinitionCompilationResult;
import com.example.editor.definition.runtime.EditorRuntimeApplicationService;
import com.example.editor.definition.runtime.EditorRuntimeCacheMetrics;

/** API del compilador y caché runtime de editores configurables. */
@RestController
@RequestMapping("/api/canonical-metamodels/{metamodelId}/editors/{editorId}/runtime")
public class EditorRuntimeController {
    private final EditorRuntimeApplicationService service;

    /** Descripción: Inicializa una instancia de EditorRuntimeController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorRuntimeController(EditorRuntimeApplicationService service) {
        this.service = service;
    }

    @GetMapping
    /** Descripción: Obtiene la información correspondiente a «find» dentro de EditorRuntimeController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EditorDefinitionCompilationResult find(@PathVariable String metamodelId,
            @PathVariable String editorId) {
        return service.find(metamodelId, editorId);
    }

    @PostMapping("/compile")
    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de EditorRuntimeController. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EditorDefinitionCompilationResult compile(@PathVariable String metamodelId,
            @PathVariable String editorId) {
        return service.compile(metamodelId, editorId, true);
    }

    @DeleteMapping("/cache")
    /** Descripción: Elimina o libera el recurso gestionado por «invalidate» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void invalidate(@PathVariable String metamodelId, @PathVariable String editorId) {
        service.invalidate(metamodelId, editorId);
    }

    @GetMapping("/cache/metrics")
    /** Descripción: Ejecuta la operación «metrics» correspondiente a la responsabilidad de EditorRuntimeController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public EditorRuntimeCacheMetrics metrics() {
        return service.metrics();
    }
}
