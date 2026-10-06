package com.example.editor.metamodel.deletion;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metamodels")
public final class MetamodelDependentArtifactCleanupController {
    private final MetamodelDependentArtifactCleanupService cleanupService;

    /** Descripción: Inicializa una instancia de MetamodelDependentArtifactCleanupController con sus dependencias y datos de entrada. Entradas: cleanupService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelDependentArtifactCleanupController(MetamodelDependentArtifactCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @PostMapping("/{metamodelId}/dependent-artifacts:cleanup")
    /** Descripción: Ejecuta la operación «cleanup» correspondiente a la responsabilidad de MetamodelDependentArtifactCleanupController. Entradas: metamodelId: identificador que delimita el recurso objetivo; deleteNotations: dato de entrada necesario para la operación; deleteExports: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ResponseEntity<MetamodelDependentArtifactCleanupService.CleanupResult> cleanup(
            @PathVariable String metamodelId,
            @RequestParam(defaultValue = "true") boolean deleteNotations,
            @RequestParam(defaultValue = "true") boolean deleteExports) {
        return ResponseEntity.ok(cleanupService.cleanup(metamodelId, deleteNotations, deleteExports));
    }
}
