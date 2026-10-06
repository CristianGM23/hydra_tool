package com.example.editor.metamodel.importing.updatesite;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for AT generated fixtures, structural fuzzing and invariants. */
@RestController
@RequestMapping("/api/update-site-imports/extension-adapters/verification")
public class UpdateSiteAdapterVerificationController {
    private final UpdateSiteAdapterVerificationService service;
    /** Descripción: Inicializa una instancia de UpdateSiteAdapterVerificationController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteAdapterVerificationController(UpdateSiteAdapterVerificationService service){this.service = service;}
    /** Descripción: Valida las condiciones necesarias para «verify» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @PostMapping public ResponseEntity<UpdateSiteAdapterVerificationReport> verify(@RequestBody UpdateSiteAdapterVerificationRequest request){return ResponseEntity.ok(service.verify(request));}
    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public ResponseEntity<List<UpdateSiteAdapterVerificationReport>> reports(){return ResponseEntity.ok(service.reports());}
    @GetMapping("/{reportId}") public ResponseEntity<UpdateSiteAdapterVerificationReport> find(@PathVariable String reportId){return ResponseEntity.ok(service.find(reportId));}
    @GetMapping(value="/{reportId}/html", produces=MediaType.TEXT_HTML_VALUE) public ResponseEntity<byte[]> html(@PathVariable String reportId){return ResponseEntity.ok(service.html(reportId));}
}
