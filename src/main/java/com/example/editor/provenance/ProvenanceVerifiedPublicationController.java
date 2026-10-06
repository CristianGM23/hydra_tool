package com.example.editor.provenance;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.standalone.publication.PublicationResult;

/** AV-gated distribution publication endpoint. */
@RestController
@RequestMapping("/api/publications/provenance-verified")
public class ProvenanceVerifiedPublicationController {
    private final ProvenanceVerifiedPublicationService service;
    /** Descripción: Inicializa una instancia de ProvenanceVerifiedPublicationController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public ProvenanceVerifiedPublicationController(ProvenanceVerifiedPublicationService service){this.service = service;}
    @PostMapping("/publish") public ResponseEntity<PublicationResult> publish(@RequestBody ProvenanceVerifiedPublicationRequest request){return ResponseEntity.ok(service.publish(request));}
}
