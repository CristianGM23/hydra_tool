import type { MetamodelDiagramNode, MetamodelHandleRole } from './types';
import type { MetamodelRoutePoint } from './routingTypes';

/** Lado disponible para conectar una relación. */
export type MetamodelPortSide = 'top' | 'right' | 'bottom' | 'left';

/** Estrategia sencilla utilizada al reconstruir una ruta manual. */
export type OrthogonalRouteStrategy = 'HORIZONTAL_FIRST' | 'VERTICAL_FIRST';

/** Puerto descompuesto en lado y porcentaje. */
export interface MetamodelPortDescriptor {
    side: MetamodelPortSide;
    percent: number;
}

/**
 * Convierte un identificador actual o histórico de puerto en valores editables.
 */
export function parseMetamodelPortHandle(value: string | undefined, role: MetamodelHandleRole): MetamodelPortDescriptor {
    const fallback = role === 'SOURCE' ? 'right-source' : 'left-target';
    const normalized = value === undefined || value === '' ? fallback : value;
    const sideMatch = normalized.match(/^(top|right|bottom|left)/);
    const percentMatch = normalized.match(/-(\d+(?:\.\d+)?)(?:-source|-target)?$/);
    const side = (sideMatch?.[1] ?? (role === 'SOURCE' ? 'right' : 'left')) as MetamodelPortSide;
    let percent = Number(percentMatch?.[1]);

    if(!Number.isFinite(percent)) {
        percent = defaultHandlePercent(normalized, role);
    }

    return { side, percent: Math.max(0, Math.min(100, percent)) };
}

/**
 * Construye un identificador persistible para un puerto dinámico.
 */
export function buildMetamodelPortHandle(side: MetamodelPortSide, percent: number, role: MetamodelHandleRole): string {
    const safePercent = Math.max(0, Math.min(100, percent));
    const formattedPercent = Number(safePercent.toFixed(3)).toString();
    return `${side}-${formattedPercent}-${role === 'SOURCE' ? 'source' : 'target'}`;
}

/**
 * Calcula las coordenadas absolutas de un puerto de un nodo persistido.
 */
export function calculateDiagramHandlePoint(node: MetamodelDiagramNode, handle: string | undefined, role: MetamodelHandleRole): MetamodelRoutePoint {
    const descriptor = parseMetamodelPortHandle(handle, role);
    const ratio = descriptor.percent / 100;

    if(descriptor.side === 'top') {
        return { x: node.x + node.width * ratio, y: node.y };
    }

    if(descriptor.side === 'right') {
        return { x: node.x + node.width, y: node.y + node.height * ratio };
    }

    if(descriptor.side === 'bottom') {
        return { x: node.x + node.width * ratio, y: node.y + node.height };
    }

    return { x: node.x, y: node.y + node.height * ratio };
}

/**
 * Crea una ruta ortogonal inicial respetando el lado de salida y el lado de entrada.
 */
export function createEditableOrthogonalRoute(sourceNode: MetamodelDiagramNode, targetNode: MetamodelDiagramNode, sourceHandle: string | undefined, targetHandle: string | undefined, strategy: OrthogonalRouteStrategy = 'HORIZONTAL_FIRST'): MetamodelRoutePoint[] {
    const source = calculateDiagramHandlePoint(sourceNode, sourceHandle, 'SOURCE');
    const target = calculateDiagramHandlePoint(targetNode, targetHandle, 'TARGET');
    const sourceSide = parseMetamodelPortHandle(sourceHandle, 'SOURCE').side;
    const targetSide = parseMetamodelPortHandle(targetHandle, 'TARGET').side;
    const clearance = 36;
    const sourceExit = offsetPoint(source, sourceSide, clearance);
    const targetEntry = offsetPoint(target, targetSide, clearance);
    const middle = connectOrthogonally(sourceExit, targetEntry, strategy);
    return simplifyOrthogonalRoute([source, sourceExit, ...middle.slice(1, -1), targetEntry, target]);
}

/**
 * Reconstruye una ruta ortogonal pasando por los puntos indicados por el usuario.
 * Cuando dos puntos no están alineados se añade automáticamente una esquina intermedia.
 */
export function normalizeEditableOrthogonalRoute(points: MetamodelRoutePoint[], strategy: OrthogonalRouteStrategy = 'HORIZONTAL_FIRST'): MetamodelRoutePoint[] {
    if(points.length < 2) {
        return [...points];
    }

    const result: MetamodelRoutePoint[] = [points[0]];

    for(let index = 1; index < points.length; index += 1) {
        const current = result[result.length - 1];
        const target = points[index];
        const connection = connectOrthogonally(current, target, strategy);
        result.push(...connection.slice(1));
    }

    return simplifyOrthogonalRoute(result);
}


/**
 * Mueve un punto interior de una ruta ortogonal sin convertir sus segmentos en diagonales.
 * Los puntos vecinos se desplazan cuando es necesario para conservar la forma horizontal o vertical.
 */
