import type { Node } from '@xyflow/react';

import type { MetamodelEdgeLabelPosition, MetamodelRoutePoint } from './routingTypes';

/** Coordenada absoluta del lienzo. */
export interface MetamodelRelationPoint {
    x: number;
    y: number;
}

/** Trazado calculado de una relación y posición de su etiqueta. */
export interface MetamodelRelationGeometry {
    path: string;
    labelX: number;
    labelY: number;
    points: MetamodelRelationPoint[];
}

/**
 * Construye una ruta ortogonal persistida o una curva normal entre dos puntos.
 */
export function buildRelationGeometry(source: MetamodelRelationPoint, target: MetamodelRelationPoint, routePoints: MetamodelRoutePoint[] = [], labelPosition?: MetamodelEdgeLabelPosition): MetamodelRelationGeometry {
    if(routePoints.length >= 2) {
        const adjustedRoute = routePoints.map((point, index) => {
            if(index === 0) {
                return source;
            }

            if(index === routePoints.length - 1) {
                return target;
            }

            return { ...point };
        });
        alignOrthogonalEndpoints(adjustedRoute, routePoints, source, target);
        const path = adjustedRoute.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ');
        const fallbackLabel = longestSegmentLabel(adjustedRoute);
        return { path, labelX: labelPosition?.x ?? fallbackLabel.x, labelY: labelPosition?.y ?? fallbackLabel.y, points: adjustedRoute };
    }

    const distanceX = target.x - source.x;
    const distanceY = target.y - source.y;
    const horizontal = Math.abs(distanceX) >= Math.abs(distanceY);
    let control1X = source.x;
    let control1Y = source.y;
    let control2X = target.x;
    let control2Y = target.y;

    if(horizontal) {
        const offset = Math.max(50, Math.abs(distanceX) * 0.45);
        control1X += distanceX >= 0 ? offset : -offset;
        control2X -= distanceX >= 0 ? offset : -offset;
    } else {
        const offset = Math.max(50, Math.abs(distanceY) * 0.45);
        control1Y += distanceY >= 0 ? offset : -offset;
        control2Y -= distanceY >= 0 ? offset : -offset;
    }

    const path = `M ${source.x} ${source.y} C ${control1X} ${control1Y}, ${control2X} ${control2Y}, ${target.x} ${target.y}`;
    return { path, labelX: cubicCoordinate(source.x, control1X, control2X, target.x, 0.5), labelY: cubicCoordinate(source.y, control1Y, control2Y, target.y, 0.5), points: [] };
}

/**
 * Calcula la posición absoluta de un puerto de React Flow, incluidos los puertos dinámicos expresados como porcentaje.
 */
export function calculateHandlePoint(node: Node, handleId: string | null | undefined, source: boolean): MetamodelRelationPoint {
    const width = readDimension(node.measured?.width, node.width, node.style?.width, 240);
    const height = readDimension(node.measured?.height, node.height, node.style?.height, 130);
    const normalizedHandle = normalizeHandleId(handleId, source);
    const side = readHandleSide(normalizedHandle);
    const percent = readHandlePercent(normalizedHandle, source);
    const ratio = Math.max(0, Math.min(100, percent)) / 100;
    const x = node.position.x;
    const y = node.position.y;

    if(side === 'top') {
        return { x: x + width * ratio, y };
    }

    if(side === 'right') {
        return { x: x + width, y: y + height * ratio };
    }

    if(side === 'bottom') {
        return { x: x + width * ratio, y: y + height };
    }

    return { x, y: y + height * ratio };
}


/**
 * Mantiene horizontales o verticales los segmentos extremos cuando un nodo cambia de tamaño o de posición.
 * La orientación se toma de la ruta persistida para no alterar los puntos interiores elegidos por el usuario.
 */
