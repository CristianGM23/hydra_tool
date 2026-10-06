package com.example.editor.projection.drawio;

import java.util.ArrayList;
import java.util.List;

/** Constructor mínimo de XML mxGraph sin acoplar los módulos al formato textual. */
public final class DrawioGraphBuilder {
    private final String diagramId;
    private final String name;
    private final List<String> cells = new ArrayList<>();

    /** Descripción: Inicializa una instancia de DrawioGraphBuilder con sus dependencias y datos de entrada. Entradas: diagramId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public DrawioGraphBuilder(String diagramId, String name) {
        this.diagramId = diagramId == null || diagramId.isBlank() ? "diagram" : diagramId;
        this.name = name == null || name.isBlank() ? "Diagram" : name;
    }

    /** Descripción: Ejecuta la operación «vertex» correspondiente a la responsabilidad de DrawioGraphBuilder. Entradas: id: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse; x: dato de entrada necesario para la operación; y: dato de entrada necesario para la operación; width: dato de entrada necesario para la operación; height: dato de entrada necesario para la operación; style: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public DrawioGraphBuilder vertex(String id, String value, double x, double y, double width, double height, String style) {
        String cell = "        <mxCell id=\"" + xml(id) + "\" value=\"" + xml(value) + "\" style=\"" + xml(style)
                + "\" vertex=\"1\" parent=\"1\"><mxGeometry x=\"" + number(x) + "\" y=\"" + number(y)
                + "\" width=\"" + number(width) + "\" height=\"" + number(height) + "\" as=\"geometry\"/></mxCell>";
        cells.add(cell);
        return this;
    }

    /** Descripción: Ejecuta la operación «edge» correspondiente a la responsabilidad de DrawioGraphBuilder. Entradas: id: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; style: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public DrawioGraphBuilder edge(String id, String value, String source, String target, String style) {
        String cell = "        <mxCell id=\"" + xml(id) + "\" value=\"" + xml(value) + "\" style=\"" + xml(style)
                + "\" edge=\"1\" parent=\"1\" source=\"" + xml(source) + "\" target=\"" + xml(target)
                + "\"><mxGeometry relative=\"1\" as=\"geometry\"/></mxCell>";
        cells.add(cell);
        return this;
    }

    /** Descripción: Crea o prepara el resultado de «build» dentro de DrawioGraphBuilder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String build() {
        StringBuilder result = new StringBuilder();
        result.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        result.append("<mxfile host=\"app.diagrams.net\" agent=\"Neutral Editor Drawio Projection\">\n");
        result.append("  <diagram id=\"").append(xml(diagramId)).append("\" name=\"").append(xml(name)).append("\">\n");
        result.append("    <mxGraphModel grid=\"1\" gridSize=\"10\" guides=\"1\" connect=\"1\" page=\"1\" pageWidth=\"1600\" pageHeight=\"1200\">\n");
        result.append("      <root>\n");
        result.append("        <mxCell id=\"0\"/>\n");
        result.append("        <mxCell id=\"1\" parent=\"0\"/>\n");
        for(String cell : cells) result.append(cell).append('\n');
        result.append("      </root>\n");
        result.append("    </mxGraphModel>\n");
        result.append("  </diagram>\n");
        result.append("</mxfile>\n");
        return result.toString();
    }

    /** Descripción: Ejecuta la operación «html» correspondiente a la responsabilidad de DrawioGraphBuilder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String html(String value) { return xml(value).replace("\n", "&lt;br&gt;"); }

    /** Descripción: Ejecuta la operación «xml» correspondiente a la responsabilidad de DrawioGraphBuilder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String xml(String value) {
        if(value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    /** Descripción: Ejecuta la operación «number» correspondiente a la responsabilidad de DrawioGraphBuilder. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String number(double value) { return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value); }
}
