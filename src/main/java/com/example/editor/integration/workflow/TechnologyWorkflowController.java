package com.example.editor.integration.workflow;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.integration.IntegrationTechnology;

/** API de solo lectura para comparar la madurez real de los destinos tecnológicos. */
@RestController
@RequestMapping({
    "/api/canonical-metamodels/{semanticScopeId}/integrations/technologies/workflows",
    "/api/semantic-contexts/{semanticScopeId}/integrations/technologies/workflows"
})
public class TechnologyWorkflowController {
    private final TechnologyWorkflowService service;

    /** Descripción: Inicializa una instancia de TechnologyWorkflowController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TechnologyWorkflowController(TechnologyWorkflowService service){this.service=service;}

    @GetMapping
    /** Descripción: Obtiene la información correspondiente a «list» dentro de TechnologyWorkflowController. Entradas: semanticScopeId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<TechnologyWorkflowDescriptor> list(@PathVariable String semanticScopeId){return service.descriptors();}

    @GetMapping("/{technology}")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de TechnologyWorkflowController. Entradas: semanticScopeId: identificador que delimita el recurso objetivo; technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TechnologyWorkflowDescriptor find(@PathVariable String semanticScopeId, @PathVariable IntegrationTechnology technology){
        return service.descriptor(technology);
    }
}
