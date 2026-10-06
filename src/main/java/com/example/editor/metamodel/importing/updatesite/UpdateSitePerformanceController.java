package com.example.editor.metamodel.importing.updatesite;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/** Operational cache metrics isolated from the import command surface. */
@RestController @RequestMapping("/api/update-site-imports/performance")
public class UpdateSitePerformanceController {
    private final UpdateSiteImportApplicationService service;
    /** Descripción: Inicializa una instancia de UpdateSitePerformanceController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSitePerformanceController(UpdateSiteImportApplicationService service){this.service=service;}
    @GetMapping("/cache") public UpdateSiteAnalysisCacheMetrics cacheMetrics(){return service.cacheMetrics();}
    @DeleteMapping("/cache") public void clearCache(){service.clearAnalysisCache();}
}
