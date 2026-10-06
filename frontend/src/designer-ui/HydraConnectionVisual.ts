import { Position } from '@xyflow/react';
import type { NodeHandle } from '@xyflow/react';

export type HydraConnectionRouteStyle = 'ORTHOGONAL' | 'SMOOTH' | 'STRAIGHT';
export type HydraConnectionLineStyle = 'SOLID' | 'DASHED' | 'DOTTED' | 'LONG_DASH' | 'DASH_DOT' | 'NONE';

/**
 * Built-in endpoint markers. The descriptor registry is the single extension
 * point used by editors, validation contracts and SVG rendering.
 */
export const HYDRA_CONNECTION_TERMINATOR_DEFINITIONS = [
    { value:'NONE', label:'No end marker', category:'Simple', description:'Leaves this end of the connection completely unmarked.' },
    { value:'ARROW_OPEN', label:'Open arrow', category:'Arrows', description:'Open directional arrow without a filled interior.' },
    { value:'ARROW_CLOSED', label:'Filled arrow', category:'Arrows', description:'Filled directional arrow for a strong direction cue.' },
    { value:'DOUBLE_ARROW_OPEN', label:'Double open arrow', category:'Arrows', description:'Two open chevrons for a stronger directional cue.' },
    { value:'DOUBLE_ARROW_CLOSED', label:'Double filled arrow', category:'Arrows', description:'Two filled arrowheads for emphasized direction.' },
    { value:'TRIANGLE_OPEN', label:'Open triangle', category:'Geometry', description:'Outlined triangle, useful for specialization or generalization.' },
    { value:'TRIANGLE_CLOSED', label:'Filled triangle', category:'Geometry', description:'Solid triangular endpoint.' },
    { value:'DIAMOND_OPEN', label:'Open diamond', category:'Geometry', description:'Outlined diamond, commonly used for aggregation-like semantics.' },
    { value:'DIAMOND_CLOSED', label:'Filled diamond', category:'Geometry', description:'Solid diamond, commonly used for composition-like semantics.' },
    { value:'CIRCLE_OPEN', label:'Open circle', category:'Geometry', description:'Outlined circular endpoint.' },
    { value:'CIRCLE_CLOSED', label:'Filled circle', category:'Geometry', description:'Solid circular endpoint.' },
    { value:'SQUARE_OPEN', label:'Open square', category:'Geometry', description:'Outlined square endpoint.' },
    { value:'SQUARE_CLOSED', label:'Filled square', category:'Geometry', description:'Solid square endpoint.' },
    { value:'PENTAGON_OPEN', label:'Open pentagon', category:'Geometry', description:'Outlined five-sided endpoint for specialized notations.' },
    { value:'PENTAGON_CLOSED', label:'Filled pentagon', category:'Geometry', description:'Solid five-sided endpoint for specialized notations.' },
    { value:'HEXAGON_OPEN', label:'Open hexagon', category:'Geometry', description:'Outlined six-sided endpoint for specialized notations.' },
    { value:'HEXAGON_CLOSED', label:'Filled hexagon', category:'Geometry', description:'Solid six-sided endpoint for specialized notations.' },
    { value:'OCTAGON_OPEN', label:'Open octagon', category:'Geometry', description:'Outlined eight-sided endpoint.' },
    { value:'OCTAGON_CLOSED', label:'Filled octagon', category:'Geometry', description:'Solid eight-sided endpoint.' },
    { value:'CROSS_OPEN', label:'Open cross', category:'Geometry', description:'Outlined cross-shaped endpoint.' },
    { value:'CROSS_CLOSED', label:'Filled cross', category:'Geometry', description:'Solid cross-shaped endpoint.' },
    { value:'BAR', label:'Bar', category:'Cardinality', description:'Single perpendicular bar endpoint.' },
    { value:'DOUBLE_BAR', label:'Double bar', category:'Cardinality', description:'Two perpendicular bars for mandatory-one style cardinality.' },
    { value:'CROW_FOOT', label:"Crow's foot", category:'Cardinality', description:'Three-pronged many-style cardinality marker.' },
    { value:'CROW_FOOT_OPTIONAL', label:"Optional crow's foot", category:'Cardinality', description:'Circle plus crow’s foot for optional-many semantics.' },
    { value:'CROW_FOOT_REQUIRED', label:"Required crow's foot", category:'Cardinality', description:'Bar plus crow’s foot for required-many semantics.' }
] as const;

