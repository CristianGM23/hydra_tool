package com.example.editor.metamodel.layout;

import java.util.List;
import java.util.Locale;

/**
 * Configura una operación concreta de layout o encaminamiento.
 *
 * @param algorithm contiene el algoritmo de colocación.
 * @param direction contiene la dirección principal.
 * @param preserveNodeSizes indica si conserva tamaños.
 * @param preserveStyles indica si conserva estilos.
 * @param useAttributeTypesForPlacement acerca tipos a sus clases.
 * @param fixedElementIds contiene los nodos inmóviles.
 * @param mode contiene FULL o ROUTE_ONLY.
 * @param portAssignmentAlgorithm contiene el asignador de puertos.
 * @param edgeRoutingAlgorithm contiene el router.
 * @param labelPlacementAlgorithm contiene el colocador de etiquetas.
 */
public record MetamodelLayoutOptions(String algorithm, String direction, boolean preserveNodeSizes, boolean preserveStyles, boolean useAttributeTypesForPlacement, List<String> fixedElementIds, String mode, String portAssignmentAlgorithm, String edgeRoutingAlgorithm, String labelPlacementAlgorithm) {

    public MetamodelLayoutOptions {
        algorithm = normalize(algorithm, "layered");
        direction = normalizeUpper(direction, "LEFT_TO_RIGHT");
        fixedElementIds = fixedElementIds == null ? List.of() : List.copyOf(fixedElementIds);
        mode = normalizeUpper(mode, "FULL");
        portAssignmentAlgorithm = normalize(portAssignmentAlgorithm, "dynamic");
        edgeRoutingAlgorithm = normalize(edgeRoutingAlgorithm, "orthogonal");
        labelPlacementAlgorithm = normalize(labelPlacementAlgorithm, "longest-segment");
    }

    /** Descripción: Inicializa una instancia de MetamodelLayoutOptions con sus dependencias y datos de entrada. Entradas: algorithm: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación; preserveNodeSizes: dato de entrada necesario para la operación; preserveStyles: dato de entrada necesario para la operación; useAttributeTypesForPlacement: dato de entrada necesario para la operación; fixedElementIds: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelLayoutOptions(String algorithm, String direction, boolean preserveNodeSizes, boolean preserveStyles, boolean useAttributeTypesForPlacement, List<String> fixedElementIds) {
        this(algorithm, direction, preserveNodeSizes, preserveStyles, useAttributeTypesForPlacement, fixedElementIds, "FULL", "dynamic", "orthogonal", "longest-segment");
    }

    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de MetamodelLayoutOptions. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static MetamodelLayoutOptions defaults(MetamodelLayoutProperties properties) {
        return new MetamodelLayoutOptions(properties.getDefaultAlgorithm(), properties.getDefaultDirection(), properties.isPreserveNodeSizes(), properties.isPreserveStyles(), properties.isUseAttributeTypesForPlacement(), List.of(), "FULL", properties.getPortAssignmentAlgorithm(), properties.getEdgeRoutingAlgorithm(), properties.getLabelPlacementAlgorithm());
    }

    /** Descripción: Ejecuta la operación «routeOnly» correspondiente a la responsabilidad de MetamodelLayoutOptions. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean routeOnly() {
        return "ROUTE_ONLY".equals(mode);
    }

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de MetamodelLayoutOptions. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value, String fallback) {
        if(value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** Descripción: Ejecuta la operación «normalizeUpper» correspondiente a la responsabilidad de MetamodelLayoutOptions. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeUpper(String value, String fallback) {
        if(value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }
}
