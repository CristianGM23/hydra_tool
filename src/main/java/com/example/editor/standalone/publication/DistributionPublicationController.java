package com.example.editor.standalone.publication;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP API for validation, publication and update-channel resolution. */
@RestController
@RequestMapping("/api/publications")
public class DistributionPublicationController {
    private final DistributionPublicationService service;
    /** Descripción: Inicializa una instancia de DistributionPublicationController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public DistributionPublicationController(DistributionPublicationService service){this.service = service;}
    @PostMapping("/validate") public PublicationValidationReport validate(@RequestBody PublicationRequest request){
        return service.validate(request);
    }
    @PostMapping("/publish") public PublicationResult publish(@RequestBody PublicationRequest request){
        return service.publish(request);
    }
    @PostMapping("/check") public UpdateCheckResult check(@RequestBody UpdateCheckRequest request){
        return service.check(request);
    }
}
