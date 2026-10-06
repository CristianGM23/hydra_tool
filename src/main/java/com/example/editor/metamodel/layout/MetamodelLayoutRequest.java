package com.example.editor.metamodel.layout;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;

/** Describe una petición de layout sobre el núcleo canónico. */
public record MetamodelLayoutRequest(MetaModel metamodel, MetamodelDiagramDocument diagram, MetamodelLayoutOptions options) {
}
