package com.example.editor.metamodel.layout.routing;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.LabelPositionDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign;
import com.example.editor.metamodel.layout.MetamodelLayoutOptions;
import com.example.editor.metamodel.layout.MetamodelLayoutProperties;

/**
 * Coloca cada etiqueta en el segmento más largo de su ruta.
 */
@Component
public class LongestSegmentMetamodelEdgeLabelPlacementEngine implements MetamodelEdgeLabelPlacementEngine {

    private final MetamodelLayoutProperties properties;

    /** Descripción: Inicializa una instancia de LongestSegmentMetamodelEdgeLabelPlacementEngine con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public LongestSegmentMetamodelEdgeLabelPlacementEngine(MetamodelLayoutProperties properties) {
        this.properties = properties;
    }

    @Override
    /** Descripción: Ejecuta la operación «id» correspondiente a la responsabilidad de LongestSegmentMetamodelEdgeLabelPlacementEngine. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String id() {
        return "longest-segment";
    }

    @Override
    /** Descripción: Ejecuta la operación «place» correspondiente a la responsabilidad de LongestSegmentMetamodelEdgeLabelPlacementEngine. Entradas: metamodel: dato de entrada necesario para la operación; diagram: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelDiagramDocument place(MetaModel metamodel, MetamodelDiagramDocument diagram, MetamodelLayoutOptions options) {
        List<EdgeDesign> edges = new ArrayList<>();

        for(EdgeDesign edge : diagram.edges()) {
            LabelPositionDesign position = choosePosition(edge.routePoints());
            edges.add(edge.withLabelPosition(position));
        }

        return new MetamodelDiagramDocument(diagram.metamodelId(), diagram.nodes(), edges);
    }

    /** Descripción: Ejecuta la operación «choosePosition» correspondiente a la responsabilidad de LongestSegmentMetamodelEdgeLabelPlacementEngine. Entradas: route: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private LabelPositionDesign choosePosition(List<RoutePointDesign> route) {
        if(route == null || route.size() < 2) {
            return null;
        }

        int bestSegment = 0;
        double bestLength = -1;

        for(int index = 0; index < route.size() - 1; index += 1) {
            double length = MetamodelRoutingGeometry.segmentLength(route.get(index), route.get(index + 1));

            if(length > bestLength) {
                bestLength = length;
                bestSegment = index;
            }
        }

        RoutePointDesign first = route.get(bestSegment);
        RoutePointDesign second = route.get(bestSegment + 1);
        double x = (first.x() + second.x()) / 2;
        double y = (first.y() + second.y()) / 2;
        double clearance = Math.max(0, properties.getLabelClearance());

        if(MetamodelRoutingGeometry.approximately(first.y(), second.y())) {
            y -= clearance;
        } else {
            x += clearance;
        }

        return new LabelPositionDesign(round(x), round(y), bestSegment);
    }

    /** Descripción: Ejecuta la operación «round» correspondiente a la responsabilidad de LongestSegmentMetamodelEdgeLabelPlacementEngine. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
