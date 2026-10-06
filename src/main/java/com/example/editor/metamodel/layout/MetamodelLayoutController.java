package com.example.editor.metamodel.layout;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone el layout automático al diseñador.
 */
@RestController
@RequestMapping("/api/metamodel-layouts")
public class MetamodelLayoutController {

    private final MetamodelDiagramLayoutService service;

    /** Descripción: Inicializa una instancia de MetamodelLayoutController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelLayoutController(MetamodelDiagramLayoutService service) {
        this.service = service;
    }

    @GetMapping("/algorithms")
    /** Descripción: Ejecuta la operación «algorithms» correspondiente a la responsabilidad de MetamodelLayoutController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> algorithms() {
        return service.algorithms();
    }

    @PostMapping
    /** Descripción: Ejecuta la operación «layout» correspondiente a la responsabilidad de MetamodelLayoutController. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelLayoutResponse layout(@RequestBody MetamodelLayoutRequest request) {
        return service.layout(request);
    }
}
