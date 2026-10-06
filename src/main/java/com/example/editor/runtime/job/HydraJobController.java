package com.example.editor.runtime.job;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read/cancel API for long-running jobs registered across HYDRA layers. */
@RestController
@RequestMapping("/api/jobs")
public class HydraJobController {
    private final HydraJobOrchestrator jobs;
    /** Descripción: Inicializa una instancia de HydraJobController con sus dependencias y datos de entrada. Entradas: jobs: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraJobController(HydraJobOrchestrator jobs) { this.jobs = jobs; }
    /** Descripción: Obtiene la información correspondiente a «list» dentro de HydraJobController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public List<HydraJobSnapshot> list() { return jobs.list(); }
    @GetMapping("/{jobId}") public HydraJobSnapshot find(@PathVariable String jobId) { return jobs.find(jobId); }
    @PostMapping("/{jobId}/cancel") public HydraJobSnapshot cancel(@PathVariable String jobId) { return jobs.cancel(jobId); }
}
