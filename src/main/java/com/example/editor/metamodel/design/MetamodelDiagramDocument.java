package com.example.editor.metamodel.design;

import java.util.List;

/**
 * Describe la disposición visual completa del diseñador de metamodelos.
 *
 * @param metamodelId identifica el metamodelo.
 * @param nodes contiene posiciones, tamaños y estilos.
 * @param edges contiene puertos, rutas, etiquetas y estilos.
 */
public record MetamodelDiagramDocument(String metamodelId, List<NodeDesign> nodes, List<EdgeDesign> edges) {

    public MetamodelDiagramDocument {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    /**
     * Describe la disposición de una clase, enumeración o tipo de datos.
     *
     * @param elementId identifica el elemento.
     * @param x contiene la posición horizontal.
     * @param y contiene la posición vertical.
     * @param width contiene la anchura.
     * @param height contiene la altura.
     * @param style contiene la personalización visual.
     */
    public record NodeDesign(String elementId, double x, double y, double width, double height, NodeStyleDesign style) {

        /** Descripción: Inicializa una instancia de NodeDesign con sus dependencias y datos de entrada. Entradas: elementId: identificador que delimita el recurso objetivo; x: dato de entrada necesario para la operación; y: dato de entrada necesario para la operación; width: dato de entrada necesario para la operación; height: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public NodeDesign(String elementId, double x, double y, double width, double height) {
            this(elementId, x, y, width, height, null);
        }
    }

    /**
     * Describe una relación visual completa.
     *
     * @param relationId identifica la relación.
     * @param sourceHandle identifica el puerto de salida.
     * @param targetHandle identifica el puerto de entrada.
     * @param kind contiene el tipo visual.
     * @param sourceClassId identifica el elemento de origen.
     * @param targetClassId identifica el elemento de destino.
     * @param label contiene la etiqueta.
     * @param style contiene la personalización visual.
     * @param routePoints contiene la ruta ortogonal completa, incluidos sus extremos.
     * @param labelPosition contiene la posición calculada para la etiqueta.
     */
    public record EdgeDesign(String relationId, String sourceHandle, String targetHandle, String kind, String sourceClassId, String targetClassId, String label, EdgeStyleDesign style, List<RoutePointDesign> routePoints, LabelPositionDesign labelPosition) {

        public EdgeDesign {
            routePoints = routePoints == null ? List.of() : List.copyOf(routePoints);
        }

        /** Descripción: Inicializa una instancia de EdgeDesign con sus dependencias y datos de entrada. Entradas: relationId: identificador que delimita el recurso objetivo; sourceHandle: dato de entrada necesario para la operación; targetHandle: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EdgeDesign(String relationId, String sourceHandle, String targetHandle) {
            this(relationId, sourceHandle, targetHandle, null, null, null, "", null, List.of(), null);
        }

        /** Descripción: Inicializa una instancia de EdgeDesign con sus dependencias y datos de entrada. Entradas: relationId: identificador que delimita el recurso objetivo; sourceHandle: dato de entrada necesario para la operación; targetHandle: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; sourceClassId: identificador que delimita el recurso objetivo; targetClassId: identificador que delimita el recurso objetivo; label: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EdgeDesign(String relationId, String sourceHandle, String targetHandle, String kind, String sourceClassId, String targetClassId, String label) {
            this(relationId, sourceHandle, targetHandle, kind, sourceClassId, targetClassId, label, null, List.of(), null);
        }

        /** Descripción: Inicializa una instancia de EdgeDesign con sus dependencias y datos de entrada. Entradas: relationId: identificador que delimita el recurso objetivo; sourceHandle: dato de entrada necesario para la operación; targetHandle: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; sourceClassId: identificador que delimita el recurso objetivo; targetClassId: identificador que delimita el recurso objetivo; label: dato de entrada necesario para la operación; style: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EdgeDesign(String relationId, String sourceHandle, String targetHandle, String kind, String sourceClassId, String targetClassId, String label, EdgeStyleDesign style) {
            this(relationId, sourceHandle, targetHandle, kind, sourceClassId, targetClassId, label, style, List.of(), null);
        }

        /**
         * Descripción:
         * Crea la misma relación con una ruta nueva.
         *
         * @param newRoutePoints contiene los puntos nuevos.
         * @return la relación actualizada.
         */
        /** Descripción: Ejecuta la operación «withRoutePoints» correspondiente a la responsabilidad de MetamodelDiagramDocument. Entradas: newRoutePoints: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EdgeDesign withRoutePoints(List<RoutePointDesign> newRoutePoints) {
            return new EdgeDesign(relationId, sourceHandle, targetHandle, kind, sourceClassId, targetClassId, label, style, newRoutePoints, labelPosition);
        }

        /**
         * Descripción:
         * Crea la misma relación con una posición de etiqueta nueva.
         *
         * @param newLabelPosition contiene la posición.
         * @return la relación actualizada.
         */
        /** Descripción: Ejecuta la operación «withLabelPosition» correspondiente a la responsabilidad de MetamodelDiagramDocument. Entradas: newLabelPosition: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EdgeDesign withLabelPosition(LabelPositionDesign newLabelPosition) {
            return new EdgeDesign(relationId, sourceHandle, targetHandle, kind, sourceClassId, targetClassId, label, style, routePoints, newLabelPosition);
        }
    }

    /**
     * Describe un punto de la ruta.
     *
     * @param x contiene la coordenada horizontal.
     * @param y contiene la coordenada vertical.
     */
    public record RoutePointDesign(double x, double y) {
    }

    /**
     * Describe la posición calculada de una etiqueta.
     *
     * @param x contiene la coordenada horizontal.
     * @param y contiene la coordenada vertical.
     * @param segmentIndex identifica el segmento utilizado.
     */
    public record LabelPositionDesign(double x, double y, int segmentIndex) {
    }

    /**
     * Describe la apariencia de una figura.
     */
    public record NodeStyleDesign(String fillColor, String headerColor, String strokeColor, String textColor, String fontFamily, Double fontSize, Double fontWeight, Double strokeWidth, Double borderRadius) {
    }

    /**
     * Describe la apariencia de una relación.
     */
    public record EdgeStyleDesign(String strokeColor, String textColor, String fontFamily, Double fontSize, Double fontWeight, Double strokeWidth, String lineStyle) {
    }
}
