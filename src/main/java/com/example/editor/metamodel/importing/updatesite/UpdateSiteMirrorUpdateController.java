package com.example.editor.metamodel.importing.updatesite;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for AS incremental mirrors and channel rollback. */
@RestController
@RequestMapping("/api/update-site-imports/mirror-updates")
public class UpdateSiteMirrorUpdateController {
    private final UpdateSiteMirrorUpdateService service;
    /** Descripción: Inicializa una instancia de UpdateSiteMirrorUpdateController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteMirrorUpdateController(UpdateSiteMirrorUpdateService service){this.service = service;}
    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ @PostMapping public ResponseEntity<UpdateSiteMirrorUpdateReport> evaluate(@RequestBody UpdateSiteMirrorUpdateRequest request){return ResponseEntity.ok(service.evaluate(request));}
    /** Descripción: Ejecuta la operación «reports» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @GetMapping public ResponseEntity<List<UpdateSiteMirrorUpdateReport>> reports(){return ResponseEntity.ok(service.reports());}
    @GetMapping("/{reportId}") public ResponseEntity<UpdateSiteMirrorUpdateReport> find(@PathVariable String reportId){return ResponseEntity.ok(service.find(reportId));}
    @GetMapping("/channels/{channel}") public ResponseEntity<UpdateSiteMirrorChannelState> channel(@PathVariable UpdateSiteMirrorChannel channel){return ResponseEntity.ok(service.channel(channel));}
    @PostMapping("/channels/{channel}/rollback") public ResponseEntity<UpdateSiteMirrorChannelState> rollback(@PathVariable UpdateSiteMirrorChannel channel){return ResponseEntity.ok(service.rollback(channel));}
}
