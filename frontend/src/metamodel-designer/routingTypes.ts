import type { MetamodelHandleRole } from './types';

/**
 * Describe un punto de una ruta ortogonal.
 */
export interface MetamodelRoutePoint {
    x: number;
    y: number;
}

/**
 * Describe la posición persistida de una etiqueta.
 */
export interface MetamodelEdgeLabelPosition {
    x: number;
    y: number;
    segmentIndex: number;
}

/**
 * Describe un puerto visible en un nodo.
 */
export interface MetamodelNodePort {
    id: string;
    side: 'top' | 'right' | 'bottom' | 'left';
    percent: number;
    role: MetamodelHandleRole;
}

declare module './types' {
    interface MetamodelDiagramEdge {
        routePoints?: MetamodelRoutePoint[];
        labelPosition?: MetamodelEdgeLabelPosition;
    }
}
