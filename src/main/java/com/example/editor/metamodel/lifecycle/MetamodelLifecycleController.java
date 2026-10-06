package com.example.editor.metamodel.lifecycle;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Expone el ciclo de vida de la fuente canónica. */
@RestController
@RequestMapping("/api/canonical-metamodels/{metamodelId}")
public class MetamodelLifecycleController {

    private final MetamodelLifecycleService service;

    /** Descripción: Inicializa una instancia de MetamodelLifecycleController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelLifecycleController(MetamodelLifecycleService service) {
        this.service = service;
    }

    @GetMapping("/deletion-impact")
    /** Descripción: Ejecuta la operación «analyzeDeletion» correspondiente a la responsabilidad de MetamodelLifecycleController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDeletionImpact analyzeDeletion(@PathVariable String metamodelId) {
        return service.analyzeDeletion(metamodelId);
    }

    @PostMapping("/deletion-recovery")
    /** Descripción: Ejecuta la operación «retryPendingCleanup» correspondiente a la responsabilidad de MetamodelLifecycleController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDeletionResult retryPendingCleanup(@PathVariable String metamodelId) {
        return service.retryPendingCleanup(metamodelId);
    }

    @DeleteMapping("/lifecycle")
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; deleteNotations: dato de entrada necesario para la operación; deleteExports: dato de entrada necesario para la operación; expectedEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDeletionResult delete(@PathVariable String metamodelId,
            @RequestParam(defaultValue = "true") boolean deleteNotations,
            @RequestParam(defaultValue = "true") boolean deleteExports,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String expectedEtag) {
        return service.delete(metamodelId, deleteNotations, deleteExports, expectedEtag);
    }
}