export type HydraConnectionTerminator = typeof HYDRA_CONNECTION_TERMINATOR_DEFINITIONS[number]['value'];
export const HYDRA_CONNECTION_TERMINATORS: readonly HydraConnectionTerminator[] = HYDRA_CONNECTION_TERMINATOR_DEFINITIONS.map(item => item.value);

export interface HydraPersistedConnectionAppearance {
    routeStyle?: HydraConnectionRouteStyle;
    lineStyle?: HydraConnectionLineStyle;
    lineColor?: string;
    lineWidth?: number;
    lineOpacity?: number;
    lineBorderColor?: string;
    lineBorderWidth?: number;
    sourceTerminator?: HydraConnectionTerminator;
    targetTerminator?: HydraConnectionTerminator;
    sourceTerminatorFillColor?: string;
    sourceTerminatorBorderColor?: string;
    sourceTerminatorBorderWidth?: number;
    sourceTerminatorSize?: number;
    targetTerminatorFillColor?: string;
    targetTerminatorBorderColor?: string;
    targetTerminatorBorderWidth?: number;
    targetTerminatorSize?: number;
}

export interface HydraConnectionVisualData extends Record<string, unknown>, HydraPersistedConnectionAppearance {
    connectionLabel?: string;
    connectionNote?: string;
}

export interface NormalizedHydraConnectionVisual {
    connectionLabel: string;
    connectionNote: string;
    routeStyle: HydraConnectionRouteStyle;
    lineStyle: HydraConnectionLineStyle;
    lineColor: string;
    lineWidth: number;
    lineOpacity: number;
    lineBorderColor: string;
    lineBorderWidth: number;
    sourceTerminator: HydraConnectionTerminator;
    targetTerminator: HydraConnectionTerminator;
    sourceTerminatorFillColor: string;
    sourceTerminatorBorderColor: string;
    sourceTerminatorBorderWidth: number;
    sourceTerminatorSize: number;
    targetTerminatorFillColor: string;
    targetTerminatorBorderColor: string;
    targetTerminatorBorderWidth: number;
    targetTerminatorSize: number;
}

export const HYDRA_SOURCE_PORT_IDS = ['top-source', 'right-source', 'bottom-source', 'left-source'] as const;
export const HYDRA_TARGET_PORT_IDS = ['top-target', 'right-target', 'bottom-target', 'left-target'] as const;
export const DEFAULT_HYDRA_SOURCE_PORT = 'right-source';
export const DEFAULT_HYDRA_TARGET_PORT = 'left-target';

interface PortDefinition { id: string; type: 'source' | 'target'; position: Position; percent: number; }
const PORTS: PortDefinition[] = [
    { id: 'top-source', type: 'source', position: Position.Top, percent: 25 },
    { id: 'top-target', type: 'target', position: Position.Top, percent: 75 },
    { id: 'right-source', type: 'source', position: Position.Right, percent: 25 },
    { id: 'right-target', type: 'target', position: Position.Right, percent: 75 },
    { id: 'bottom-target', type: 'target', position: Position.Bottom, percent: 25 },
    { id: 'bottom-source', type: 'source', position: Position.Bottom, percent: 75 },
    { id: 'left-target', type: 'target', position: Position.Left, percent: 25 },
    { id: 'left-source', type: 'source', position: Position.Left, percent: 75 }
];

export function hydraConnectionPorts(): readonly PortDefinition[] { return PORTS; }