function alignOrthogonalEndpoints(adjustedRoute: MetamodelRelationPoint[], storedRoute: MetamodelRoutePoint[], source: MetamodelRelationPoint, target: MetamodelRelationPoint): void {
    if(adjustedRoute.length < 3 || storedRoute.length < 3) {
        return;
    }

    const storedSource = storedRoute[0];
    const storedSecond = storedRoute[1];
    const second = adjustedRoute[1];

    if(Math.abs(storedSource.y - storedSecond.y) < 0.0001) {
        second.y = source.y;
    } else if(Math.abs(storedSource.x - storedSecond.x) < 0.0001) {
        second.x = source.x;
    }

    const lastIndex = adjustedRoute.length - 1;
    const storedBeforeTarget = storedRoute[storedRoute.length - 2];
    const storedTarget = storedRoute[storedRoute.length - 1];
    const beforeTarget = adjustedRoute[lastIndex - 1];

    if(Math.abs(storedBeforeTarget.y - storedTarget.y) < 0.0001) {
        beforeTarget.y = target.y;
    } else if(Math.abs(storedBeforeTarget.x - storedTarget.x) < 0.0001) {
        beforeTarget.x = target.x;
    }
}

function longestSegmentLabel(points: MetamodelRelationPoint[]): MetamodelRelationPoint {
    let bestIndex = 0;
    let bestLength = -1;

    for(let index = 0; index < points.length - 1; index += 1) {
        const length = Math.abs(points[index + 1].x - points[index].x) + Math.abs(points[index + 1].y - points[index].y);

        if(length > bestLength) {
            bestLength = length;
            bestIndex = index;
        }
    }

    return { x: (points[bestIndex].x + points[bestIndex + 1].x) / 2, y: (points[bestIndex].y + points[bestIndex + 1].y) / 2 - 8 };
}

function readHandleSide(handleId: string): 'top' | 'right' | 'bottom' | 'left' {
    if(handleId.startsWith('top')) {
        return 'top';
    }

    if(handleId.startsWith('right')) {
        return 'right';
    }

    if(handleId.startsWith('bottom')) {
        return 'bottom';
    }

    return 'left';
}

function readHandlePercent(handleId: string, source: boolean): number {
    const match = handleId.match(/-(\d+(?:\.\d+)?)(?:-source|-target)?$/);

    if(match !== null) {
        return Number(match[1]);
    }

    if(handleId === 'top-source' || handleId === 'right-source') {
        return 25;
    }

    if(handleId === 'bottom-source' || handleId === 'left-source') {
        return 75;
    }

    if(handleId === 'top-target' || handleId === 'right-target') {
        return 75;
    }

    if(handleId === 'bottom-target' || handleId === 'left-target') {
        return 25;
    }

    return source ? 50 : 50;
}

function normalizeHandleId(handleId: string | null | undefined, source: boolean): string {
    if(handleId === null || handleId === undefined || handleId === '') {
        return source ? 'right-source' : 'left-target';
    }

    if(/^(top|right|bottom|left)-\d+(\.\d+)?(?:-source|-target)?$/.test(handleId)) {
        return handleId;
    }

    if(handleId === 'top-source' || handleId === 'top-target' || handleId === 'right-source' || handleId === 'right-target' || handleId === 'bottom-source' || handleId === 'bottom-target' || handleId === 'left-source' || handleId === 'left-target') {
        return handleId;
    }

    if(handleId.includes('top')) {
        return source ? 'top-source' : 'top-target';
    }

    if(handleId.includes('right')) {
        return source ? 'right-source' : 'right-target';
    }

    if(handleId.includes('bottom')) {
        return source ? 'bottom-source' : 'bottom-target';
    }

    if(handleId.includes('left')) {
        return source ? 'left-source' : 'left-target';
    }

    return source ? 'right-source' : 'left-target';
}

function readDimension(measured: number | undefined, direct: number | undefined, styleValue: string | number | undefined, fallback: number): number {
    if(measured !== undefined && Number.isFinite(measured) && measured > 0) {
        return measured;
    }

    if(direct !== undefined && Number.isFinite(direct) && direct > 0) {
        return direct;
    }

    if(typeof styleValue === 'number' && Number.isFinite(styleValue) && styleValue > 0) {
        return styleValue;
    }

    if(typeof styleValue === 'string') {
        const parsedValue = Number.parseFloat(styleValue);

        if(Number.isFinite(parsedValue) && parsedValue > 0) {
            return parsedValue;
        }
    }

    return fallback;
}

function cubicCoordinate(start: number, control1: number, control2: number, end: number, t: number): number {
    const inverse = 1 - t;
    return inverse * inverse * inverse * start + 3 * inverse * inverse * t * control1 + 3 * inverse * t * t * control2 + t * t * t * end;
}
