package com.example.editor.metamodel.layout.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.example.editor.metamodel.design.MetamodelDiagramDocument.NodeDesign;
import com.example.editor.metamodel.design.MetamodelDiagramDocument.RoutePointDesign;

/**
 * Reúne cálculos geométricos compartidos por puertos, rutas y métricas.
 */
public final class MetamodelRoutingGeometry {

    /** Descripción: Inicializa una instancia de MetamodelRoutingGeometry con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private MetamodelRoutingGeometry() {
    }

    /** Descripción: Procesa la operación «handlePoint» coordinando las responsabilidades de MetamodelRoutingGeometry. Entradas: node: dato de entrada necesario para la operación; handle: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static RoutePointDesign handlePoint(NodeDesign node, String handle) {
        String side = side(handle);
        double percent = percent(handle);
        double ratio = Math.max(0, Math.min(100, percent)) / 100.0;

        return switch(side) {
            case "left" -> new RoutePointDesign(node.x(), node.y() + node.height() * ratio);
            case "right" -> new RoutePointDesign(node.x() + node.width(), node.y() + node.height() * ratio);
            case "top" -> new RoutePointDesign(node.x() + node.width() * ratio, node.y());
            case "bottom" -> new RoutePointDesign(node.x() + node.width() * ratio, node.y() + node.height());
            default -> new RoutePointDesign(node.x() + node.width(), node.y() + node.height() / 2);
        };
    }

    /** Descripción: Ejecuta la operación «leadPoint» correspondiente a la responsabilidad de MetamodelRoutingGeometry. Entradas: point: dato de entrada necesario para la operación; handle: dato de entrada necesario para la operación; clearance: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static RoutePointDesign leadPoint(RoutePointDesign point, String handle, double clearance) {
        return switch(side(handle)) {
            case "left" -> new RoutePointDesign(point.x() - clearance, point.y());
            case "right" -> new RoutePointDesign(point.x() + clearance, point.y());
            case "top" -> new RoutePointDesign(point.x(), point.y() - clearance);
            case "bottom" -> new RoutePointDesign(point.x(), point.y() + clearance);
            default -> point;
        };
    }

    /** Descripción: Ejecuta la operación «side» correspondiente a la responsabilidad de MetamodelRoutingGeometry. Entradas: handle: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String side(String handle) {
        if(handle == null || handle.isBlank()) {
            return "right";
        }

        String normalized = handle.toLowerCase(Locale.ROOT);

        if(normalized.startsWith("left")) {
            return "left";
        }

        if(normalized.startsWith("right")) {
            return "right";
        }

        if(normalized.startsWith("top")) {
            return "top";
        }

        if(normalized.startsWith("bottom")) {
            return "bottom";
        }

        return "right";
    }

    /** Descripción: Ejecuta la operación «percent» correspondiente a la responsabilidad de MetamodelRoutingGeometry. Entradas: handle: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static double percent(String handle) {
        if(handle == null || handle.isBlank()) {
            return 50;
        }

        String[] parts = handle.split("-");

        for(String part : parts) {
            try {
                double value = Double.parseDouble(part);

                if(value >= 0 && value <= 100) {
                    return value;
                }
            } catch(NumberFormatException exception) {
                // El identificador puede contener palabras como source o target.
            }
        }

        if(handle.contains("top-source") || handle.contains("right-source")) {
            return 25;
        }

        if(handle.contains("bottom-source") || handle.contains("left-source")) {
            return 75;
        }

        if(handle.contains("top-target") || handle.contains("right-target")) {
            return 75;
        }

        if(handle.contains("bottom-target") || handle.contains("left-target")) {
            return 25;
        }

        return 50;
    }

    /** Descripción: Ejecuta la operación «compress» correspondiente a la responsabilidad de MetamodelRoutingGeometry. Entradas: points: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static List<RoutePointDesign> compress(List<RoutePointDesign> points) {
        if(points == null || points.size() < 3) {
            return points == null ? List.of() : List.copyOf(points);
        }

        List<RoutePointDesign> result = new ArrayList<>();
        result.add(points.get(0));

        for(int index = 1; index < points.size() - 1; index += 1) {
            RoutePointDesign previous = result.get(result.size() - 1);
            RoutePointDesign current = points.get(index);
            RoutePointDesign next = points.get(index + 1);
            boolean sameX = approximately(previous.x(), current.x()) && approximately(current.x(), next.x());
            boolean sameY = approximately(previous.y(), current.y()) && approximately(current.y(), next.y());

            if(!sameX && !sameY) {
                result.add(current);
            }
        }

        result.add(points.get(points.size() - 1));
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «approximately» correspondiente a la responsabilidad de MetamodelRoutingGeometry. Entradas: first: dato de entrada necesario para la operación; second: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean approximately(double first, double second) {
        return Math.abs(first - second) < 0.0001;
    }

    /** Descripción: Ejecuta la operación «segmentLength» correspondiente a la responsabilidad de MetamodelRoutingGeometry. Entradas: first: dato de entrada necesario para la operación; second: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static double segmentLength(RoutePointDesign first, RoutePointDesign second) {
        return Math.abs(second.x() - first.x()) + Math.abs(second.y() - first.y());
    }

    /** Descripción: Ejecuta la operación «segmentIntersectsRectangle» correspondiente a la responsabilidad de MetamodelRoutingGeometry. Entradas: first: dato de entrada necesario para la operación; second: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; clearance: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean segmentIntersectsRectangle(RoutePointDesign first, RoutePointDesign second, NodeDesign node, double clearance) {
        double left = node.x() - clearance;
        double right = node.x() + node.width() + clearance;
        double top = node.y() - clearance;
        double bottom = node.y() + node.height() + clearance;

        if(approximately(first.x(), second.x())) {
            double x = first.x();
            double minimumY = Math.min(first.y(), second.y());
            double maximumY = Math.max(first.y(), second.y());
            return x > left && x < right && maximumY > top && minimumY < bottom;
        }

        if(approximately(first.y(), second.y())) {
            double y = first.y();
            double minimumX = Math.min(first.x(), second.x());
            double maximumX = Math.max(first.x(), second.x());
            return y > top && y < bottom && maximumX > left && minimumX < right;
        }

        return false;
    }

    /** Descripción: Ejecuta la operación «segmentsCross» correspondiente a la responsabilidad de MetamodelRoutingGeometry. Entradas: firstA: dato de entrada necesario para la operación; secondA: dato de entrada necesario para la operación; firstB: dato de entrada necesario para la operación; secondB: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean segmentsCross(RoutePointDesign firstA, RoutePointDesign secondA, RoutePointDesign firstB, RoutePointDesign secondB) {
        boolean firstHorizontal = approximately(firstA.y(), secondA.y());
        boolean secondHorizontal = approximately(firstB.y(), secondB.y());

        if(firstHorizontal == secondHorizontal) {
            return false;
        }

        RoutePointDesign horizontalStart = firstHorizontal ? firstA : firstB;
        RoutePointDesign horizontalEnd = firstHorizontal ? secondA : secondB;
        RoutePointDesign verticalStart = firstHorizontal ? firstB : firstA;
        RoutePointDesign verticalEnd = firstHorizontal ? secondB : secondA;
        double crossX = verticalStart.x();
        double crossY = horizontalStart.y();
        boolean insideHorizontal = crossX > Math.min(horizontalStart.x(), horizontalEnd.x()) && crossX < Math.max(horizontalStart.x(), horizontalEnd.x());
        boolean insideVertical = crossY > Math.min(verticalStart.y(), verticalEnd.y()) && crossY < Math.max(verticalStart.y(), verticalEnd.y());
        return insideHorizontal && insideVertical;
    }
}
