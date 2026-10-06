package com.example.editor.metamodel.design;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone la persistencia de los diagramas de metamodelos.
 */
@RestController
@RequestMapping("/api/canonical-metamodels/{metamodelId}/diagram")
public class MetamodelDiagramController {

    private final MetamodelDiagramStorageService service;

    /**
     * Descripción:
     * Crea el controlador del diagrama.
     *
     * Entrada:
     * @param service administra el almacenamiento.
     *
     * Salida:
     * Crea una instancia de MetamodelDiagramController.
     */
    /** Descripción: Inicializa una instancia de MetamodelDiagramController con sus dependencias y datos de entrada. Entradas: service: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelDiagramController(MetamodelDiagramStorageService service) {
        this.service = service;
    }

    /**
     * Descripción:
     * Carga la disposición del metamodelo.
     *
     * Entrada:
     * @param metamodelId identifica el metamodelo.
     *
     * Salida:
     * @return el diagrama disponible.
     */
    @GetMapping
    /** Descripción: Obtiene la información correspondiente a «load» dentro de MetamodelDiagramController. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDiagramDocument load(@PathVariable String metamodelId) {
        return service.load(metamodelId);
    }

    /**
     * Descripción:
     * Guarda la disposición del metamodelo.
     *
     * Entrada:
     * @param metamodelId identifica el metamodelo.
     * @param document contiene la disposición.
     *
     * Salida:
     * @return el diagrama guardado.
     */
    @PutMapping
    /** Descripción: Actualiza el estado gestionado por MetamodelDiagramController mediante la operación «save». Entradas: metamodelId: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDiagramDocument save(@PathVariable String metamodelId, @RequestBody MetamodelDiagramDocument document) {
        return service.save(metamodelId, document);
    }

    /**
     * Descripción:
     * Elimina la disposición guardada.
     *
     * Entrada:
     * @param metamodelId identifica el metamodelo.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    @DeleteMapping
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(@PathVariable String metamodelId) {
        service.delete(metamodelId);
    }
}
