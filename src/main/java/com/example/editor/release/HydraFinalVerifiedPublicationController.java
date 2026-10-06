package com.example.editor.release;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.standalone.publication.PublicationResult;

/** Endpoint aditivo para el gate final unificado de HYDRA. */
@RestController
@RequestMapping("/api/publications/final-verified")
public class HydraFinalVerifiedPublicationController {
    private final HydraFinalVerifiedPublicationService service;

    /** Descripción: Inicializa una instancia de HydraFinalVerifiedPublicationController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraFinalVerifiedPublicationController(HydraFinalVerifiedPublicationService service) {
        this.service = service;
    }

    @PostMapping("/publish")
    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de HydraFinalVerifiedPublicationController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<PublicationResult> publish(@RequestBody HydraFinalVerifiedPublicationRequest request) {
        return ResponseEntity.ok(service.publish(request));
    }
}
