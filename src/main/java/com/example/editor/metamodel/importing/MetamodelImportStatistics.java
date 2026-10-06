package com.example.editor.metamodel.importing;

/**
 * Resume los elementos reconstruidos.
 *
 * @param classes contiene el número de clases.
 * @param enums contiene el número de enumeraciones.
 * @param dataTypes contiene el número de tipos personalizados.
 * @param attributes contiene el número de atributos.
 * @param relations contiene el número de relaciones.
 * @param diagramNodes contiene el número de nodos visuales.
 * @param diagramEdges contiene el número de conexiones visuales.
 */
public record MetamodelImportStatistics(int classes, int enums, int dataTypes, int attributes, int relations, int diagramNodes, int diagramEdges) {
}
