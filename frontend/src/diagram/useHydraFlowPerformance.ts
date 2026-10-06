import { useEffect, useLayoutEffect, useMemo } from 'react';
import { resolveHydraFlowPerformanceProfile, type HydraFlowPerformanceProfile } from './flowPerformance';
import { recordHydraDiagramTiming } from './performanceTelemetry';

export interface HydraDiagramPerformanceSample extends HydraFlowPerformanceProfile {
    diagramId: string;
    measuredAt: number;
}

/**
 * Resuelve un perfil estable de React Flow y mide el coste de commit/paint.
 * Las muestras se publican como eventos browser acotados, sin llamadas HTTP en
 * el camino crítico del renderizado.
 */
export function useHydraFlowPerformance(diagramId: string, nodeCount: number, edgeCount: number): HydraFlowPerformanceProfile {
    const renderStartedAt = now();
    const profile = useMemo(() => resolveHydraFlowPerformanceProfile(nodeCount, edgeCount), [nodeCount, edgeCount]);

    useLayoutEffect(() => {
        const committedAt = now();
        recordHydraDiagramTiming({ diagramId, phase: 'render', durationMs: committedAt - renderStartedAt,
            nodeCount: profile.nodeCount, edgeCount: profile.edgeCount, measuredAt: committedAt });
        if (typeof requestAnimationFrame !== 'undefined') {
            const handle = requestAnimationFrame(() => {
                const paintedAt = now();
                recordHydraDiagramTiming({ diagramId, phase: 'paint', durationMs: paintedAt - committedAt,
                    nodeCount: profile.nodeCount, edgeCount: profile.edgeCount, measuredAt: paintedAt });
            });
            return () => cancelAnimationFrame(handle);
        }
        return undefined;
    }, [diagramId, edgeCount, nodeCount, profile.edgeCount, profile.nodeCount, renderStartedAt]);

    useEffect(() => {
        if (typeof window === 'undefined') return;
        const detail: HydraDiagramPerformanceSample = { ...profile, diagramId, measuredAt: now() };
        window.dispatchEvent(new CustomEvent('hydra:diagram-performance-profile', { detail }));
    }, [diagramId, profile]);
    return profile;
}

function now(): number { return typeof performance === 'undefined' ? Date.now() : performance.now(); }
