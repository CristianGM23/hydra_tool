package com.example.editor.metamodel.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.metamodel.MetamodelDescriptor;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.registry.RegisteredMetamodel;

/** Expone únicamente las proyecciones disponibles para el runtime EMF. */
@RestController
@RequestMapping("/api/metamodels")
public class MetamodelController {

    private final MetamodelRegistryService registryService;

    /** Descripción: Inicializa una instancia de MetamodelController con sus dependencias y datos de entrada. Entradas: registryService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelController(MetamodelRegistryService registryService) {
        this.registryService = registryService;
    }

    @GetMapping
    /** Descripción: Obtiene la información correspondiente a «list» dentro de MetamodelController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<MetamodelSummary> list() {
        return registryService.list().stream().map(this::toSummary).toList();
    }

    @GetMapping("/{id}")
    /** Descripción: Obtiene la información correspondiente a «find» dentro de MetamodelController. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDescriptor find(@PathVariable String id) {
        return registryService.find(id).descriptor();
    }

    @GetMapping("/runtime-status")
    /** Descripción: Procesa la operación «runtimeStatuses» coordinando las responsabilidades de MetamodelController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<MetamodelRegistryService.RuntimeMetamodelStatus> runtimeStatuses() {
        return registryService.statuses();
    }

    @PostMapping("/reload")
    /** Descripción: Ejecuta la operación «reload» correspondiente a la responsabilidad de MetamodelController. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<MetamodelSummary> reload() {
        registryService.initialize();
        return list();
    }

    /** Descripción: Ejecuta la operación «toSummary» correspondiente a la responsabilidad de MetamodelController. Entradas: registered: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelSummary toSummary(RegisteredMetamodel registered) {
        return new MetamodelSummary(registered.id(), registered.fileName(), registered.descriptor().packages().size());
    }

    public record MetamodelSummary(String id, String fileName, int rootPackageCount) {
    }
}
