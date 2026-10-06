package com.example.editor.metamodel.importing.updatesite;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Paged read API for large update-site evidence and diagnostic collections. */
@RestController
@RequestMapping("/api/update-site-imports/analyses")
public class UpdateSiteAnalysisPagingController {
    private final UpdateSiteAnalysisPagingService service;
    /** Descripción: Inicializa una instancia de UpdateSiteAnalysisPagingController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteAnalysisPagingController(UpdateSiteAnalysisPagingService service){this.service = service;}
    @GetMapping("/{analysisId}/compact")
    /** Descripción: Ejecuta la operación «compact» correspondiente a la responsabilidad de UpdateSiteAnalysisPagingController. Entradas: analysisId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteCompactAnalysis compact(@PathVariable String analysisId){return service.compact(analysisId);}
    @GetMapping("/{analysisId}/evidence")
    /** Descripción: Ejecuta la operación «evidence» correspondiente a la responsabilidad de UpdateSiteAnalysisPagingController. Entradas: analysisId: identificador que delimita el recurso objetivo; page: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación; query: dato de entrada necesario para la operación; confidence: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAnalysisPage<UpdateSiteElementProvenance> evidence(@PathVariable String analysisId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "ALL") String confidence){return service.evidence(analysisId, page, size, query, confidence);}
    @GetMapping("/{analysisId}/logs")
    /** Descripción: Ejecuta la operación «logs» correspondiente a la responsabilidad de UpdateSiteAnalysisPagingController. Entradas: analysisId: identificador que delimita el recurso objetivo; page: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAnalysisPage<UpdateSiteImportLogEntry> logs(@PathVariable String analysisId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size){return service.logs(analysisId, page, size);}
    @GetMapping("/{analysisId}/files")
    /** Descripción: Ejecuta la operación «files» correspondiente a la responsabilidad de UpdateSiteAnalysisPagingController. Entradas: analysisId: identificador que delimita el recurso objetivo; page: dato de entrada necesario para la operación; size: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAnalysisPage<UpdateSiteFileOutcome> files(@PathVariable String analysisId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size){return service.files(analysisId, page, size);}
}
