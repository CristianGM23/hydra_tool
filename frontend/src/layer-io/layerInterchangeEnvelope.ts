export type HydraLayerKind = 'VALIDATION' | 'QUICK_FIX' | 'EVENTS' | 'EVENT_FLOW' | 'GAMIFICATION';

export interface HydraLayerInterchangeEnvelope<T> {
    schemaVersion: '1.0';
    kind: HydraLayerKind;
    metamodelId: string;
    dependencies: readonly string[];
    payload: T;
    annotations: Readonly<Record<string, string>>;
}

export interface UnwrappedLayerPayload<T> {
    payload: T;
    metamodelId: string;
    dependencies: readonly string[];
    warnings: string[];
    enveloped: boolean;
}

export function makeLayerInterchangeEnvelope<T>(kind: HydraLayerKind, metamodelId: string, payload: T,
        dependencies: readonly string[] = []): HydraLayerInterchangeEnvelope<T> {
    return {
        schemaVersion: '1.0',
        kind,
        metamodelId: metamodelId.trim(),
        dependencies: [...new Set(dependencies.map(item => item.trim()).filter(item => item !== ''))].sort(),
        payload,
        annotations: { 'hydra.interchange': 'layer-envelope-1.0' }
    };
}

export function unwrapLayerInterchangeEnvelope<T>(content: string, expectedKind: HydraLayerKind,
        activeMetamodelId = ''): UnwrappedLayerPayload<T> {
    const parsed = JSON.parse(content) as unknown;
    if (!isObject(parsed) || parsed.schemaVersion !== '1.0' || typeof parsed.kind !== 'string' || !('payload' in parsed)) {
        return { payload: parsed as T, metamodelId: '', dependencies: [], warnings: [], enveloped: false };
    }
    if (parsed.kind !== expectedKind) throw new Error(`The HYDRA layer package contains ${String(parsed.kind)}, not ${expectedKind}.`);
    const metamodelId = typeof parsed.metamodelId === 'string' ? parsed.metamodelId.trim() : '';
    const warnings: string[] = [];
    if (activeMetamodelId.trim() !== '' && metamodelId !== '' && metamodelId !== activeMetamodelId.trim()) {
        warnings.push(`The layer package targets metamodel ${metamodelId}; it is being adapted against ${activeMetamodelId.trim()}.`);
    }
    const dependencies = Array.isArray(parsed.dependencies) ? parsed.dependencies.filter((item): item is string => typeof item === 'string') : [];
    return { payload: parsed.payload as T, metamodelId, dependencies, warnings, enveloped: true };
}

function isObject(value: unknown): value is Record<string, unknown> {
    return value !== null && typeof value === 'object' && !Array.isArray(value);
}
