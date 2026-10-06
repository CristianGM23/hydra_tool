package com.example.editor.release;

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
import org.springframework.web.bind.annotation.RestController;

/** REST API for current release and publication gates. */
@RestController
@RequestMapping("/api/release-gates")
public class HydraReleaseGateController {
    private final HydraReleaseGateService service;
    /** Descripción: Inicializa una instancia de HydraReleaseGateController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraReleaseGateController(HydraReleaseGateService service){this.service = service;}
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de HydraReleaseGateController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public HydraReleaseGateReport evaluate(@RequestBody HydraReleaseGateRequest request){return service.evaluate(request);}
    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de HydraReleaseGateController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public List<HydraReleaseGateReport> reports(){return service.reports();}
    @GetMapping("/{reportId}") public HydraReleaseGateReport find(@PathVariable String reportId){return service.find(reportId);}
    @GetMapping("/{reportId}/html") public ResponseEntity<byte[]> html(@PathVariable String reportId){HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.TEXT_HTML); headers.setContentDisposition(ContentDisposition.attachment().filename(reportId.replaceAll("[^A-Za-z0-9._-]", "-") + ".html").build()); return ResponseEntity.ok().headers(headers).body(service.html(reportId));}
}
