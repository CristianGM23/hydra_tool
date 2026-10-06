package com.example.editor.metamodel.export;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign;

/**
 * Genera una captura SVG neutral.
 */
@Component
public class SvgMetamodelExporter implements MetamodelTextExporter {

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de SvgMetamodelExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportFormat format() {
        return MetamodelExportFormat.SVG;
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de SvgMetamodelExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String export(NeutralMetamodelExportContext context) {
        Map<String, NodeDesign> nodes = layout(context);
        Map<String, EdgeDesign> edges = MetamodelExportSupport.indexEdges(context);
        DiagramExportBounds.Bounds bounds = DiagramExportBounds.calculate(nodes, edges);
        int scale = context.options().imageScale();
        StringBuilder result = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append((int) Math.ceil(bounds.width() * scale)).append("\" height=\"").append((int) Math.ceil(bounds.height() * scale)).append("\" viewBox=\"").append(bounds.x()).append(" ").append(bounds.y()).append(" ").append(bounds.width()).append(" ").append(bounds.height()).append("\">\n");
        appendDefinitions(result);

        if(!context.options().transparentBackground()) {
            result.append("  <rect x=\"").append(bounds.x()).append("\" y=\"").append(bounds.y()).append("\" width=\"").append(bounds.width()).append("\" height=\"").append(bounds.height()).append("\" fill=\"#ffffff\"/>\n");
        }

        for(MetaRelation relation : context.neutralMetamodel().relations()) {
            appendEdge(result, relation, nodes, edges.get(relation.id()));
        }

        for(MetaClass metaClass : context.neutralMetamodel().classes()) {
            appendClass(result, context, metaClass, nodes.get(metaClass.id()));
        }

        for(MetaEnum metaEnum : context.neutralMetamodel().enums()) {
            appendSpecial(result, "«enumeration»", metaEnum.name(), nodes.get(metaEnum.id()), "#f5f3ff", "#7c3aed");
        }

        for(MetaDataType dataType : context.neutralMetamodel().dataTypes()) {
            appendSpecial(result, "«datatype»", dataType.name(), nodes.get(dataType.id()), "#ecfeff", "#0891b2");
        }

        result.append("</svg>\n");
        return result.toString();
    }

    /** Descripción: Ejecuta la operación «layout» correspondiente a la responsabilidad de SvgMetamodelExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, NodeDesign> layout(NeutralMetamodelExportContext context) {
        Map<String, NodeDesign> stored = MetamodelExportSupport.indexNodes(context);
        Map<String, NodeDesign> result = new LinkedHashMap<>();
        int index = 0;

        for(MetaClass item : context.neutralMetamodel().classes()) {
            NodeDesign current = stored.getOrDefault(item.id(), new NodeDesign(item.id(), 60 + index % 3 * 300, 60 + index / 3 * 220, 240, 140));
            int relationRows = MetamodelExportSupport.structuralRelationsFrom(context, item.id()).size();
            double requiredHeight = 76 + (item.attributes().size() + relationRows + item.operations().size()) * 16;
            result.put(item.id(), new NodeDesign(current.elementId(), current.x(), current.y(), current.width(), Math.max(current.height(), requiredHeight), current.style()));
            index += 1;
        }

        for(MetaEnum item : context.neutralMetamodel().enums()) {
            result.put(item.id(), stored.getOrDefault(item.id(), new NodeDesign(item.id(), 60 + index % 3 * 300, 60 + index / 3 * 220, 220, 120)));
            index += 1;
        }

        for(MetaDataType item : context.neutralMetamodel().dataTypes()) {
            result.put(item.id(), stored.getOrDefault(item.id(), new NodeDesign(item.id(), 60 + index % 3 * 300, 60 + index / 3 * 220, 220, 100)));
            index += 1;
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «appendDefinitions» correspondiente a la responsabilidad de SvgMetamodelExporter. Entradas: result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendDefinitions(StringBuilder result) {
        result.append("  <defs>\n");
        result.append("    <marker id=\"arrow\" markerWidth=\"10\" markerHeight=\"10\" refX=\"9\" refY=\"5\" orient=\"auto\"><path d=\"M0,0 L10,5 L0,10\" fill=\"none\" stroke=\"context-stroke\"/></marker>\n");
        result.append("    <marker id=\"triangle\" markerWidth=\"12\" markerHeight=\"12\" refX=\"11\" refY=\"6\" orient=\"auto\"><path d=\"M1,1 L11,6 L1,11 z\" fill=\"#ffffff\" stroke=\"context-stroke\"/></marker>\n");
        result.append("    <marker id=\"diamond-filled\" markerWidth=\"12\" markerHeight=\"12\" refX=\"1\" refY=\"6\" orient=\"auto\"><path d=\"M1,6 L6,1 L11,6 L6,11 z\" fill=\"context-stroke\"/></marker>\n");
        result.append("    <marker id=\"diamond-open\" markerWidth=\"12\" markerHeight=\"12\" refX=\"1\" refY=\"6\" orient=\"auto\"><path d=\"M1,6 L6,1 L11,6 L6,11 z\" fill=\"#ffffff\" stroke=\"context-stroke\"/></marker>\n");
        result.append("  </defs>\n");
    }

    /** Descripción: Ejecuta la operación «appendEdge» correspondiente a la responsabilidad de SvgMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; relation: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación; stored: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendEdge(StringBuilder result, MetaRelation relation, Map<String, NodeDesign> nodes, EdgeDesign stored) {
        NodeDesign source = nodes.get(relation.sourceTypeId());
        NodeDesign target = nodes.get(relation.targetTypeId());

        if(source == null || target == null) {
            return;
        }

        java.util.List<RoutePointDesign> route = stored == null || stored.routePoints() == null || stored.routePoints().size() < 2 ? fallbackRoute(source, target) : stored.routePoints();
        String color = "#334155";

        if(stored != null && stored.style() != null && stored.style().strokeColor() != null) {
            color = stored.style().strokeColor();
        }

        String dash = relation.kind() == MetaRelationKind.DEPENDENCY || relation.kind() == MetaRelationKind.IMPLEMENTATION ? " stroke-dasharray=\"8 5\"" : "";
        String markers = switch(relation.kind()) {
            case REFERENCE -> " marker-end=\"url(#arrow)\"";
            case CONTAINMENT -> " marker-start=\"url(#diamond-filled)\" marker-end=\"url(#arrow)\"";
            case AGGREGATION -> " marker-start=\"url(#diamond-open)\" marker-end=\"url(#arrow)\"";
            case DEPENDENCY -> " marker-end=\"url(#arrow)\"";
            case INHERITANCE, IMPLEMENTATION -> " marker-end=\"url(#triangle)\"";
        };
        StringBuilder path = new StringBuilder();

        for(int index = 0; index < route.size(); index += 1) {
            RoutePointDesign point = route.get(index);
            path.append(index == 0 ? "M " : " L ").append(point.x()).append(" ").append(point.y());
        }

        result.append("  <path d=\"").append(path).append("\" fill=\"none\" stroke=\"").append(color).append("\" stroke-width=\"2\"").append(dash).append(markers).append("/>\n");

        if(!relation.name().isBlank() && relation.kind() != MetaRelationKind.INHERITANCE && relation.kind() != MetaRelationKind.IMPLEMENTATION) {
            double labelX;
            double labelY;

            if(stored != null && stored.labelPosition() != null) {
                labelX = stored.labelPosition().x();
                labelY = stored.labelPosition().y();
            } else {
                RoutePointDesign first = route.get(0);
                RoutePointDesign last = route.get(route.size() - 1);
                labelX = (first.x() + last.x()) / 2;
                labelY = (first.y() + last.y()) / 2 - 5;
            }

            result.append("  <text x=\"").append(labelX).append("\" y=\"").append(labelY).append("\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"11\">").append(MetamodelExportSupport.xml(relation.name())).append("</text>\n");
        }
    }

    /** Descripción: Ejecuta la operación «fallbackRoute» correspondiente a la responsabilidad de SvgMetamodelExporter. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private java.util.List<RoutePointDesign> fallbackRoute(NodeDesign source, NodeDesign target) {
        double x1 = source.x() + source.width() / 2;
        double y1 = source.y() + source.height() / 2;
        double x2 = target.x() + target.width() / 2;
        double y2 = target.y() + target.height() / 2;
        return java.util.List.of(new RoutePointDesign(x1, y1), new RoutePointDesign(x2, y2));
    }

    /** Descripción: Ejecuta la operación «appendClass» correspondiente a la responsabilidad de SvgMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendClass(StringBuilder result, NeutralMetamodelExportContext context, MetaClass metaClass, NodeDesign node) {
        String fill = "#ffffff";
        String header = "#e2e8f0";
        String stroke = "#475569";
        String text = "#0f172a";

        if(context.options().includeStyles() && node.style() != null) {
            fill = MetamodelExportSupport.text(node.style().fillColor(), fill);
            header = MetamodelExportSupport.text(node.style().headerColor(), header);
            stroke = MetamodelExportSupport.text(node.style().strokeColor(), stroke);
            text = MetamodelExportSupport.text(node.style().textColor(), text);
        }

        result.append("  <rect x=\"").append(node.x()).append("\" y=\"").append(node.y()).append("\" width=\"").append(node.width()).append("\" height=\"").append(node.height()).append("\" rx=\"5\" fill=\"").append(fill).append("\" stroke=\"").append(stroke).append("\" stroke-width=\"2\"/>\n");
        result.append("  <rect x=\"").append(node.x()).append("\" y=\"").append(node.y()).append("\" width=\"").append(node.width()).append("\" height=\"42\" rx=\"5\" fill=\"").append(header).append("\" stroke=\"").append(stroke).append("\" stroke-width=\"2\"/>\n");
        result.append("  <text x=\"").append(node.x() + node.width() / 2).append("\" y=\"").append(node.y() + 26).append("\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"14\" font-weight=\"700\" fill=\"").append(text).append("\">").append(MetamodelExportSupport.xml(metaClass.name())).append("</text>\n");
        double y = node.y() + 60;

        for(var attribute : metaClass.attributes()) {
            result.append("  <text x=\"").append(node.x() + 9).append("\" y=\"").append(y).append("\" font-family=\"Arial\" font-size=\"11\" fill=\"").append(text).append("\">").append(MetamodelExportSupport.xml(attribute.name() + " : " + MetamodelExportSupport.resolveTypeName(context, attribute.type()) + " [" + MetamodelExportSupport.multiplicity(attribute.multiplicity()) + "]")).append("</text>\n");
            y += 16;
        }
        for(var relation : MetamodelExportSupport.structuralRelationsFrom(context, metaClass.id())) {
            result.append("  <text x=\"").append(node.x() + 9).append("\" y=\"").append(y).append("\" font-family=\"Arial\" font-size=\"11\" fill=\"").append(text).append("\">").append(MetamodelExportSupport.xml(relation.name() + " : " + MetamodelExportSupport.relationTargetName(context, relation) + " [" + MetamodelExportSupport.multiplicity(relation.targetMultiplicity()) + "]")).append("</text>\n");
            y += 16;
        }
        for(var operation : metaClass.operations()) {
            String parameters = operation.parameters().stream().map(parameter -> parameter.name() + ": " + MetamodelExportSupport.resolveTypeName(context, parameter.type())).collect(java.util.stream.Collectors.joining(", "));
            String returnType = operation.returnType() == null ? "void" : MetamodelExportSupport.resolveTypeName(context, operation.returnType());
            result.append("  <text x=\"").append(node.x() + 9).append("\" y=\"").append(y).append("\" font-family=\"Arial\" font-size=\"11\" fill=\"").append(text).append("\">").append(MetamodelExportSupport.xml(operation.name() + "(" + parameters + ") : " + returnType)).append("</text>\n");
            y += 16;
        }
    }

    /** Descripción: Ejecuta la operación «appendSpecial» correspondiente a la responsabilidad de SvgMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; stereotype: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; fill: dato de entrada necesario para la operación; stroke: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendSpecial(StringBuilder result, String stereotype, String name, NodeDesign node, String fill, String stroke) {
        result.append("  <rect x=\"").append(node.x()).append("\" y=\"").append(node.y()).append("\" width=\"").append(node.width()).append("\" height=\"").append(node.height()).append("\" rx=\"5\" fill=\"").append(fill).append("\" stroke=\"").append(stroke).append("\" stroke-width=\"2\"/>\n");
        result.append("  <text x=\"").append(node.x() + node.width() / 2).append("\" y=\"").append(node.y() + 18).append("\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"10\">").append(stereotype).append("</text>\n");
        result.append("  <text x=\"").append(node.x() + node.width() / 2).append("\" y=\"").append(node.y() + 38).append("\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"14\" font-weight=\"700\">").append(MetamodelExportSupport.xml(name)).append("</text>\n");
    }

}