export function moveEditableOrthogonalRoutePoint(points: MetamodelRoutePoint[], internalIndex: number, target: MetamodelRoutePoint): MetamodelRoutePoint[] {
    if(points.length < 3 || internalIndex < 0 || internalIndex >= points.length - 2) {
        return points.map(point => ({ ...point }));
    }

    const result = points.map(point => ({ ...point }));
    const pointIndex = internalIndex + 1;
    const previousIndex = pointIndex - 1;
    const nextIndex = pointIndex + 1;
    const previous = result[previousIndex];
    const current = result[pointIndex];
    const next = result[nextIndex];
    const incoming = readSegmentOrientation(previous, current);
    const outgoing = readSegmentOrientation(current, next);

    if(incoming === 'HORIZONTAL' && outgoing === 'VERTICAL') {
        current.x = nextIndex === result.length - 1 ? next.x : target.x;
        current.y = previousIndex === 0 ? previous.y : target.y;

        if(previousIndex > 0) {
            previous.y = current.y;
        }

        if(nextIndex < result.length - 1) {
            next.x = current.x;
        }

        return result;
    }

    if(incoming === 'VERTICAL' && outgoing === 'HORIZONTAL') {
        current.x = previousIndex === 0 ? previous.x : target.x;
        current.y = nextIndex === result.length - 1 ? next.y : target.y;

        if(previousIndex > 0) {
            previous.x = current.x;
        }

        if(nextIndex < result.length - 1) {
            next.y = current.y;
        }

        return result;
    }

    if(incoming === 'HORIZONTAL' && outgoing === 'HORIZONTAL') {
        current.x = target.x;
        current.y = previous.y;
        return result;
    }

    if(incoming === 'VERTICAL' && outgoing === 'VERTICAL') {
        current.x = previous.x;
        current.y = target.y;
        return result;
    }

    return normalizeEditableOrthogonalRoute([result[0], ...result.slice(1, -1).map((point, index) => index === internalIndex ? { ...target } : point), result[result.length - 1]]);
}

/**
 * Comprueba que cada segmento de una ruta es horizontal o vertical.
 */
export function isOrthogonalRoute(points: MetamodelRoutePoint[]): boolean {
    for(let index = 0; index < points.length - 1; index += 1) {
        if(!sameCoordinate(points[index].x, points[index + 1].x) && !sameCoordinate(points[index].y, points[index + 1].y)) {
            return false;
        }
    }

    return true;
}

/**
 * Devuelve los puntos interiores, excluyendo los extremos ligados a los puertos.
 */
export function readInternalRoutePoints(points: MetamodelRoutePoint[]): MetamodelRoutePoint[] {
    if(points.length <= 2) {
        return [];
    }

    return points.slice(1, -1);
}


type SegmentOrientation = 'HORIZONTAL' | 'VERTICAL' | 'DIAGONAL';

function readSegmentOrientation(source: MetamodelRoutePoint, target: MetamodelRoutePoint): SegmentOrientation {
    if(sameCoordinate(source.y, target.y)) {
        return 'HORIZONTAL';
    }

    if(sameCoordinate(source.x, target.x)) {
        return 'VERTICAL';
    }

    return 'DIAGONAL';
}

function connectOrthogonally(source: MetamodelRoutePoint, target: MetamodelRoutePoint, strategy: OrthogonalRouteStrategy): MetamodelRoutePoint[] {
    if(sameCoordinate(source.x, target.x) || sameCoordinate(source.y, target.y)) {
        return [source, target];
    }

    if(strategy === 'VERTICAL_FIRST') {
        return [source, { x: source.x, y: target.y }, target];
    }

    return [source, { x: target.x, y: source.y }, target];
}

function simplifyOrthogonalRoute(points: MetamodelRoutePoint[]): MetamodelRoutePoint[] {
    const unique: MetamodelRoutePoint[] = [];

    for(const point of points) {
        const previous = unique[unique.length - 1];

        if(previous === undefined || !sameCoordinate(previous.x, point.x) || !sameCoordinate(previous.y, point.y)) {
            unique.push({ x: point.x, y: point.y });
        }
    }

    if(unique.length <= 2) {
        return unique;
    }

    const simplified: MetamodelRoutePoint[] = [unique[0]];

    for(let index = 1; index < unique.length - 1; index += 1) {
        const previous = simplified[simplified.length - 1];
        const current = unique[index];
        const next = unique[index + 1];
        const sameVerticalLine = sameCoordinate(previous.x, current.x) && sameCoordinate(current.x, next.x);
        const sameHorizontalLine = sameCoordinate(previous.y, current.y) && sameCoordinate(current.y, next.y);

        if(!sameVerticalLine && !sameHorizontalLine) {
            simplified.push(current);
        }
    }

    simplified.push(unique[unique.length - 1]);
    return simplified;
}

function offsetPoint(point: MetamodelRoutePoint, side: MetamodelPortSide, distance: number): MetamodelRoutePoint {
    if(side === 'top') {
        return { x: point.x, y: point.y - distance };
    }

    if(side === 'right') {
        return { x: point.x + distance, y: point.y };
    }

    if(side === 'bottom') {
        return { x: point.x, y: point.y + distance };
    }

    return { x: point.x - distance, y: point.y };
}

function defaultHandlePercent(handle: string, role: MetamodelHandleRole): number {
    if(handle === 'top-source' || handle === 'right-source') {
        return 25;
    }

    if(handle === 'bottom-source' || handle === 'left-source') {
        return 75;
    }

    if(handle === 'top-target' || handle === 'right-target') {
        return 75;
    }

    if(handle === 'bottom-target' || handle === 'left-target') {
        return 25;
    }

    return role === 'SOURCE' ? 25 : 75;
}

function sameCoordinate(first: number, second: number): boolean {
    return Math.abs(first - second) < 0.0001;
}
