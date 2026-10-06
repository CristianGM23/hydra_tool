package com.example.editor.metamodel.application;

import java.util.List;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.projection.ProjectionResult;
import com.example.editor.metamodel.validation.ValidationReport;

/**
 * Devuelve el canónico persistido y el resultado independiente de sus proyecciones.
 */
public record MetamodelSaveResponse(MetaModel metamodel, ValidationReport validation, List<ProjectionResult> projections, String etag) {

    public MetamodelSaveResponse {
        projections = projections == null ? List.of() : List.copyOf(projections);
    }

    /** Compatibilidad con las pruebas de los bloques 1 y 2. */
    /** Descripción: Inicializa una instancia de MetamodelSaveResponse con sus dependencias y datos de entrada. Entradas: metamodel: dato de entrada necesario para la operación; validation: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelSaveResponse(MetaModel metamodel, ValidationReport validation, String etag) {
        this(metamodel, validation, List.of(), etag);
    }
}
