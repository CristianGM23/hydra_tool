package com.example.editor.policy;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for AY data-only policies and revocations. */
@RestController
@RequestMapping("/api/policies")
public class HydraPolicyController {
    private final HydraPolicyService service;
    /** Descripción: Inicializa una instancia de HydraPolicyController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraPolicyController(HydraPolicyService service){this.service=service;}
    @GetMapping("/rules") public ResponseEntity<List<HydraPolicyRule>> rules(){return ResponseEntity.ok(service.rules());}
    @PostMapping("/rules") public ResponseEntity<HydraPolicyRule> saveRule(@RequestBody HydraPolicyRule rule){return ResponseEntity.ok(service.saveRule(rule));}
    @GetMapping("/revocations") public ResponseEntity<List<HydraRevocationEntry>> revocations(){return ResponseEntity.ok(service.revocations());}
    @PostMapping("/revocations") public ResponseEntity<HydraRevocationEntry> revoke(@RequestBody HydraRevocationEntry entry){return ResponseEntity.ok(service.revoke(entry));}
    @PostMapping("/evaluate") public ResponseEntity<HydraPolicyEvaluationReport> evaluate(@RequestBody HydraPolicyEvaluationRequest request){return ResponseEntity.ok(service.evaluate(request));}
}
