package com.example.editor.event.flow.runtime;

import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.event.CanonicalEvent;

/** API de compilación, despliegue y ejecución headless de flujos. */
@RestController
@RequestMapping("/api/canonical-metamodels/{metamodelId}/event-flows/runtime")
public class EventFlowRuntimeController {
    private final EventFlowRuntimeService service;
    /** Descripción: Inicializa una instancia de EventFlowRuntimeController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowRuntimeController(EventFlowRuntimeService service) { this.service = service; }
    @PostMapping("/compile") public ExecutableEventFlowPlan compile(@PathVariable String metamodelId) { return service.compile(metamodelId); }
    @PostMapping("/start") public EventFlowRuntimeStatus start(@PathVariable String metamodelId) { return service.start(metamodelId); }
    @PostMapping("/stop") public EventFlowRuntimeStatus stop(@PathVariable String metamodelId) { return service.stop(metamodelId); }
    @PostMapping("/refresh") public EventFlowRuntimeStatus refresh(@PathVariable String metamodelId) { return service.refresh(metamodelId); }
    @GetMapping("/status") public EventFlowRuntimeStatus status(@PathVariable String metamodelId) { return service.status(metamodelId); }
    @PostMapping("/events") public EventFlowProcessResult process(@PathVariable String metamodelId, @RequestBody CanonicalEvent event) { return service.process(metamodelId, event); }
    @PostMapping("/tick") public EventFlowProcessResult tick(@PathVariable String metamodelId, @RequestBody(required = false) EventFlowTickRequest request) {
        return service.tick(metamodelId, request == null ? Instant.now() : request.now());
    }
    @GetMapping("/traces") public List<FlowExecutionTrace> traces(@PathVariable String metamodelId) { return service.traces(metamodelId); }
    @GetMapping("/state") public EventFlowRuntimeSnapshot state(@PathVariable String metamodelId) { return service.snapshot(metamodelId); }
    @DeleteMapping("/state") public void clearState(@PathVariable String metamodelId) { service.clearState(metamodelId); }
}
