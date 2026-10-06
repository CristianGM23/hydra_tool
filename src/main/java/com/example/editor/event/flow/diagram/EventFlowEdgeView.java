package com.example.editor.event.flow.diagram;

import java.util.List;
import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Geometry and current visual presentation of one diagram connection. */
public record EventFlowEdgeView(String transitionId, List<EventFlowPoint> points, String sourceHandle, String targetHandle, String label, String note,
        String routeStyle, String lineStyle, String lineColor, double lineWidth, double lineOpacity, String lineBorderColor, double lineBorderWidth,
        String sourceTerminator, String targetTerminator, String sourceTerminatorFillColor, String sourceTerminatorBorderColor,
        double sourceTerminatorBorderWidth, double sourceTerminatorSize, String targetTerminatorFillColor,
        String targetTerminatorBorderColor, double targetTerminatorBorderWidth, double targetTerminatorSize) {
    public EventFlowEdgeView {
        transitionId = StableIdentifierPolicy.requireElementId(transitionId, "eventFlowEdgeView.transitionId");
        points = points == null ? List.of() : List.copyOf(points);
        sourceHandle = value(sourceHandle, "right-source");
        targetHandle = value(targetHandle, "left-target");
        label = value(label, "");
        note = value(note, "");
        routeStyle = value(routeStyle, "ORTHOGONAL");
        lineStyle = value(lineStyle, "SOLID");
        lineColor = value(lineColor, "");
        lineWidth = bounded(lineWidth, 0, 16, 0);
        lineOpacity = bounded(lineOpacity, .1, 1, 1);
        lineBorderColor = value(lineBorderColor, "");
        lineBorderWidth = bounded(lineBorderWidth, 0, 8, 0);
        sourceTerminator = value(sourceTerminator, "NONE");
        targetTerminator = value(targetTerminator, "ARROW_CLOSED");
        sourceTerminatorFillColor = value(sourceTerminatorFillColor, "");
        sourceTerminatorBorderColor = value(sourceTerminatorBorderColor, "");
        sourceTerminatorBorderWidth = bounded(sourceTerminatorBorderWidth, 0, 8, 0);
        sourceTerminatorSize = bounded(sourceTerminatorSize, .5, 2.5, 1);
        targetTerminatorFillColor = value(targetTerminatorFillColor, "");
        targetTerminatorBorderColor = value(targetTerminatorBorderColor, "");
        targetTerminatorBorderWidth = bounded(targetTerminatorBorderWidth, 0, 8, 0);
        targetTerminatorSize = bounded(targetTerminatorSize, .5, 2.5, 1);
    }

    /** Creates a connection with the current default visual presentation. */
    /** Descripción: Inicializa una instancia de EventFlowEdgeView con sus dependencias y datos de entrada. Entradas: transitionId: identificador que delimita el recurso objetivo; points: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventFlowEdgeView(String transitionId, List<EventFlowPoint> points) {
        this(transitionId, points, "right-source", "left-target", "", "", "ORTHOGONAL", "SOLID", "", 0, 1, "", 0,
                "NONE", "ARROW_CLOSED", "", "", 0, 1, "", "", 0, 1);
    }

    /** Descripción: Ejecuta la operación «value» correspondiente a la responsabilidad de EventFlowEdgeView. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Descripción: Ejecuta la operación «bounded» correspondiente a la responsabilidad de EventFlowEdgeView. Entradas: value: valor de entrada que debe procesarse; min: dato de entrada necesario para la operación; max: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) && value >= min && value <= max ? value : fallback;
    }
}
