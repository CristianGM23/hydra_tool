package com.example.editor.gamification.runtime;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import com.example.editor.gamification.feedback.UserFeedbackEnvelope;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.event.CanonicalEvent;

@RestController
@RequestMapping("/api/canonical-metamodels/{metamodelId}/gamification/runtime")
public class GamificationRuntimeController {
    private final GamificationRuntimeService service;
    /** Descripción: Inicializa una instancia de GamificationRuntimeController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GamificationRuntimeController(GamificationRuntimeService service) { this.service = service; }
    @PostMapping("/events") public GamificationProcessingResult process(@PathVariable String metamodelId, @RequestBody CanonicalEvent event) { return service.process(metamodelId, event); }
    /** Descripción: Ejecuta la operación «state» correspondiente a la responsabilidad de GamificationRuntimeController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public GamificationRuntimeSnapshot state(@PathVariable String metamodelId) { return service.state(metamodelId); }
    @GetMapping("/feedback") public List<UserFeedbackEnvelope> feedback(@PathVariable String metamodelId,
            @RequestParam(defaultValue = "") String actorId, @RequestParam(defaultValue = "100") int limit) {
        return service.feedback(metamodelId, actorId, limit);
    }
    /** Descripción: Ejecuta la operación «reset» correspondiente a la responsabilidad de GamificationRuntimeController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @DeleteMapping public void reset(@PathVariable String metamodelId) { service.reset(metamodelId); }
}
