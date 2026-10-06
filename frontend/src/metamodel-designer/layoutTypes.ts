import type { MetaModel, MetamodelDiagramDocument } from './types';

/**
 * Define la dirección principal del layout.
 */
export type MetamodelLayoutDirection = 'LEFT_TO_RIGHT' | 'TOP_TO_BOTTOM';

/**
 * Configura una operación de layout.
 */
export interface MetamodelLayoutOptions {
    algorithm: string;
    direction: MetamodelLayoutDirection;
    preserveNodeSizes: boolean;
    preserveStyles: boolean;
    useAttributeTypesForPlacement: boolean;
    fixedElementIds: string[];
    mode?: 'FULL' | 'ROUTE_ONLY';
    portAssignmentAlgorithm?: string;
    edgeRoutingAlgorithm?: string;
    labelPlacementAlgorithm?: string;
}

/**
 * Describe una petición de layout.
 */
export interface MetamodelLayoutRequest {
    metamodel: MetaModel;
    diagram: MetamodelDiagramDocument;
    options: MetamodelLayoutOptions;
}

/**
 * Describe una advertencia del layout.
 */
export interface MetamodelLayoutWarning {
    code: string;
    path: string;
    message: string;
}

/**
 * Resume el resultado del layout.
 */
export interface MetamodelLayoutStatistics {
    nodes: number;
    edges: number;
    components: number;
    layers: number;
    cycles: number;
    crossings?: number;
    edgeNodeIntersections?: number;
    bends?: number;
    totalLength?: number;
    maximumPortCongestion?: number;
}

/**
 * Contiene el diagrama organizado.
 */
export interface MetamodelLayoutResponse {
    diagram: MetamodelDiagramDocument;
    warnings: MetamodelLayoutWarning[];
    statistics: MetamodelLayoutStatistics;
}
