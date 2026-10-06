package com.example.editor.recovery.continuity;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** BE local continuity plan and RPO/RTO evidence API. */
@RestController
@RequestMapping("/api/recovery/continuity")
public class HydraContinuityController {
    private final HydraContinuityService service;
    /** Descripción: Inicializa una instancia de HydraContinuityController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraContinuityController(HydraContinuityService service){this.service=service;}
    @GetMapping("/plans") public List<HydraContinuityPlan> plans(){return service.plans();}
    @PostMapping("/plans") public HydraContinuityPlan save(@RequestBody HydraContinuityPlan plan){return service.save(plan);}
    @GetMapping("/plans/due") public List<HydraContinuityPlan> duePlans(){return service.duePlans();}
    @PostMapping("/runs") public HydraContinuityRunReport run(@RequestBody HydraContinuityRunRequest request){return service.run(request);}
    @GetMapping("/runs") public List<HydraContinuityRunReport> reports(){return service.reports();}
    @GetMapping("/compare") public HydraContinuityComparison compare(@RequestParam String baselineRunId,@RequestParam String candidateRunId){return service.compare(baselineRunId,candidateRunId);}
}
