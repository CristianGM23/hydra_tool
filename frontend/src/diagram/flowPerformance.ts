/**
 * Clasifica el coste visual de un diagrama React Flow sin alterar su semántica.
 *
 * Los umbrales son deliberadamente conservadores: los diagramas pequeños mantienen
 * el renderizado completo, mientras que los medianos y grandes pueden delegar en
 * React Flow el descarte de elementos fuera del viewport.
 */
export type HydraFlowScale = 'SMALL' | 'MEDIUM' | 'LARGE';

export interface HydraFlowPerformanceProfile {
    scale: HydraFlowScale;
    nodeCount: number;
    edgeCount: number;
    onlyRenderVisibleElements: boolean;
}

const MEDIUM_NODE_THRESHOLD = 120;
const MEDIUM_EDGE_THRESHOLD = 240;
const LARGE_NODE_THRESHOLD = 400;
const LARGE_EDGE_THRESHOLD = 800;

/**
 * Calcula el perfil de renderizado para un diagrama.
 *
 * @param nodeCount número de nodos semánticos/visuales.
 * @param edgeCount número de conexiones visuales.
 * @returns perfil estable para configurar React Flow.
 */
export function resolveHydraFlowPerformanceProfile(nodeCount: number, edgeCount: number): HydraFlowPerformanceProfile {
    const normalizedNodeCount = normalizeCount(nodeCount);
    const normalizedEdgeCount = normalizeCount(edgeCount);
    const large = normalizedNodeCount >= LARGE_NODE_THRESHOLD || normalizedEdgeCount >= LARGE_EDGE_THRESHOLD;
    const medium = normalizedNodeCount >= MEDIUM_NODE_THRESHOLD || normalizedEdgeCount >= MEDIUM_EDGE_THRESHOLD;
    const scale: HydraFlowScale = large ? 'LARGE' : medium ? 'MEDIUM' : 'SMALL';
    return {
        scale,
        nodeCount: normalizedNodeCount,
        edgeCount: normalizedEdgeCount,
        onlyRenderVisibleElements: scale !== 'SMALL'
    };
}

function normalizeCount(value: number): number {
    if(!Number.isFinite(value) || value <= 0) return 0;
    return Math.floor(value);
}
