package com.example.editor.performance;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON and HTML performance reports comparable across executions. */
@RestController
@RequestMapping("/api/performance/capabilities")
public class CapabilityPerformanceController {
    private final CapabilityPerformanceService service;
    /** Descripción: Inicializa una instancia de CapabilityPerformanceController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityPerformanceController(CapabilityPerformanceService service) { this.service = service; }
    @GetMapping("/budgets") public List<CapabilityPerformanceBudget> budgets() { return service.budgets(); }
    @PostMapping(value = "/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de CapabilityPerformanceController. Entradas: sample: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityPerformanceReport evaluate(@RequestBody CapabilityPerformanceSample sample) { return service.evaluate(sample); }
    @GetMapping("/reports") public List<CapabilityPerformanceReport> reports() { return service.reports(); }
    @GetMapping("/reports/compare")
    /** Descripción: Ejecuta la operación «compare» correspondiente a la responsabilidad de CapabilityPerformanceController. Entradas: baseline: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityPerformanceComparison compare(@org.springframework.web.bind.annotation.RequestParam String baseline,
            @org.springframework.web.bind.annotation.RequestParam String candidate) { return service.compare(baseline, candidate); }
    @GetMapping("/reports/{reportId}") public CapabilityPerformanceReport report(@PathVariable String reportId) { return service.find(reportId); }
    @GetMapping("/reports/{reportId}.html")
    /** Descripción: Ejecuta la operación «html» correspondiente a la responsabilidad de CapabilityPerformanceController. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> html(@PathVariable String reportId) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + reportId.replaceAll("[^A-Za-z0-9._-]", "-") + ".html\"")
                .contentType(MediaType.TEXT_HTML).body(service.html(reportId));
    }
}
