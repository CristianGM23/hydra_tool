package com.example.editor.metamodel.layout;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configura la colocación, los puertos y el encaminamiento de metamodelos.
 */
@Component
@ConfigurationProperties(prefix = "dsl.layout.metamodel")
public class MetamodelLayoutProperties {

    private boolean enabled = true;
    private String defaultAlgorithm = "layered";
    private String defaultDirection = "LEFT_TO_RIGHT";
    private String portAssignmentAlgorithm = "dynamic";
    private String edgeRoutingAlgorithm = "orthogonal";
    private String labelPlacementAlgorithm = "longest-segment";
    private double margin = 60;
    private double layerGap = 180;
    private double nodeGap = 90;
    private double componentGap = 180;
    private int crossingReductionPasses = 12;
    private int isolatedNodeColumns = 4;
    private boolean preserveNodeSizes = true;
    private boolean preserveStyles = true;
    private boolean useAttributeTypesForPlacement = true;
    private double portMargin = 18;
    private double minimumPortGap = 14;
    private int maximumPortsPerSide = 24;
    private double routingGridSize = 20;
    private double routingNodeClearance = 24;
    private double routingEdgeGap = 8;
    private double routingTurnCost = 25;
    private double routingCrossingCost = 600;
    private double routingCongestionCost = 120;
    private int routingMaximumExpandedStates = 250000;
    private boolean parallelRouting = true;
    private double labelClearance = 8;

    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled() {
        return enabled;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setEnabled». Entradas: enabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Descripción: Obtiene la información correspondiente a «getDefaultAlgorithm» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getDefaultAlgorithm() {
        return defaultAlgorithm;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setDefaultAlgorithm». Entradas: defaultAlgorithm: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDefaultAlgorithm(String defaultAlgorithm) {
        this.defaultAlgorithm = defaultAlgorithm;
    }

    /** Descripción: Obtiene la información correspondiente a «getDefaultDirection» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getDefaultDirection() {
        return defaultDirection;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setDefaultDirection». Entradas: defaultDirection: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDefaultDirection(String defaultDirection) {
        this.defaultDirection = defaultDirection;
    }

    /** Descripción: Obtiene la información correspondiente a «getPortAssignmentAlgorithm» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getPortAssignmentAlgorithm() {
        return portAssignmentAlgorithm;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setPortAssignmentAlgorithm». Entradas: portAssignmentAlgorithm: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setPortAssignmentAlgorithm(String portAssignmentAlgorithm) {
        this.portAssignmentAlgorithm = portAssignmentAlgorithm;
    }

    /** Descripción: Obtiene la información correspondiente a «getEdgeRoutingAlgorithm» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getEdgeRoutingAlgorithm() {
        return edgeRoutingAlgorithm;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setEdgeRoutingAlgorithm». Entradas: edgeRoutingAlgorithm: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEdgeRoutingAlgorithm(String edgeRoutingAlgorithm) {
        this.edgeRoutingAlgorithm = edgeRoutingAlgorithm;
    }

    /** Descripción: Obtiene la información correspondiente a «getLabelPlacementAlgorithm» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getLabelPlacementAlgorithm() {
        return labelPlacementAlgorithm;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setLabelPlacementAlgorithm». Entradas: labelPlacementAlgorithm: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setLabelPlacementAlgorithm(String labelPlacementAlgorithm) {
        this.labelPlacementAlgorithm = labelPlacementAlgorithm;
    }

    /** Descripción: Obtiene la información correspondiente a «getMargin» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getMargin() {
        return margin;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setMargin». Entradas: margin: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMargin(double margin) {
        this.margin = margin;
    }

    /** Descripción: Obtiene la información correspondiente a «getLayerGap» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getLayerGap() {
        return layerGap;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setLayerGap». Entradas: layerGap: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setLayerGap(double layerGap) {
        this.layerGap = layerGap;
    }

    /** Descripción: Obtiene la información correspondiente a «getNodeGap» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getNodeGap() {
        return nodeGap;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setNodeGap». Entradas: nodeGap: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setNodeGap(double nodeGap) {
        this.nodeGap = nodeGap;
    }

    /** Descripción: Obtiene la información correspondiente a «getComponentGap» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getComponentGap() {
        return componentGap;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setComponentGap». Entradas: componentGap: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setComponentGap(double componentGap) {
        this.componentGap = componentGap;
    }

    /** Descripción: Obtiene la información correspondiente a «getCrossingReductionPasses» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getCrossingReductionPasses() {
        return crossingReductionPasses;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setCrossingReductionPasses». Entradas: crossingReductionPasses: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setCrossingReductionPasses(int crossingReductionPasses) {
        this.crossingReductionPasses = crossingReductionPasses;
    }

    /** Descripción: Obtiene la información correspondiente a «getIsolatedNodeColumns» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getIsolatedNodeColumns() {
        return isolatedNodeColumns;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setIsolatedNodeColumns». Entradas: isolatedNodeColumns: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setIsolatedNodeColumns(int isolatedNodeColumns) {
        this.isolatedNodeColumns = isolatedNodeColumns;
    }

    /** Descripción: Evalúa la condición «isPreserveNodeSizes» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isPreserveNodeSizes() {
        return preserveNodeSizes;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setPreserveNodeSizes». Entradas: preserveNodeSizes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setPreserveNodeSizes(boolean preserveNodeSizes) {
        this.preserveNodeSizes = preserveNodeSizes;
    }

    /** Descripción: Evalúa la condición «isPreserveStyles» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isPreserveStyles() {
        return preserveStyles;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setPreserveStyles». Entradas: preserveStyles: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setPreserveStyles(boolean preserveStyles) {
        this.preserveStyles = preserveStyles;
    }

    /** Descripción: Evalúa la condición «isUseAttributeTypesForPlacement» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isUseAttributeTypesForPlacement() {
        return useAttributeTypesForPlacement;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setUseAttributeTypesForPlacement». Entradas: useAttributeTypesForPlacement: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setUseAttributeTypesForPlacement(boolean useAttributeTypesForPlacement) {
        this.useAttributeTypesForPlacement = useAttributeTypesForPlacement;
    }

    /** Descripción: Obtiene la información correspondiente a «getPortMargin» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getPortMargin() {
        return portMargin;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setPortMargin». Entradas: portMargin: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setPortMargin(double portMargin) {
        this.portMargin = portMargin;
    }

    /** Descripción: Obtiene la información correspondiente a «getMinimumPortGap» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getMinimumPortGap() {
        return minimumPortGap;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setMinimumPortGap». Entradas: minimumPortGap: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMinimumPortGap(double minimumPortGap) {
        this.minimumPortGap = minimumPortGap;
    }

    /** Descripción: Obtiene la información correspondiente a «getMaximumPortsPerSide» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumPortsPerSide() {
        return maximumPortsPerSide;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setMaximumPortsPerSide». Entradas: maximumPortsPerSide: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumPortsPerSide(int maximumPortsPerSide) {
        this.maximumPortsPerSide = maximumPortsPerSide;
    }

    /** Descripción: Obtiene la información correspondiente a «getRoutingGridSize» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getRoutingGridSize() {
        return routingGridSize;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setRoutingGridSize». Entradas: routingGridSize: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRoutingGridSize(double routingGridSize) {
        this.routingGridSize = routingGridSize;
    }

    /** Descripción: Obtiene la información correspondiente a «getRoutingNodeClearance» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getRoutingNodeClearance() {
        return routingNodeClearance;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setRoutingNodeClearance». Entradas: routingNodeClearance: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRoutingNodeClearance(double routingNodeClearance) {
        this.routingNodeClearance = routingNodeClearance;
    }

    /** Descripción: Obtiene la información correspondiente a «getRoutingEdgeGap» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getRoutingEdgeGap() {
        return routingEdgeGap;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setRoutingEdgeGap». Entradas: routingEdgeGap: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRoutingEdgeGap(double routingEdgeGap) {
        this.routingEdgeGap = routingEdgeGap;
    }

    /** Descripción: Obtiene la información correspondiente a «getRoutingTurnCost» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getRoutingTurnCost() {
        return routingTurnCost;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setRoutingTurnCost». Entradas: routingTurnCost: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRoutingTurnCost(double routingTurnCost) {
        this.routingTurnCost = routingTurnCost;
    }

    /** Descripción: Obtiene la información correspondiente a «getRoutingCrossingCost» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getRoutingCrossingCost() {
        return routingCrossingCost;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setRoutingCrossingCost». Entradas: routingCrossingCost: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRoutingCrossingCost(double routingCrossingCost) {
        this.routingCrossingCost = routingCrossingCost;
    }

    /** Descripción: Obtiene la información correspondiente a «getRoutingCongestionCost» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getRoutingCongestionCost() {
        return routingCongestionCost;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setRoutingCongestionCost». Entradas: routingCongestionCost: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRoutingCongestionCost(double routingCongestionCost) {
        this.routingCongestionCost = routingCongestionCost;
    }

    /** Descripción: Obtiene la información correspondiente a «getRoutingMaximumExpandedStates» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getRoutingMaximumExpandedStates() {
        return routingMaximumExpandedStates;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setRoutingMaximumExpandedStates». Entradas: routingMaximumExpandedStates: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRoutingMaximumExpandedStates(int routingMaximumExpandedStates) {
        this.routingMaximumExpandedStates = routingMaximumExpandedStates;
    }

    /** Descripción: Evalúa la condición «isParallelRouting» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isParallelRouting() {
        return parallelRouting;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setParallelRouting». Entradas: parallelRouting: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setParallelRouting(boolean parallelRouting) {
        this.parallelRouting = parallelRouting;
    }

    /** Descripción: Obtiene la información correspondiente a «getLabelClearance» dentro de MetamodelLayoutProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getLabelClearance() {
        return labelClearance;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelLayoutProperties mediante la operación «setLabelClearance». Entradas: labelClearance: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setLabelClearance(double labelClearance) {
        this.labelClearance = labelClearance;
    }

}
