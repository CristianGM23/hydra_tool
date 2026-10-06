package com.example.editor.metamodel.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.metamodel.projection.ProjectionCoordinator;
import com.example.editor.metamodel.projection.ProjectionManifest;
import com.example.editor.metamodel.projection.ProjectionResult;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.target.EcoreProjectionConfiguration;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

/**
 * Expone la configuración y regeneración de proyecciones sin contaminar la API semántica.
 */
@RestController
@RequestMapping("/api/canonical-metamodels/{id}")
public class CanonicalMetamodelProjectionController {

    private final ProjectionCoordinator coordinator;
    private final MetamodelRegistryService runtimeRegistry;

    /** Descripción: Inicializa una instancia de CanonicalMetamodelProjectionController con sus dependencias y datos de entrada. Entradas: coordinator: dato de entrada necesario para la operación; runtimeRegistry: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CanonicalMetamodelProjectionController(ProjectionCoordinator coordinator, MetamodelRegistryService runtimeRegistry) {
        this.coordinator = coordinator;
        this.runtimeRegistry = runtimeRegistry;
    }

    @GetMapping("/targets")
    /** Descripción: Ejecuta la operación «targets» correspondiente a la responsabilidad de CanonicalMetamodelProjectionController. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public TargetConfigurationDocument targets(@PathVariable("id") String id) {
        return coordinator.findTargets(id);
    }

    @PutMapping("/targets/ecore")
    /** Descripción: Actualiza el estado gestionado por CanonicalMetamodelProjectionController mediante la operación «updateEcore». Entradas: id: identificador que delimita el recurso objetivo; configuration: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public TargetConfigurationDocument updateEcore(@PathVariable("id") String id, @RequestBody EcoreProjectionConfiguration configuration) {
        return coordinator.saveEcoreConfiguration(id, configuration);
    }

    @GetMapping("/projections")
    /** Descripción: Ejecuta la operación «projections» correspondiente a la responsabilidad de CanonicalMetamodelProjectionController. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectionManifest projections(@PathVariable("id") String id) {
        return coordinator.findManifest(id);
    }

    @PostMapping("/projections/ecore")
    /** Descripción: Ejecuta la operación «regenerateEcore» correspondiente a la responsabilidad de CanonicalMetamodelProjectionController. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectionResult regenerateEcore(@PathVariable("id") String id) {
        ProjectionResult result = coordinator.regenerateEcore(id);
        runtimeRegistry.initialize();
        return result;
    }
}
