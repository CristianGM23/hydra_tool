package com.example.editor.metamodel.importing;

import java.util.List;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;

/**
 * Agrupa el resultado neutral producido por un importador.
 *
 * @param metamodel contiene el metamodelo neutral.
 * @param diagram contiene el diagrama recuperado.
 * @param warnings contiene las adaptaciones realizadas.
 */
public record MetamodelImportOutput(MetaModel metamodel, MetamodelDiagramDocument diagram, List<TransformationWarning> warnings) {

    public MetamodelImportOutput {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
