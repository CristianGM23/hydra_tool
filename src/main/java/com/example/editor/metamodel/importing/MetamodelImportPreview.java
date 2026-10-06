package com.example.editor.metamodel.importing;

import java.util.List;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

/** Devuelve el mismo MetaModel canónico usado por la vista previa y el guardado. */
public record MetamodelImportPreview(String detectedFormat, String supportLevel, MetaModel metamodel, TargetConfigurationDocument targets, MetamodelDiagramDocument diagram, List<TransformationWarning> warnings, MetamodelImportStatistics statistics) {
    public MetamodelImportPreview {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
