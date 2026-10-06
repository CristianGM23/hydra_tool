package com.example.editor.portability.merge;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.portability.transaction.CapabilityLayerRollbackRequest;
import com.example.editor.portability.transaction.CapabilityLayerRollbackResult;

/** REST API for semantic capability package merging. */
@RestController
@RequestMapping("/api/capability-packages/semantic-merge")
public class CapabilitySemanticMergeController {
    private final CapabilitySemanticMergeService service;
    /** Descripción: Inicializa una instancia de CapabilitySemanticMergeController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilitySemanticMergeController(CapabilitySemanticMergeService service){this.service = service;}
    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de CapabilitySemanticMergeController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilitySemanticMergePreview preview(@RequestBody CapabilitySemanticMergeRequest request){return service.preview(request);}
    @PostMapping(value = "/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Actualiza el estado gestionado por CapabilitySemanticMergeController mediante la operación «apply». Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilitySemanticMergeResult apply(@RequestBody CapabilitySemanticMergeRequest request){return service.apply(request);}
    @PostMapping(value = "/rollback", consumes = MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de CapabilitySemanticMergeController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityLayerRollbackResult rollback(@RequestBody CapabilityLayerRollbackRequest request){return service.rollback(request);}
}
