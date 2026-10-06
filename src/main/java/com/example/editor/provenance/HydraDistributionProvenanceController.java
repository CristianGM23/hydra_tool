package com.example.editor.provenance;

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

/** REST API for AV provenance inventory, SBOM and attestation. */
@RestController
@RequestMapping("/api/distribution-provenance")
public class HydraDistributionProvenanceController {
    private final HydraDistributionProvenanceService service;
    /** Descripción: Inicializa una instancia de HydraDistributionProvenanceController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraDistributionProvenanceController(HydraDistributionProvenanceService service){this.service = service;}
    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de HydraDistributionProvenanceController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @PostMapping public ResponseEntity<HydraDistributionProvenanceReport> evaluate(@RequestBody HydraDistributionProvenanceRequest request){return ResponseEntity.ok(service.evaluate(request));}
    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de HydraDistributionProvenanceController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public ResponseEntity<List<HydraDistributionProvenanceReport>> reports(){return ResponseEntity.ok(service.reports());}
    @GetMapping("/{reportId}") public ResponseEntity<HydraDistributionProvenanceReport> find(@PathVariable String reportId){return ResponseEntity.ok(service.find(reportId));}
    @GetMapping(value="/{reportId}/html", produces=MediaType.TEXT_HTML_VALUE) public ResponseEntity<byte[]> html(@PathVariable String reportId){return ResponseEntity.ok(service.html(reportId));}
    @GetMapping(value="/{reportId}/sbom", produces=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<byte[]> export(@PathVariable String reportId){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + reportId + ".hydra-sbom.json\"").body(service.export(reportId));}
}
