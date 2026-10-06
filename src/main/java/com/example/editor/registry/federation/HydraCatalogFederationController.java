package com.example.editor.registry.federation;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BB local/manual federation API. */
@RestController
@RequestMapping("/api/distribution-registry/federation")
public class HydraCatalogFederationController {
    private final HydraCatalogFederationService service;
    /** Descripción: Inicializa una instancia de HydraCatalogFederationController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraCatalogFederationController(HydraCatalogFederationService service){this.service=service;}
    @GetMapping("/sources") public List<HydraFederatedCatalogSource> sources(){return service.sources();}
    @PostMapping("/sources") public HydraFederatedCatalogSource saveSource(@RequestBody HydraFederatedCatalogSource source){return service.saveSource(source);}
    @PostMapping("/synchronize") public HydraCatalogSyncReport synchronize(@RequestBody HydraCatalogSyncRequest request){return service.synchronize(request);}
    @GetMapping("/reports") public List<HydraCatalogSyncReport> reports(){return service.reports();}
    @GetMapping("/reports/{reportId}") public HydraCatalogSyncReport report(@PathVariable String reportId){return service.report(reportId);}
}
