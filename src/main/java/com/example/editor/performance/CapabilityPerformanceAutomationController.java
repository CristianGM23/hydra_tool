package com.example.editor.performance;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for AU repeatable suites and approved performance baselines. */
@RestController
@RequestMapping("/api/performance/capabilities/automation")
public class CapabilityPerformanceAutomationController {
    private final CapabilityPerformanceAutomationService service;
    /** Descripción: Inicializa una instancia de CapabilityPerformanceAutomationController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityPerformanceAutomationController(CapabilityPerformanceAutomationService service){this.service = service;}
    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de CapabilityPerformanceAutomationController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @PostMapping public ResponseEntity<CapabilityPerformanceAutomationReport> run(@RequestBody CapabilityPerformanceScenarioRequest request){return ResponseEntity.ok(service.run(request));}
    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de CapabilityPerformanceAutomationController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public ResponseEntity<List<CapabilityPerformanceAutomationReport>> reports(){return ResponseEntity.ok(service.reports());}
    @GetMapping("/{reportId}") public ResponseEntity<CapabilityPerformanceAutomationReport> find(@PathVariable String reportId){return ResponseEntity.ok(service.find(reportId));}
    @PostMapping("/{reportId}/approve") public ResponseEntity<CapabilityPerformanceBaseline> approve(@PathVariable String reportId){return ResponseEntity.ok(service.approve(reportId));}
    @GetMapping("/baselines/{capabilityId}/{scenarioId}") public ResponseEntity<CapabilityPerformanceBaseline> baseline(@PathVariable String capabilityId, @PathVariable String scenarioId){return ResponseEntity.ok(service.baseline(scenarioId, capabilityId));}
    @GetMapping(value="/{reportId}/html", produces=MediaType.TEXT_HTML_VALUE) public ResponseEntity<byte[]> html(@PathVariable String reportId){return ResponseEntity.ok(service.html(reportId));}
}
