package com.example.editor.metamodel.layout;

import java.util.List;

import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;

/**
 * Devuelve el diagrama organizado al frontend.
 *
 * @param diagram contiene el resultado.
 * @param warnings contiene avisos.
 * @param statistics contiene métricas.
 */
public record MetamodelLayoutResponse(MetamodelDiagramDocument diagram, List<TransformationWarning> warnings, MetamodelLayoutStatistics statistics) {
}
