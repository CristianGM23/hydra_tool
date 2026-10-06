package com.example.editor.metamodel.layout;

import java.util.List;

import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;

/**
 * Contiene un diagrama reorganizado.
 *
 * @param diagram contiene el resultado visual.
 * @param warnings contiene adaptaciones realizadas.
 * @param statistics contiene métricas del layout.
 */
public record MetamodelLayoutResult(MetamodelDiagramDocument diagram, List<TransformationWarning> warnings, MetamodelLayoutStatistics statistics) {

    public MetamodelLayoutResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
