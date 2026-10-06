package com.example.editor.notation;

import java.util.List;

/**
 * Describe declarativamente la representación gráfica de un metamodelo.
 *
 * @param id identificador de la notación.
 * @param metamodelId identificador del metamodelo.
 * @param name nombre visible de la notación.
 * @param nodeStyles representaciones disponibles para los nodos.
 * @param edgeStyles representaciones disponibles para las relaciones.
 */
public record NotationDefinition(String id, String metamodelId, String name, List<NodeStyle> nodeStyles, List<EdgeStyle> edgeStyles) {

    /**
     * Describe una representación visual de una EClass.
     *
     * @param id identificador de la representación.
     * @param classId identificador completo de la EClass.
     * @param representationName nombre visible de la representación.
     * @param shape forma utilizada.
     * @param defaultWidth anchura inicial.
     * @param defaultHeight altura inicial.
     * @param minWidth anchura mínima.
     * @param minHeight altura mínima.
     * @param fillColor color interior.
     * @param strokeColor color del borde.
     * @param strokeWidth grosor del borde.
     * @param strokeDash patrón discontinuo del borde.
     * @param borderRadius radio de las esquinas.
     * @param labelAttribute atributo utilizado como etiqueta.
     * @param svgViewBox viewBox utilizado por una ruta SVG personalizada.
     * @param svgPath ruta SVG personalizada.
     */
    public record NodeStyle(String id, String classId, String representationName, String shape, double defaultWidth, double defaultHeight, double minWidth, double minHeight, String fillColor, String strokeColor, double strokeWidth, String strokeDash, double borderRadius, String labelAttribute, String svgViewBox, String svgPath) {
    }

    /**
     * Describe la representación visual de una EReference.
     *
     * @param id identificador del estilo.
     * @param sourceClassId identificador de la clase de origen.
     * @param referenceName nombre de la referencia.
     * @param targetClassId identificador de la clase de destino.
     * @param lineType tipo de trazado.
     * @param strokeColor color de la línea.
     * @param strokeWidth grosor de la línea.
     * @param strokeDash patrón discontinuo.
     * @param startMarker forma del extremo inicial.
     * @param endMarker forma del extremo final.
     * @param animated indica si la línea está animada.
     */
    public record EdgeStyle(String id, String sourceClassId, String referenceName, String targetClassId, String lineType, String strokeColor, double strokeWidth, String strokeDash, String startMarker, String endMarker, boolean animated) {
    }
}