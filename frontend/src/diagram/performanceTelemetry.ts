export type HydraDiagramPerformancePhase = 'render' | 'paint' | 'layout' | 'serialization' | 'commit';

export interface HydraDiagramTimingSample {
    diagramId: string;
    phase: HydraDiagramPerformancePhase;
    durationMs: number;
    nodeCount?: number;
    edgeCount?: number;
    measuredAt: number;
}

const MAX_SAMPLES = 500;
const samples: HydraDiagramTimingSample[] = [];

/**
 * Registra una muestra acotada y desacoplada del backend. Diagnostics puede
 * escuchar el evento sin que los diagramadores conozcan el destino de métricas.
 */
export function recordHydraDiagramTiming(sample: HydraDiagramTimingSample): void {
    const safe = { ...sample, durationMs: Math.max(0, sample.durationMs) };
    samples.push(safe);
    if (samples.length > MAX_SAMPLES) samples.splice(0, samples.length - MAX_SAMPLES);
    if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('hydra:diagram-performance-timing', { detail: safe }));
}

/** Ejecuta una operación síncrona y registra su coste. */
export function measureHydraDiagramOperation<T>(diagramId: string, phase: HydraDiagramPerformancePhase, operation: () => T,
        counts: { nodeCount?: number; edgeCount?: number } = {}): T {
    const started = now();
    try { return operation(); }
    finally { recordHydraDiagramTiming({ diagramId, phase, durationMs: now() - started, measuredAt: now(), ...counts }); }
}

/** Ejecuta una operación asíncrona y registra hasta completar o fallar. */
export async function measureHydraDiagramAsyncOperation<T>(diagramId: string, phase: HydraDiagramPerformancePhase,
        operation: () => Promise<T>, counts: { nodeCount?: number; edgeCount?: number } = {}): Promise<T> {
    const started = now();
    try { return await operation(); }
    finally { recordHydraDiagramTiming({ diagramId, phase, durationMs: now() - started, measuredAt: now(), ...counts }); }
}

/** Snapshot defensivo para tests/diagnóstico local. */
export function hydraDiagramTimingSnapshot(): readonly HydraDiagramTimingSample[] { return samples.map(value => ({ ...value })); }
export function clearHydraDiagramTimingSamples(): void { samples.length = 0; }

function now(): number { return typeof performance === 'undefined' ? Date.now() : performance.now(); }
