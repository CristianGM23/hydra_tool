package com.example.editor.metamodel.export;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeStyleDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeStyleDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign;

/**
 * Genera un diagrama UML editable para draw.io.
 */
@Component
public class DrawioMetamodelExporter implements MetamodelTextExporter {

    private static final double ROW_HEIGHT = 26;
    private static final double HEADER_HEIGHT = 44;

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de Geometry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportFormat format() {
        return MetamodelExportFormat.DRAWIO;
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de Geometry. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String export(NeutralMetamodelExportContext context) {
        Map<String, NodeDesign> storedNodes = MetamodelExportSupport.indexNodes(context);
        Map<String, EdgeDesign> storedEdges = MetamodelExportSupport.indexEdges(context);
        Map<String, Geometry> geometries = buildGeometries(context, storedNodes);
        StringBuilder result = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<mxfile host=\"app.diagrams.net\" agent=\"Neutral Metamodel Editor\">\n  <diagram id=\"").append(MetamodelExportSupport.xml(context.neutralMetamodel().id())).append("\" name=\"Metamodel\">\n    <mxGraphModel grid=\"1\" gridSize=\"10\" guides=\"1\" connect=\"1\" page=\"1\" pageWidth=\"1600\" pageHeight=\"1200\" background=\"none\">\n      <root>\n        <mxCell id=\"0\"/>\n        <mxCell id=\"1\" parent=\"0\"/>\n");

        for(MetaRelation relation : context.neutralMetamodel().relations()) {
            appendRelation(result, relation, storedEdges.get(relation.id()), context);
        }

        for(MetaClass metaClass : context.neutralMetamodel().classes()) {
            appendClass(result, context, metaClass, geometries.get(metaClass.id()), storedNodes.get(metaClass.id()));
        }

        for(MetaEnum metaEnum : context.neutralMetamodel().enums()) {
            appendEnum(result, metaEnum, geometries.get(metaEnum.id()), storedNodes.get(metaEnum.id()), context.options().includeStyles());
        }

        for(MetaDataType dataType : context.neutralMetamodel().dataTypes()) {
            appendDataType(result, context, dataType, geometries.get(dataType.id()), storedNodes.get(dataType.id()), context.options().includeStyles());
        }

        result.append("      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n");
        return result.toString();
    }

