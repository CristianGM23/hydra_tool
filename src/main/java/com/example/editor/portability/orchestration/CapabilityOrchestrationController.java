package com.example.editor.portability.orchestration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for AR multiorigin orchestration and deterministic replay. */
@RestController
@RequestMapping("/api/capability-packages/orchestration")
public class CapabilityOrchestrationController {
    private final CapabilityOrchestrationService service;
    /** Descripción: Inicializa una instancia de CapabilityOrchestrationController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityOrchestrationController(CapabilityOrchestrationService service){this.service = service;}
    @PostMapping("/preview") public ResponseEntity<CapabilityOrchestrationPreview> preview(@RequestBody CapabilityOrchestrationRequest request){return ResponseEntity.ok(service.preview(request));}
    @PostMapping("/apply") public ResponseEntity<CapabilityOrchestrationResult> apply(@RequestBody CapabilityOrchestrationRequest request){return ResponseEntity.ok(service.apply(request));}
    @PostMapping("/{orchestrationId}/replay") public ResponseEntity<CapabilityOrchestrationPreview> replay(@PathVariable String orchestrationId, @RequestBody CapabilityOrchestrationRequest request){return ResponseEntity.ok(service.replay(request, orchestrationId));}
    @GetMapping("/{orchestrationId}") public ResponseEntity<CapabilityOrchestrationPreview> find(@PathVariable String orchestrationId){return ResponseEntity.ok(service.find(orchestrationId));}
}
