package com.example.editor.workbench.capability;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only capability inventory used by the modular application shell. */
@RestController
@RequestMapping("/api/hydra/capabilities")
public class HydraCapabilityReadinessController {
    private final HydraCapabilityReadinessService service;
    /** Descripción: Inicializa el controlador de disponibilidad de capacidades. Entradas: service: servicio que calcula el inventario de capacidades. Salida: deja el controlador listo para atender consultas. */
    /** Descripción: Inicializa una instancia de HydraCapabilityReadinessController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraCapabilityReadinessController(HydraCapabilityReadinessService service){this.service = service;}

    @GetMapping("/metamodel/{metamodelId}")
    /** Descripción: Resume las capacidades asociadas a un metamodelo. Entradas: metamodelId: identificador del metamodelo. Salida: devuelve el estado agregado de sus capas. */
    /** Descripción: Ejecuta la operación «summarizeMetamodel» correspondiente a la responsabilidad de HydraCapabilityReadinessController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraCapabilityReadiness summarizeMetamodel(@PathVariable String metamodelId){return service.summarizeMetamodel(metamodelId);}

    @GetMapping("/grammar/{grammarId}")
    /** Descripción: Resume las capacidades asociadas a una gramática. Entradas: grammarId: identificador de la gramática. Salida: devuelve el estado agregado de sus capas. */
    /** Descripción: Ejecuta la operación «summarizeGrammar» correspondiente a la responsabilidad de HydraCapabilityReadinessController. Entradas: grammarId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraCapabilityReadiness summarizeGrammar(@PathVariable String grammarId){return service.summarizeGrammar(grammarId);}
}