    /** Descripción: Crea o prepara el resultado de «buildGeometries» dentro de Geometry. Entradas: context: dato de entrada necesario para la operación; storedNodes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, Geometry> buildGeometries(NeutralMetamodelExportContext context, Map<String, NodeDesign> storedNodes) {
        Map<String, Geometry> result = new LinkedHashMap<>();
        int index = 0;

        for(MetaClass metaClass : context.neutralMetamodel().classes()) {
            int relationRows = Math.max(1, MetamodelExportSupport.structuralRelationsFrom(context, metaClass.id()).size());
            double height = HEADER_HEIGHT + Math.max(1, metaClass.attributes().size()) * ROW_HEIGHT + 8 + relationRows * ROW_HEIGHT + 8 + Math.max(1, metaClass.operations().size()) * ROW_HEIGHT;
            result.put(metaClass.id(), geometry(storedNodes.get(metaClass.id()), index, 240, height));
            index += 1;
        }

        for(MetaEnum metaEnum : context.neutralMetamodel().enums()) {
            result.put(metaEnum.id(), geometry(storedNodes.get(metaEnum.id()), index, 220, HEADER_HEIGHT + Math.max(1, metaEnum.literals().size()) * ROW_HEIGHT));
            index += 1;
        }

        for(MetaDataType dataType : context.neutralMetamodel().dataTypes()) {
            result.put(dataType.id(), geometry(storedNodes.get(dataType.id()), index, 240, HEADER_HEIGHT + 2 * ROW_HEIGHT));
            index += 1;
        }

        return result;
    }

    /** Descripción: Ejecuta la operación «geometry» correspondiente a la responsabilidad de Geometry. Entradas: node: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación; width: dato de entrada necesario para la operación; height: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Geometry geometry(NodeDesign node, int index, double width, double height) {
        if(node != null) {
            return new Geometry(node.x(), node.y(), Math.max(width, node.width()), Math.max(height, node.height()));
        }

        return new Geometry(60 + index % 4 * 290, 60 + index / 4 * 250, width, height);
    }

    /** Descripción: Ejecuta la operación «appendClass» correspondiente a la responsabilidad de Geometry. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación; geometry: dato de entrada necesario para la operación; storedNode: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendClass(StringBuilder result, NeutralMetamodelExportContext context, MetaClass metaClass, Geometry geometry, NodeDesign storedNode) {
        NodeStyleDesign style = context.options().includeStyles() && storedNode != null ? storedNode.style() : null;
        String title = metaClass.kind() == MetaClassKind.INTERFACE ? "«interface»&#xa;" + metaClass.name() : metaClass.kind() == MetaClassKind.ABSTRACT_CLASS ? "«abstract»&#xa;" + metaClass.name() : metaClass.name();
        appendSwimlane(result, metaClass.id(), title, geometry, style);
        double y = HEADER_HEIGHT;

        if(metaClass.attributes().isEmpty()) {
            appendRow(result, metaClass.id() + "-attribute-empty", metaClass.id(), "", y, geometry.width(), style);
        } else {
            for(MetaAttribute attribute : metaClass.attributes()) {
                appendRow(result, metaClass.id() + "-attribute-" + attribute.id(), metaClass.id(), "+ " + attribute.name() + " : " + drawioTypeName(context, attribute) + " [" + MetamodelExportSupport.multiplicity(attribute.multiplicity()) + "]", y, geometry.width(), style);
                y += ROW_HEIGHT;
            }
        }

        appendSeparator(result, metaClass.id() + "-separator", metaClass.id(), y, geometry.width(), style);
        y += 8;
        var relations = MetamodelExportSupport.structuralRelationsFrom(context, metaClass.id());

        if(relations.isEmpty()) {
            appendRow(result, metaClass.id() + "-relation-empty", metaClass.id(), "", y, geometry.width(), style);
            y += ROW_HEIGHT;
        } else {
            for(MetaRelation relation : relations) {
                appendRow(result, metaClass.id() + "-relation-" + relation.id(), metaClass.id(), "+ " + MetamodelExportSupport.text(relation.name(), "reference") + " : " + MetamodelExportSupport.relationTargetName(context, relation) + " [" + MetamodelExportSupport.multiplicity(relation.targetMultiplicity()) + "]", y, geometry.width(), style);
                y += ROW_HEIGHT;
            }
        }
        appendSeparator(result, metaClass.id() + "-operation-separator", metaClass.id(), y, geometry.width(), style);
        y += 8;
        if(metaClass.operations().isEmpty()) {
            appendRow(result, metaClass.id() + "-operation-empty", metaClass.id(), "", y, geometry.width(), style);
        } else {
            for(MetaOperation operation : metaClass.operations()) {
                String parameters = operation.parameters().stream().map(parameter -> parameter.name() + ": " + MetamodelExportSupport.resolveTypeName(context, parameter.type())).collect(java.util.stream.Collectors.joining(", "));
                String returnType = operation.returnType() == null ? "void" : MetamodelExportSupport.resolveTypeName(context, operation.returnType());
                String behavior = operation.behaviorKind() == com.example.editor.metamodel.core.MetaOperationBehaviorKind.UNSPECIFIED ? "" : " <<" + operation.behaviorKind().name() + ">>";
                appendRow(result, metaClass.id() + "-operation-" + operation.id(), metaClass.id(), "+ " + operation.name() + "(" + parameters + ") : " + returnType + behavior, y, geometry.width(), style);
                y += ROW_HEIGHT;
            }
        }
    }


    /**
     * Descripción:
     * Devuelve el nombre de tipo usado por la plantilla UML de draw.io.
     *
     * Entrada:
     * @param context contiene el metamodelo neutral.
     * @param attribute contiene el atributo.
     *
     * Salida:
     * @return el nombre del tipo.
     */
    /** Descripción: Ejecuta la operación «drawioTypeName» correspondiente a la responsabilidad de Geometry. Entradas: context: dato de entrada necesario para la operación; attribute: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String drawioTypeName(NeutralMetamodelExportContext context, MetaAttribute attribute) {
        if(attribute.type().kind() != MetaTypeReferenceKind.PRIMITIVE) {
            return MetamodelExportSupport.resolveTypeName(context, attribute.type());
        }

        return switch(attribute.type().targetId()) {
            case "STRING" -> "EString";
            case "BOOLEAN" -> "EBoolean";
            case "BYTE" -> "EByte";
            case "SHORT" -> "EShort";
            case "INTEGER" -> "EInt";
            case "LONG" -> "ELong";
            case "FLOAT" -> "EFloat";
            case "DOUBLE" -> "EDouble";
            case "DECIMAL" -> "EBigDecimal";
            case "BIG_INTEGER" -> "EBigInteger";
            case "CHARACTER" -> "EChar";
            case "DATE" -> "EDate";
            case "BINARY" -> "EByteArray";
            case "OBJECT" -> "EJavaObject";
            default -> MetamodelExportSupport.resolveTypeName(context, attribute.type());
        };
    }

    /** Descripción: Ejecuta la operación «appendEnum» correspondiente a la responsabilidad de Geometry. Entradas: result: dato de entrada necesario para la operación; metaEnum: dato de entrada necesario para la operación; geometry: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; includeStyles: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendEnum(StringBuilder result, MetaEnum metaEnum, Geometry geometry, NodeDesign node, boolean includeStyles) {
        NodeStyleDesign style = includeStyles && node != null ? node.style() : null;
        appendSwimlane(result, metaEnum.id(), "«enumeration»&#xa;" + metaEnum.name(), geometry, style);
        double y = HEADER_HEIGHT;

        if(metaEnum.literals().isEmpty()) {
            appendRow(result, metaEnum.id() + "-empty", metaEnum.id(), "", y, geometry.width(), style);
        } else {
            for(var literal : metaEnum.literals()) {
                appendRow(result, metaEnum.id() + "-" + literal.id(), metaEnum.id(), literal.name() + " = " + literal.value(), y, geometry.width(), style);
                y += ROW_HEIGHT;
            }
        }
    }

    /** Descripción: Ejecuta la operación «appendDataType» correspondiente a la responsabilidad de Geometry. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; dataType: dato de entrada necesario para la operación; geometry: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; includeStyles: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendDataType(StringBuilder result, NeutralMetamodelExportContext context, MetaDataType dataType, Geometry geometry, NodeDesign node, boolean includeStyles) {
        NodeStyleDesign style = includeStyles && node != null ? node.style() : null;
        var configuration = context.targets().ecore().dataType(dataType.id());
        appendSwimlane(result, dataType.id(), "«datatype»&#xa;" + dataType.name(), geometry, style);
        appendRow(result, dataType.id() + "-implementation", dataType.id(), "+ implementationTypeName : " + configuration.instanceClassName(), HEADER_HEIGHT, geometry.width(), style);
        appendRow(result, dataType.id() + "-serializable", dataType.id(), "+ serializable : Boolean = " + configuration.serializable(), HEADER_HEIGHT + ROW_HEIGHT, geometry.width(), style);
    }

    /** Descripción: Ejecuta la operación «appendSwimlane» correspondiente a la responsabilidad de Geometry. Entradas: result: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; title: dato de entrada necesario para la operación; geometry: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendSwimlane(StringBuilder result, String id, String title, Geometry geometry, NodeStyleDesign source) {
        NodeVisual style = nodeStyle(source);
        result.append("        <mxCell id=\"").append(MetamodelExportSupport.xml(id)).append("\" parent=\"1\" style=\"swimlane;html=1;childLayout=stackLayout;horizontal=1;startSize=").append(HEADER_HEIGHT).append(";resizeParent=1;resizeLast=0;collapsible=1;swimlaneFillColor=").append(style.body()).append(";fillColor=").append(style.header()).append(";strokeColor=").append(style.stroke()).append(";fontColor=").append(style.text()).append(";fontFamily=").append(MetamodelExportSupport.xml(style.fontFamily())).append(";fontSize=").append(style.fontSize()).append(";strokeWidth=").append(style.strokeWidth()).append(";whiteSpace=wrap;\" value=\"").append(MetamodelExportSupport.xml(title).replace("&amp;#xa;", "&#xa;")).append("\" vertex=\"1\">\n");
        result.append("          <mxGeometry x=\"").append(geometry.x()).append("\" y=\"").append(geometry.y()).append("\" width=\"").append(geometry.width()).append("\" height=\"").append(geometry.height()).append("\" as=\"geometry\"/>\n        </mxCell>\n");
    }

    /** Descripción: Ejecuta la operación «appendRow» correspondiente a la responsabilidad de Geometry. Entradas: result: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; parent: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse; y: dato de entrada necesario para la operación; width: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendRow(StringBuilder result, String id, String parent, String value, double y, double width, NodeStyleDesign source) {
        NodeVisual style = nodeStyle(source);
        result.append("        <mxCell id=\"").append(MetamodelExportSupport.xml(id)).append("\" parent=\"").append(MetamodelExportSupport.xml(parent)).append("\" style=\"text;html=1;strokeColor=none;fillColor=none;align=left;verticalAlign=middle;spacingLeft=6;whiteSpace=wrap;points=[[0,0.5],[1,0.5]];portConstraint=eastwest;fontFamily=").append(MetamodelExportSupport.xml(style.fontFamily())).append(";fontSize=").append(style.fontSize()).append(";fontColor=").append(style.text()).append(";\" value=\"").append(MetamodelExportSupport.xml(value)).append("\" vertex=\"1\">\n");
        result.append("          <mxGeometry y=\"").append(y).append("\" width=\"").append(width).append("\" height=\"").append(ROW_HEIGHT).append("\" as=\"geometry\"/>\n        </mxCell>\n");
    }

    /** Descripción: Ejecuta la operación «appendSeparator» correspondiente a la responsabilidad de Geometry. Entradas: result: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; parent: dato de entrada necesario para la operación; y: dato de entrada necesario para la operación; width: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendSeparator(StringBuilder result, String id, String parent, double y, double width, NodeStyleDesign source) {
        NodeVisual style = nodeStyle(source);
        result.append("        <mxCell id=\"").append(MetamodelExportSupport.xml(id)).append("\" parent=\"").append(MetamodelExportSupport.xml(parent)).append("\" style=\"line;html=1;strokeColor=").append(style.stroke()).append(";strokeWidth=").append(style.strokeWidth()).append(";\" value=\"\" vertex=\"1\">\n");
        result.append("          <mxGeometry y=\"").append(y).append("\" width=\"").append(width).append("\" height=\"8\" as=\"geometry\"/>\n        </mxCell>\n");
    }

    /** Descripción: Ejecuta la operación «appendRelation» correspondiente a la responsabilidad de Geometry. Entradas: result: dato de entrada necesario para la operación; relation: dato de entrada necesario para la operación; stored: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendRelation(StringBuilder result, MetaRelation relation, EdgeDesign stored, NeutralMetamodelExportContext context) {
        EdgeStyleDesign style = context.options().includeStyles() && stored != null ? stored.style() : null;
        String color = style == null || style.strokeColor() == null ? "#334155" : style.strokeColor();
        double width = style == null || style.strokeWidth() == null ? 2 : style.strokeWidth();
        String line = "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;strokeColor=" + color + ";strokeWidth=" + width + ";";
        line += switch(relation.kind()) {
            case REFERENCE -> "endArrow=open;endFill=0;";
            case CONTAINMENT -> "startArrow=diamond;startFill=1;endArrow=open;endFill=0;";
            case AGGREGATION -> "startArrow=diamondThin;startFill=0;endArrow=open;endFill=0;";
            case DEPENDENCY -> "dashed=1;endArrow=open;endFill=0;";
            case INHERITANCE -> "endArrow=block;endFill=0;";
            case IMPLEMENTATION -> "dashed=1;endArrow=block;endFill=0;";
        };

        if(stored != null) {
            line += handleStyle(stored.sourceHandle(), true) + handleStyle(stored.targetHandle(), false);
        }

        String label = relation.kind() == MetaRelationKind.INHERITANCE || relation.kind() == MetaRelationKind.IMPLEMENTATION ? "" : relation.name();
        result.append("        <mxCell id=\"").append(MetamodelExportSupport.xml(relation.id())).append("\" edge=\"1\" parent=\"1\" source=\"").append(MetamodelExportSupport.xml(relation.sourceTypeId())).append("\" target=\"").append(MetamodelExportSupport.xml(relation.targetTypeId())).append("\" style=\"").append(line).append("\" value=\"").append(MetamodelExportSupport.xml(label)).append("\">\n");
        appendRouteGeometry(result, stored);
        result.append("        </mxCell>\n");
    }

    /** Descripción: Ejecuta la operación «appendRouteGeometry» correspondiente a la responsabilidad de Geometry. Entradas: result: dato de entrada necesario para la operación; stored: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendRouteGeometry(StringBuilder result, EdgeDesign stored) {
        if(stored == null || stored.routePoints() == null || stored.routePoints().size() <= 2) {
            result.append("          <mxGeometry relative=\"1\" as=\"geometry\"/>\n");
            return;
        }

        result.append("          <mxGeometry relative=\"1\" as=\"geometry\">\n");
        result.append("            <Array as=\"points\">\n");

        for(int index = 1; index < stored.routePoints().size() - 1; index += 1) {
            RoutePointDesign point = stored.routePoints().get(index);
            result.append("              <mxPoint x=\"").append(formatCoordinate(point.x())).append("\" y=\"").append(formatCoordinate(point.y())).append("\"/>\n");
        }

        result.append("            </Array>\n");
        result.append("          </mxGeometry>\n");
    }

    /** Descripción: Procesa la operación «handleStyle» coordinando las responsabilidades de Geometry. Entradas: handle: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String handleStyle(String handle, boolean source) {
        if(handle == null || handle.isBlank()) {
            return "";
        }

        String prefix = source ? "exit" : "entry";
        String lower = handle.toLowerCase(java.util.Locale.ROOT);
        double fraction = readFraction(lower);
        double x = lower.contains("left") ? 0 : lower.contains("right") ? 1 : fraction;
        double y = lower.contains("top") ? 0 : lower.contains("bottom") ? 1 : fraction;
        return prefix + "X=" + formatCoordinate(x) + ";" + prefix + "Y=" + formatCoordinate(y) + ";" + prefix + "Dx=0;" + prefix + "Dy=0;";
    }

    /** Descripción: Obtiene la información correspondiente a «readFraction» dentro de Geometry. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private double readFraction(String value) {
        for(String part : value.split("-")) {
            try {
                double number = Double.parseDouble(part);

                if(number >= 0 && number <= 100) {
                    return number / 100.0;
                }
            } catch(NumberFormatException exception) {
                // El identificador también contiene palabras.
            }
        }

        return 0.5;
    }

    /** Descripción: Ejecuta la operación «formatCoordinate» correspondiente a la responsabilidad de Geometry. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String formatCoordinate(double value) {
        if(value == Math.floor(value)) {
            return Integer.toString((int) value);
        }

        return Double.toString(Math.round(value * 1000.0) / 1000.0);
    }

    /** Descripción: Ejecuta la operación «nodeStyle» correspondiente a la responsabilidad de Geometry. Entradas: style: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NodeVisual nodeStyle(NodeStyleDesign style) {
        if(style == null) {
            return new NodeVisual("#ffffff", "#e2e8f0", "#475569", "#0f172a", "Arial", 12, 2);
        }

        return new NodeVisual(MetamodelExportSupport.text(style.fillColor(), "#ffffff"), MetamodelExportSupport.text(style.headerColor(), "#e2e8f0"), MetamodelExportSupport.text(style.strokeColor(), "#475569"), MetamodelExportSupport.text(style.textColor(), "#0f172a"), MetamodelExportSupport.text(style.fontFamily(), "Arial"), style.fontSize() == null ? 12 : style.fontSize(), style.strokeWidth() == null ? 2 : style.strokeWidth());
    }

    private record Geometry(double x, double y, double width, double height) {
    }

    private record NodeVisual(String body, String header, String stroke, String text, String fontFamily, double fontSize, double strokeWidth) {
    }
}
