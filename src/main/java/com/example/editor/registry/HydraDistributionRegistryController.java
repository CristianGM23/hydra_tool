package com.example.editor.registry;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API for AW local and federable distribution catalogs. */
@RestController
@RequestMapping("/api/distribution-registry")
public class HydraDistributionRegistryController {
    private final HydraDistributionRegistryService service;
    /** Descripción: Inicializa una instancia de HydraDistributionRegistryController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraDistributionRegistryController(HydraDistributionRegistryService service){this.service=service;}
    @PostMapping("/entries") public ResponseEntity<HydraRegistryEntry> register(@RequestParam String provenanceReportId){return ResponseEntity.ok(service.register(provenanceReportId));}
    @PostMapping("/search") public ResponseEntity<List<HydraRegistryEntry>> search(@RequestBody(required=false) HydraRegistryQuery query){return ResponseEntity.ok(service.search(query));}
    @GetMapping("/index") public ResponseEntity<HydraRegistryIndex> index(){return ResponseEntity.ok(service.exportIndex());}
    @PostMapping("/index/import") public ResponseEntity<HydraRegistryIndex> importIndex(@RequestBody HydraRegistryIndex index){return ResponseEntity.ok(service.importIndex(index));}
}
