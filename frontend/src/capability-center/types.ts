import type { HydraModuleId } from '../application-shell/types';

export interface HydraCapabilityLayerSummary {
    id: string;
    displayName: string;
    configured: boolean;
    artifactCount: number;
    metrics: Record<string, number>;
    recommendedModule: HydraModuleId;
}

export interface HydraCapabilityReadiness {
    contextId: string;
    contextName: string;
    contextType: 'METAMODEL' | 'GRAMMAR';
    semanticContextId: string;
    score: number;
    configuredLayers: number;
    totalLayers: number;
    layers: HydraCapabilityLayerSummary[];
    recommendedNextSteps: string[];
    warnings?: string[];
}


export interface CapabilityLayerTransactionItem {
    documentPath: string;
    artifactKind: string;
    targetPath: string;
    action: 'CREATE' | 'REPLACE';
    order: number;
    currentEtag: string;
    expectedEtag: string;
    proposedEtag: string;
    applicable: boolean;
    diagnostics: string[];
}

export interface CapabilityLayerTransactionPreview {
    previewId: string;
    targetMetamodelId: string;
    items: CapabilityLayerTransactionItem[];
    totalBytes: number;
    applicable: boolean;
    diagnostics: string[];
}

export interface CapabilityLayerTransactionRequest {
    capabilityPackage: Record<string, unknown>;
    targetMetamodelId: string;
    expectedEtags: Record<string, string>;
    layerOrder: string[];
    persist: boolean;
}

export interface CapabilityLayerTransactionResult {
    preview: CapabilityLayerTransactionPreview;
    transactionId: string;
    persisted: boolean;
    rollbackAvailable: boolean;
    appliedEtags: Record<string, string>;
    diagnostics: string[];
}

export interface CapabilityLayerRollbackResult {
    transactionId: string;
    restoredDocuments: string[];
    skippedDocuments: string[];
    journalClosed: boolean;
    diagnostics: string[];
}
