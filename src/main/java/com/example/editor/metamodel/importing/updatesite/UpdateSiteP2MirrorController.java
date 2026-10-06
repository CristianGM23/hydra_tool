package com.example.editor.metamodel.importing.updatesite;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for reproducible offline p2 mirrors. */
@RestController
@RequestMapping("/api/update-site-imports")
public class UpdateSiteP2MirrorController {
    private final UpdateSiteP2MirrorService service;
    /** Descripción: Inicializa una instancia de UpdateSiteP2MirrorController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteP2MirrorController(UpdateSiteP2MirrorService service){this.service = service;}
    @PostMapping("/remote/snapshots/{snapshotId}/mirror")
    /** Descripción: Crea o prepara el resultado de «create» dentro de UpdateSiteP2MirrorController. Entradas: snapshotId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteP2MirrorResult create(@PathVariable String snapshotId){return service.create(snapshotId);}
    @GetMapping("/mirrors/{mirrorId}")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de UpdateSiteP2MirrorController. Entradas: mirrorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteP2MirrorResult find(@PathVariable String mirrorId){return service.find(mirrorId);}
    @PostMapping("/mirrors/{mirrorId}/validate")
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: mirrorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public UpdateSiteP2MirrorValidation validate(@PathVariable String mirrorId){return service.validate(mirrorId);}
    @GetMapping("/mirrors/{mirrorId}/export")
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de UpdateSiteP2MirrorController. Entradas: mirrorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<byte[]> export(@PathVariable String mirrorId){
        byte[] content = service.export(mirrorId);
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename(mirrorId + ".p2-mirror.zip").build());
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
