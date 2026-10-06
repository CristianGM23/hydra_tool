package com.example.editor.metamodel.importing.updatesite;

import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API for declarative adapter fixture and mutation testing. */
@RestController
@RequestMapping("/api/update-site-imports/extension-adapters/bench")
public class UpdateSiteAdapterBenchController {
    private final UpdateSiteAdapterBenchService service;
    /** Descripción: Inicializa una instancia de UpdateSiteAdapterBenchController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteAdapterBenchController(UpdateSiteAdapterBenchService service){this.service = service;}
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Procesa la operación «run» coordinando las responsabilidades de UpdateSiteAdapterBenchController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAdapterBenchReport run(@RequestBody UpdateSiteAdapterBenchRequest request){return service.run(request);}
    @GetMapping
    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de UpdateSiteAdapterBenchController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<UpdateSiteAdapterBenchReport> reports(){return service.reports();}
    @GetMapping("/compare")
    /** Descripción: Ejecuta la operación «compare» correspondiente a la responsabilidad de UpdateSiteAdapterBenchController. Entradas: baseline: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAdapterBenchComparison compare(@RequestParam String baseline, @RequestParam String candidate){return service.compare(baseline, candidate);}
    @GetMapping("/{reportId}")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de UpdateSiteAdapterBenchController. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteAdapterBenchReport find(@PathVariable String reportId){return service.find(reportId);}
    @GetMapping("/{reportId}/html")
    /** Descripción: Ejecuta la operación «html» correspondiente a la responsabilidad de UpdateSiteAdapterBenchController. Entradas: reportId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> html(@PathVariable String reportId){HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.TEXT_HTML); headers.setContentDisposition(ContentDisposition.attachment().filename(reportId.replaceAll("[^A-Za-z0-9._-]", "-") + ".html").build()); return ResponseEntity.ok().headers(headers).body(service.html(reportId));}
}
