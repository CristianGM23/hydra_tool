package com.example.editor.integration.workflow;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API de solo lectura para conocer qué fases del delivery tecnológico están realmente disponibles para un perfil persistido. */
@RestController
@RequestMapping({
    "/api/canonical-metamodels/{semanticScopeId}/integrations/{profileId}/technology/readiness",
    "/api/semantic-contexts/{semanticScopeId}/integrations/{profileId}/technology/readiness"
})
public class TechnologyDeliveryReadinessController {
    private final TechnologyDeliveryReadinessService service;

    /** Descripción: Inicializa el controlador de readiness. Entradas: service: servicio de cálculo sin efectos externos. Salida: deja la API lista para consultas. */
    public TechnologyDeliveryReadinessController(TechnologyDeliveryReadinessService service){this.service = service;}

    @GetMapping
    /** Descripción: Obtiene la preparación real de entrega del perfil sin escribir archivos ni ejecutar toolchains externas. Entradas: semanticScopeId: ámbito semántico; profileId: perfil persistido. Salida: readiness accionable y fail-closed. */ public TechnologyDeliveryReadiness get(@PathVariable String semanticScopeId, @PathVariable String profileId){
        return service.readiness(semanticScopeId, profileId);
    }
}
