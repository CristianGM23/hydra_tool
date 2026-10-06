import type { IntegrationTechnology } from '../integration-designer/types';

export type ConflictResolutionChoice = 'KEEP_HYDRA' | 'KEEP_NATIVE' | 'KEEP_MANUAL' | 'MERGE_SAFE' | 'SKIP';
export type RoundTripChangeKind = 'UNCHANGED' | 'ADD' | 'REGENERATE' | 'MANUAL_CONFLICT' | 'NATIVE_DELETED' | 'ORPHAN_NATIVE';

export interface RoundTripFileChange {
    path: string;
    kind: RoundTripChangeKind;
    previousSha256: string;
    currentSha256: string;
    generatedSha256: string;
    selectable: boolean;
    message: string;
}

export interface RoundTripSyncPreview {
    technology: IntegrationTechnology;
    profileId: string;
    changes: RoundTripFileChange[];
    conflicts: string[];
    selectablePaths: string[];
    metadata: Record<string, string>;
    blocked?: boolean;
}

export interface ElementConflict {
    elementId: string;
    artifactPath: string;
    hydraHash: string;
    nativeHash: string;
    containsManualRegion: boolean;
    supportedChoices: ConflictResolutionChoice[];
}

export interface ElementConflictDecision {
    conflictKey: string;
    choice: ConflictResolutionChoice;
    rationale: string;
}

export interface RoundTripResolutionActionView {
    path: string;
    kind: 'WRITE_HYDRA' | 'MERGE_SAFE';
    mediaType: string;
    conflictKey: string;
}

export interface RoundTripResolutionPlanView {
    operationId: string;
    technology: IntegrationTechnology;
    profileId: string;
    targetRoot: string;
    preview: RoundTripSyncPreview;
    actions: RoundTripResolutionActionView[];
    unresolved: ElementConflict[];
    canApply: boolean;
    metadata: Record<string, string>;
}

export interface RoundTripApplyResult {
    operationId: string;
    successful: boolean;
    rollback: boolean;
    affectedPaths: string[];
    journalPath: string;
    metadata: Record<string, string>;
}
