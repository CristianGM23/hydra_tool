package com.example.editor.metamodel.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.validation.MetamodelValidationAnalysis;
import com.example.editor.metamodel.validation.MetamodelValidationApplicationService;
import com.example.editor.metamodel.validation.MetamodelValidationGateStatus;

/** API dedicada al análisis exhaustivo y al gate previo a exportaciones derivadas. */
@RestController
@RequestMapping("/api/canonical-metamodels")
public class MetamodelValidationController {
    private final MetamodelValidationApplicationService service;
    /** Descripción: Inicializa una instancia de MetamodelValidationController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelValidationController(MetamodelValidationApplicationService service){this.service = service;}

    @PostMapping("/validation/analyze")
    /** Descripción: Ejecuta la operación «analyzeDraft» correspondiente a la responsabilidad de MetamodelValidationController. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelValidationAnalysis analyzeDraft(@RequestBody MetaModel metamodel){return service.analyzeDraft(metamodel);}

    @PostMapping("/{id}/validation/run")
    /** Descripción: Valida las condiciones necesarias para «validateStored» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelValidationAnalysis validateStored(@PathVariable("id") String id){return service.validateStored(id);}

    @GetMapping("/{id}/validation/status")
    /** Descripción: Ejecuta la operación «status» correspondiente a la responsabilidad de MetamodelValidationController. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelValidationGateStatus status(@PathVariable("id") String id){return service.status(id);}
}
