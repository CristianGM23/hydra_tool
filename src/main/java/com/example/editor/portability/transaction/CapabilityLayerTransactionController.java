package com.example.editor.portability.transaction;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for portable, guarded multi-layer changes. */
@RestController
@RequestMapping("/api/capability-packages/transactions")
public class CapabilityLayerTransactionController {
    private final CapabilityLayerTransactionService service;
    /** Descripción: Inicializa una instancia de CapabilityLayerTransactionController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityLayerTransactionController(CapabilityLayerTransactionService service){this.service = service;}
    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de CapabilityLayerTransactionController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityLayerTransactionPreview preview(@RequestBody CapabilityLayerTransactionRequest request){return service.preview(request);}
    @PostMapping(value = "/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Actualiza el estado gestionado por CapabilityLayerTransactionController mediante la operación «apply». Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityLayerTransactionResult apply(@RequestBody CapabilityLayerTransactionRequest request){return service.apply(request);}
    @PostMapping(value = "/rollback", consumes = MediaType.APPLICATION_JSON_VALUE)
    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de CapabilityLayerTransactionController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CapabilityLayerRollbackResult rollback(@RequestBody CapabilityLayerRollbackRequest request){return service.rollback(request);}
}
