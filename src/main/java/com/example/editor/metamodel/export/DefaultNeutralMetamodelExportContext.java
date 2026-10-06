package com.example.editor.metamodel.export;

import java.util.List;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

/** Implementa el contexto canónico e inmutable usado por todos los exportadores. */
public record DefaultNeutralMetamodelExportContext(MetaModel neutralMetamodel, MetamodelDiagramDocument diagram, TargetConfigurationDocument targets, List<TransformationWarning> warnings, MetamodelExportOptions options) implements NeutralMetamodelExportContext {
    public DefaultNeutralMetamodelExportContext {
        if(neutralMetamodel == null) {
            throw new IllegalArgumentException("Canonical metamodel must not be null");
        }
        targets = targets == null ? TargetConfigurationDocument.defaultsFor(neutralMetamodel) : targets.normalizedFor(neutralMetamodel);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        options = options == null ? MetamodelExportOptions.defaults() : options;
    }

    /** Descripción: Inicializa una instancia de DefaultNeutralMetamodelExportContext con sus dependencias y datos de entrada. Entradas: neutralMetamodel: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación; targets: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public DefaultNeutralMetamodelExportContext(MetaModel neutralMetamodel, MetamodelDiagramDocument diagram, TargetConfigurationDocument targets, List<TransformationWarning> warnings) {
        this(neutralMetamodel, diagram, targets, warnings, MetamodelExportOptions.defaults());
    }
}
