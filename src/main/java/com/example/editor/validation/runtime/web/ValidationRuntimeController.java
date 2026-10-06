package com.example.editor.validation.runtime.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.validation.orchestration.OrchestratedValidationResult;
import com.example.editor.validation.orchestration.ValidationGate;
import com.example.editor.validation.orchestration.ValidationOrchestrator;
import com.example.editor.validation.runtime.ModelValidationReport;
import com.example.editor.validation.runtime.ValidationCompilationResult;
import com.example.editor.validation.runtime.ValidationExecutionRequest;
import com.example.editor.validation.runtime.ValidationRuntimeApplicationException;
import com.example.editor.validation.runtime.ValidationRuntimeApplicationService;

/** API del compilador y motor de validaciones canónicas. */
@RestController
@RequestMapping("/api/canonical-metamodels/{metamodelId}/validations/runtime")
public class ValidationRuntimeController {
    private final ValidationRuntimeApplicationService service;
    private final ValidationOrchestrator orchestrator;
    @Autowired
    /** Descripción: Inicializa una instancia de ValidationRuntimeController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación; orchestrator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationRuntimeController(ValidationRuntimeApplicationService service, ValidationOrchestrator orchestrator) { this.service = service; this.orchestrator = orchestrator; }

    /** Conserva el punto de construcción histórico para consumidores que solo usan compile, validate o Draw.io. */
    @Deprecated(forRemoval = false)
    /** Descripción: Inicializa una instancia de ValidationRuntimeController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ValidationRuntimeController(ValidationRuntimeApplicationService service) { this(service, null); }

    @PostMapping("/compile")
    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de ValidationRuntimeController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ValidationCompilationResult compile(@PathVariable String metamodelId) { return service.compile(metamodelId); }

    @PostMapping("/validate")
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: metamodelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ModelValidationReport validate(@PathVariable String metamodelId, @RequestBody ValidationExecutionRequest request) { return service.validate(metamodelId, request); }

    @PostMapping("/orchestrate")
    /** Descripción: Ejecuta la operación «orchestrate» correspondiente a la responsabilidad de ValidationRuntimeController. Entradas: metamodelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación; gate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public OrchestratedValidationResult orchestrate(@PathVariable String metamodelId, @RequestBody ValidationExecutionRequest request, @RequestParam(defaultValue = "NONE") ValidationGate gate) {
        if(orchestrator == null) throw new ValidationRuntimeApplicationException(ValidationRuntimeApplicationException.Reason.NOT_EXECUTABLE,
                "Validation orchestration is unavailable from the compatibility constructor");
        return orchestrator.validate(metamodelId, request, gate);
    }

    @GetMapping(value = "/drawio/definition", produces = MediaType.APPLICATION_XML_VALUE)
    /** Descripción: Ejecuta la operación «definitionDrawio» correspondiente a la responsabilidad de ValidationRuntimeController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<String> definitionDrawio(@PathVariable String metamodelId) { return drawio(service.exportDefinitionDrawio(metamodelId), metamodelId + "-validations.drawio"); }

    @GetMapping(value = "/drawio/plan", produces = MediaType.APPLICATION_XML_VALUE)
    /** Descripción: Ejecuta la operación «planDrawio» correspondiente a la responsabilidad de ValidationRuntimeController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<String> planDrawio(@PathVariable String metamodelId) { return drawio(service.exportPlanDrawio(metamodelId), metamodelId + "-validation-plan.drawio"); }

    @PostMapping(value = "/drawio/report", produces = MediaType.APPLICATION_XML_VALUE)
    /** Descripción: Ejecuta la operación «reportDrawio» correspondiente a la responsabilidad de ValidationRuntimeController. Entradas: metamodelId: identificador que delimita el recurso objetivo; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<String> reportDrawio(@PathVariable String metamodelId, @RequestBody ValidationExecutionRequest request) { return drawio(service.exportReportDrawio(metamodelId, request), metamodelId + "-validation-report.drawio"); }

    /** Descripción: Ejecuta la operación «drawio» correspondiente a la responsabilidad de ValidationRuntimeController. Entradas: content: valor de entrada que debe procesarse; fileName: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private ResponseEntity<String> drawio(String content, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
