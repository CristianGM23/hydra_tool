package com.example.editor.metamodel.export;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.EdgeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign;

/**
 * Genera una captura PNG neutral.
 */
@Component
public class PngMetamodelExporter implements MetamodelBinaryExporter {

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de PngMetamodelExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportFormat format() {
        return MetamodelExportFormat.PNG;
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de PngMetamodelExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] export(NeutralMetamodelExportContext context) {
        Map<String, NodeDesign> nodes = layout(context);
        Map<String, EdgeDesign> storedEdges = MetamodelExportSupport.indexEdges(context);
        DiagramExportBounds.Bounds bounds = DiagramExportBounds.calculate(nodes, storedEdges);
        int scale = context.options().imageScale();
        int width = Math.max(1, (int) Math.ceil(bounds.width() * scale));
        int height = Math.max(1, (int) Math.ceil(bounds.height() * scale));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if(!context.options().transparentBackground()) {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
        }

        graphics.scale(scale, scale);
        graphics.translate(-bounds.x(), -bounds.y());
        graphics.setColor(new Color(51, 65, 85));
        graphics.setStroke(new BasicStroke(2));

        for(MetaRelation relation : context.neutralMetamodel().relations()) {
            drawEdge(graphics, relation, nodes, storedEdges.get(relation.id()));
        }

        for(MetaClass metaClass : context.neutralMetamodel().classes()) {
            drawClass(graphics, context, metaClass, nodes.get(metaClass.id()));
        }

        for(MetaEnum metaEnum : context.neutralMetamodel().enums()) {
            drawSpecial(graphics, "«enumeration»", metaEnum.name(), nodes.get(metaEnum.id()), new Color(245, 243, 255), new Color(124, 58, 237));
        }

        for(MetaDataType dataType : context.neutralMetamodel().dataTypes()) {
            drawSpecial(graphics, "«datatype»", dataType.name(), nodes.get(dataType.id()), new Color(236, 254, 255), new Color(8, 145, 178));
        }

        graphics.dispose();

        try(ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.STORAGE_ERROR, "Could not generate PNG export", exception);
        }
    }

    /** Descripción: Ejecuta la operación «layout» correspondiente a la responsabilidad de PngMetamodelExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, NodeDesign> layout(NeutralMetamodelExportContext context) {
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

    /** Descripción: Ejecuta la operación «drawEdge» correspondiente a la responsabilidad de PngMetamodelExporter. Entradas: graphics: dato de entrada necesario para la operación; relation: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación; stored: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void drawEdge(Graphics2D graphics, MetaRelation relation, Map<String, NodeDesign> nodes, EdgeDesign stored) {
        NodeDesign source = nodes.get(relation.sourceTypeId());
        NodeDesign target = nodes.get(relation.targetTypeId());

        if(source == null || target == null) {
            return;
        }

        java.util.List<RoutePointDesign> route = stored == null || stored.routePoints() == null || stored.routePoints().size() < 2 ? fallbackRoute(source, target) : stored.routePoints();

        for(int index = 0; index < route.size() - 1; index += 1) {
            RoutePointDesign first = route.get(index);
            RoutePointDesign second = route.get(index + 1);
            graphics.draw(new Line2D.Double(first.x(), first.y(), second.x(), second.y()));
        }

        RoutePointDesign beforeTarget = route.get(route.size() - 2);
        RoutePointDesign targetPoint = route.get(route.size() - 1);
        double angle = Math.atan2(targetPoint.y() - beforeTarget.y(), targetPoint.x() - beforeTarget.x());
        int size = 10;
        graphics.drawLine((int) targetPoint.x(), (int) targetPoint.y(), (int) (targetPoint.x() - size * Math.cos(angle - Math.PI / 6)), (int) (targetPoint.y() - size * Math.sin(angle - Math.PI / 6)));
        graphics.drawLine((int) targetPoint.x(), (int) targetPoint.y(), (int) (targetPoint.x() - size * Math.cos(angle + Math.PI / 6)), (int) (targetPoint.y() - size * Math.sin(angle + Math.PI / 6)));

        if(!relation.name().isBlank()) {
            graphics.setFont(new Font("SansSerif", Font.PLAIN, 11));
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

            graphics.drawString(relation.name(), (int) labelX, (int) labelY);
        }
    }

    /** Descripción: Ejecuta la operación «fallbackRoute» correspondiente a la responsabilidad de PngMetamodelExporter. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private java.util.List<RoutePointDesign> fallbackRoute(NodeDesign source, NodeDesign target) {
        double x1 = source.x() + source.width() / 2;
        double y1 = source.y() + source.height() / 2;
        double x2 = target.x() + target.width() / 2;
        double y2 = target.y() + target.height() / 2;
        return java.util.List.of(new RoutePointDesign(x1, y1), new RoutePointDesign(x2, y2));
    }

    /** Descripción: Ejecuta la operación «drawClass» correspondiente a la responsabilidad de PngMetamodelExporter. Entradas: graphics: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void drawClass(Graphics2D graphics, NeutralMetamodelExportContext context, MetaClass metaClass, NodeDesign node) {
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect((int) node.x(), (int) node.y(), (int) node.width(), (int) node.height(), 10, 10);
        graphics.setColor(new Color(226, 232, 240));
        graphics.fillRoundRect((int) node.x(), (int) node.y(), (int) node.width(), 42, 10, 10);
        graphics.setColor(new Color(71, 85, 105));
        graphics.drawRoundRect((int) node.x(), (int) node.y(), (int) node.width(), (int) node.height(), 10, 10);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 14));
        graphics.drawString(metaClass.name(), (int) node.x() + 10, (int) node.y() + 27);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 11));
        int y = (int) node.y() + 60;

        for(var attribute : metaClass.attributes()) {
            graphics.drawString(attribute.name() + " : " + MetamodelExportSupport.resolveTypeName(context, attribute.type()), (int) node.x() + 10, y);
            y += 16;
        }
        for(var relation : MetamodelExportSupport.structuralRelationsFrom(context, metaClass.id())) {
            graphics.drawString(relation.name() + " : " + MetamodelExportSupport.relationTargetName(context, relation), (int) node.x() + 10, y);
            y += 16;
        }
        for(var operation : metaClass.operations()) {
            String parameters = operation.parameters().stream().map(parameter -> parameter.name() + ": " + MetamodelExportSupport.resolveTypeName(context, parameter.type())).collect(java.util.stream.Collectors.joining(", "));
            String returnType = operation.returnType() == null ? "void" : MetamodelExportSupport.resolveTypeName(context, operation.returnType());
            graphics.drawString(operation.name() + "(" + parameters + ") : " + returnType, (int) node.x() + 10, y);
            y += 16;
        }
    }

    /** Descripción: Ejecuta la operación «drawSpecial» correspondiente a la responsabilidad de PngMetamodelExporter. Entradas: graphics: dato de entrada necesario para la operación; stereotype: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; fill: dato de entrada necesario para la operación; stroke: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void drawSpecial(Graphics2D graphics, String stereotype, String name, NodeDesign node, Color fill, Color stroke) {
        graphics.setColor(fill);
        graphics.fillRoundRect((int) node.x(), (int) node.y(), (int) node.width(), (int) node.height(), 10, 10);
        graphics.setColor(stroke);
        graphics.drawRoundRect((int) node.x(), (int) node.y(), (int) node.width(), (int) node.height(), 10, 10);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 10));
        graphics.drawString(stereotype, (int) node.x() + 10, (int) node.y() + 18);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 14));
        graphics.drawString(name, (int) node.x() + 10, (int) node.y() + 38);
    }
}
