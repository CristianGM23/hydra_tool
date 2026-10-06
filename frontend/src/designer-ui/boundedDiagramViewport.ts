import type { CoordinateExtent } from '@xyflow/react';

export interface HydraViewportNode {
    position: { x: number; y: number };
    width?: number | null;
    height?: number | null;
    measured?: { width?: number | null; height?: number | null };
}

export interface HydraViewportBoundaryOptions {
    margin?: number;
    minimumWidth?: number;
    minimumHeight?: number;
    fallbackNodeWidth?: number;
    fallbackNodeHeight?: number;
}

const defaults: Required<HydraViewportBoundaryOptions> = {
    margin: 420,
    minimumWidth: 1800,
    minimumHeight: 1200,
    fallbackNodeWidth: 240,
    fallbackNodeHeight: 120
};

const defaultCache = new WeakMap<readonly object[], CoordinateExtent>();

export function resolveHydraDiagramTranslateExtent(nodes: readonly HydraViewportNode[], options: HydraViewportBoundaryOptions = {}): CoordinateExtent {
    const useCache = Object.keys(options).length === 0;
    if (useCache) {
        const cached = defaultCache.get(nodes as readonly object[]);
        if (cached !== undefined) return cached;
    }
    const config = { ...defaults, ...options };
    if (nodes.length === 0) {
        const empty: CoordinateExtent = [[-config.minimumWidth / 2, -config.minimumHeight / 2], [config.minimumWidth / 2, config.minimumHeight / 2]];
        if (useCache) defaultCache.set(nodes as readonly object[], empty);
        return empty;
    }
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (const node of nodes) {
        const width = positive(node.measured?.width) ?? positive(node.width) ?? config.fallbackNodeWidth;
        const height = positive(node.measured?.height) ?? positive(node.height) ?? config.fallbackNodeHeight;
        const x = finite(node.position.x), y = finite(node.position.y);
        minX = Math.min(minX, x); minY = Math.min(minY, y);
        maxX = Math.max(maxX, x + width); maxY = Math.max(maxY, y + height);
    }
    const centerX = (minX + maxX) / 2, centerY = (minY + maxY) / 2;
    const width = Math.max(config.minimumWidth, maxX - minX + config.margin * 2);
    const height = Math.max(config.minimumHeight, maxY - minY + config.margin * 2);
    const extent: CoordinateExtent = [[centerX - width / 2, centerY - height / 2], [centerX + width / 2, centerY + height / 2]];
    if (useCache) defaultCache.set(nodes as readonly object[], extent);
    return extent;
}

export function resolveHydraDiagramPanPolicy(mode: 'SELECT' | 'PAN' | 'CONNECT') {
    return { panOnDrag: mode === 'PAN', panActivationKeyCode: null, preventScrolling: true } as const;
}

function positive(value: number | null | undefined): number | null { return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : null; }
function finite(value: number): number { return Number.isFinite(value) ? value : 0; }
