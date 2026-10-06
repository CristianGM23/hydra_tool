package com.example.editor.validation.quickfix.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.validation.orchestration.LiveValidationRequest;
import com.example.editor.validation.orchestration.OrchestratedValidationResult;
import com.example.editor.validation.orchestration.ValidationOrchestrator;
import com.example.editor.validation.quickfix.QuickFixApplicationResult;
import com.example.editor.validation.quickfix.QuickFixDescriptor;
import com.example.editor.validation.quickfix.QuickFixExecutionRequest;
import com.example.editor.validation.quickfix.QuickFixPreviewResult;
import com.example.editor.validation.quickfix.QuickFixRuntimeException;
import com.example.editor.validation.quickfix.QuickFixRuntimeService;
import com.example.editor.validation.runtime.ModelValidationReport;

/** API de validación, problemas y Quick Fixes sobre modelos abiertos. */
@RestController
@RequestMapping("/api/models/{modelId}/validation-runtime")
public class ModelValidationRuntimeController {
    private final ValidationOrchestrator orchestrator;
    private final QuickFixRuntimeService quickFixes;

    /** Descripción: Inicializa una instancia de ModelValidationRuntimeController con sus dependencias y datos de entrada. Entradas: orchestrator: dato de entrada necesario para la operación; quickFixes: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ModelValidationRuntimeController(ValidationOrchestrator orchestrator, QuickFixRuntimeService quickFixes) {
        this.orchestrator = orchestrator;
        this.quickFixes = quickFixes;
    }

    @PostMapping("/validate")
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: modelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public OrchestratedValidationResult validate(@PathVariable String modelId, @RequestBody(required = false) LiveValidationRequest request) {
        return orchestrator.validateLive(modelId, request == null ? LiveValidationRequest.full() : request);
    }

    @GetMapping("/latest")
    /** Descripción: Ejecuta la operación «latest» correspondiente a la responsabilidad de ModelValidationRuntimeController. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ModelValidationReport latest(@PathVariable String modelId) {
        return orchestrator.latest(modelId).orElseThrow(() -> new QuickFixRuntimeException(QuickFixRuntimeException.Reason.NOT_FOUND,
                "No validation report is available for model: " + modelId));
    }

    @GetMapping("/problems/{diagnosticId}/quick-fixes")
    /** Descripción: Ejecuta la operación «quickFixes» correspondiente a la responsabilidad de ModelValidationRuntimeController. Entradas: modelId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<QuickFixDescriptor> quickFixes(@PathVariable String modelId, @PathVariable String diagnosticId) {
        return quickFixes.resolve(modelId, diagnosticId);
    }

    @PostMapping("/problems/{diagnosticId}/quick-fixes/{actionId}/preview")
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de ModelValidationRuntimeController. Entradas: modelId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public QuickFixPreviewResult preview(@PathVariable String modelId, @PathVariable String diagnosticId,
            @PathVariable String actionId, @RequestBody(required = false) QuickFixExecutionRequest request) {
        return quickFixes.preview(modelId, diagnosticId, actionId, request == null ? new QuickFixExecutionRequest(null, null) : request);
    }

    @PostMapping("/problems/{diagnosticId}/quick-fixes/{actionId}/apply")
    /** Descripción: Actualiza el estado gestionado por ModelValidationRuntimeController mediante la operación «apply». Entradas: modelId: identificador que delimita el recurso objetivo; diagnosticId: identificador que delimita el recurso objetivo; actionId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public QuickFixApplicationResult apply(@PathVariable String modelId, @PathVariable String diagnosticId,
            @PathVariable String actionId, @RequestBody(required = false) QuickFixExecutionRequest request) {
        return quickFixes.apply(modelId, diagnosticId, actionId, request == null ? new QuickFixExecutionRequest(null, null) : request);
    }

    @PostMapping("/quick-fixes/undo")
    /** Descripción: Ejecuta la operación «undo» correspondiente a la responsabilidad de ModelValidationRuntimeController. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public QuickFixApplicationResult undo(@PathVariable String modelId) { return quickFixes.undo(modelId); }

    @PostMapping("/quick-fixes/redo")
    /** Descripción: Ejecuta la operación «redo» correspondiente a la responsabilidad de ModelValidationRuntimeController. Entradas: modelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public QuickFixApplicationResult redo(@PathVariable String modelId) { return quickFixes.redo(modelId); }
}
