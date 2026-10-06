package com.example.editor.release;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Expone el preflight final previo a publicación verificada. */
@RestController
@RequestMapping("/api/release-gates")
public class HydraReleaseCandidateController {
    private final HydraReleaseCandidateService service;

    /** Descripción: Inicializa una instancia de HydraReleaseCandidateController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraReleaseCandidateController(HydraReleaseCandidateService service) {
        this.service = service;
    }

    @GetMapping("/{reportId}/candidate-readiness")
    /** Descripción: Evalúa la condición «candidateReadiness» sin modificar el estado externo. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public HydraReleaseCandidateReport candidateReadiness(@PathVariable String reportId) {
        return service.assess(reportId);
    }
}