/** Calcula handles geométricos para proyecciones que no utilizan Handle components directamente. */
export function createHydraConnectionHandles(options: { width?: number; height?: number; source?: boolean; target?: boolean; handleSize?: number } = {}): NodeHandle[] {
    const width = finite(options.width, 210), height = finite(options.height, 84), size = finite(options.handleSize, 10);
    return PORTS.filter(port => port.type === 'source' ? options.source !== false : options.target !== false).map(port => {
        const horizontal = port.position === Position.Top || port.position === Position.Bottom;
        return { id:port.id, type:port.type, position:port.position, x:horizontal ? width * port.percent / 100 - size / 2 : port.position === Position.Left ? -size / 2 : width - size / 2,
            y:horizontal ? port.position === Position.Top ? -size / 2 : height - size / 2 : height * port.percent / 100 - size / 2, width:size, height:size };
    });
}

export function normalizeHydraConnectionVisual(value: Partial<HydraConnectionVisualData> | undefined): NormalizedHydraConnectionVisual {
    return {
        connectionLabel: typeof value?.connectionLabel === 'string' ? value.connectionLabel : '',
        connectionNote: typeof value?.connectionNote === 'string' ? value.connectionNote : '',
        routeStyle: value?.routeStyle === 'SMOOTH' || value?.routeStyle === 'STRAIGHT' ? value.routeStyle : 'ORTHOGONAL',
        lineStyle: isLineStyle(value?.lineStyle) ? value.lineStyle : 'SOLID',
        lineColor: color(value?.lineColor, ''), lineWidth: range(value?.lineWidth, 0, 0, 16), lineOpacity:range(value?.lineOpacity,1,.1,1),
        lineBorderColor:color(value?.lineBorderColor,''), lineBorderWidth:range(value?.lineBorderWidth,0,0,8),
        sourceTerminator: isTerminator(value?.sourceTerminator) ? value.sourceTerminator : 'NONE',
        targetTerminator: isTerminator(value?.targetTerminator) ? value.targetTerminator : 'ARROW_CLOSED',
        sourceTerminatorFillColor: color(value?.sourceTerminatorFillColor, ''), sourceTerminatorBorderColor: color(value?.sourceTerminatorBorderColor, ''),
        sourceTerminatorBorderWidth: range(value?.sourceTerminatorBorderWidth, 0, 0, 8), sourceTerminatorSize: range(value?.sourceTerminatorSize, 1, .5, 2.5),
        targetTerminatorFillColor: color(value?.targetTerminatorFillColor, ''), targetTerminatorBorderColor: color(value?.targetTerminatorBorderColor, ''),
        targetTerminatorBorderWidth: range(value?.targetTerminatorBorderWidth, 0, 0, 8), targetTerminatorSize: range(value?.targetTerminatorSize, 1, .5, 2.5)
    };
}

export function isHydraConnectionTerminator(value: unknown): value is HydraConnectionTerminator {
    return typeof value === 'string' && (HYDRA_CONNECTION_TERMINATORS as readonly string[]).includes(value);
}
function isTerminator(value: unknown): value is HydraConnectionTerminator { return isHydraConnectionTerminator(value); }
function isLineStyle(value: unknown): value is HydraConnectionLineStyle { return value === 'SOLID' || value === 'DASHED' || value === 'DOTTED' || value === 'LONG_DASH' || value === 'DASH_DOT' || value === 'NONE'; }
function color(value: unknown, fallback: string): string { return typeof value === 'string' && /^#[0-9A-Fa-f]{6}$/.test(value) ? value.toUpperCase() : fallback; }
function finite(value: number | undefined, fallback: number): number { return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : fallback; }
function range(value: unknown, fallback: number, min: number, max: number): number { const number = typeof value === 'number' ? value : Number(value); return Number.isFinite(number) ? Math.max(min, Math.min(max, number)) : fallback; }
