package com.example.editor.metamodel.layout.routing;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.layout.MetamodelLayoutOptions;

/**
 * Ejecuta de forma desacoplada puertos, rutas y etiquetas.
 */
@Service
public class MetamodelRoutingPipeline {

    private final MetamodelPortAssignmentEngineRegistry portRegistry;
    private final MetamodelEdgeRoutingEngineRegistry routingRegistry;
    private final MetamodelEdgeLabelPlacementEngineRegistry labelRegistry;

    /** Descripción: Inicializa una instancia de MetamodelRoutingPipeline con sus dependencias y datos de entrada. Entradas: portRegistry: dato de entrada necesario para la operación; routingRegistry: dato de entrada necesario para la operación; labelRegistry: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelRoutingPipeline(MetamodelPortAssignmentEngineRegistry portRegistry, MetamodelEdgeRoutingEngineRegistry routingRegistry, MetamodelEdgeLabelPlacementEngineRegistry labelRegistry) {
        this.portRegistry = portRegistry;
        this.routingRegistry = routingRegistry;
        this.labelRegistry = labelRegistry;
    }

    /** Descripción: Ejecuta la operación «route» correspondiente a la responsabilidad de MetamodelRoutingPipeline. Entradas: metamodel: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDiagramDocument route(MetaModel metamodel, MetamodelDiagramDocument diagram, MetamodelLayoutOptions options) {
        MetamodelDiagramDocument withPorts = portRegistry.require(options.portAssignmentAlgorithm()).assign(metamodel, diagram, options);
        MetamodelDiagramDocument withRoutes = routingRegistry.require(options.edgeRoutingAlgorithm()).route(metamodel, withPorts, options);
        return labelRegistry.require(options.labelPlacementAlgorithm()).place(metamodel, withRoutes, options);
    }
}
