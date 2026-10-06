package com.example.editor.release;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.standalone.publication.PublicationResult;

/** Verified distribution publication endpoint. */
@RestController
@RequestMapping("/api/publications/verified")
public class VerifiedDistributionPublicationController {
    private final VerifiedDistributionPublicationService service;
    /** Descripción: Inicializa una instancia de VerifiedDistributionPublicationController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public VerifiedDistributionPublicationController(VerifiedDistributionPublicationService service){this.service = service;}
    @PostMapping("/publish") public PublicationResult publish(@RequestBody VerifiedPublicationRequest request){return service.publish(request);}
}
