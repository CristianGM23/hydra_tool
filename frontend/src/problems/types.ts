import type { ActionParameter, ActionTransactionPolicy, ActionUndoPolicy, CanonicalModelDocument, ModelValidationDiagnostic, ModelValidationReport, ValidationChangeSet, ValidationRunMode } from '../validation/types';
import type { ModelDocument } from '../types/editor';

export type ValidationGate = 'NONE' | 'SAVE' | 'EXPORT';
export interface LiveValidationRequest {
    mode: ValidationRunMode;
    selectionElementIds: string[];
    classId: string;
    changeSet: ValidationChangeSet;
    variables: Record<string, unknown>;
    gate: ValidationGate;
}
export interface ValidationReportDelta {
    created: ModelValidationDiagnostic[];
    unchanged: ModelValidationDiagnostic[];
    resolved: ModelValidationDiagnostic[];
}
export interface ValidationBlockDecision { gate: ValidationGate; blocked: boolean; diagnosticIds: string[]; reason: string; }
export interface OrchestratedValidationResult { report: ModelValidationReport; delta: ValidationReportDelta; blocking: ValidationBlockDecision; }
export interface QuickFixDescriptor {
    actionId: string;
    name: string;
    description: string;
    enabled: boolean;
    parameters: ActionParameter[];
    transactionPolicy: ActionTransactionPolicy;
    undoPolicy: ActionUndoPolicy;
}
export interface QuickFixExecutionRequest { arguments: Record<string, unknown>; selectionElementIds: string[]; }
export interface CanonicalModelChangeSummary {
    createdElementIds: string[];
    deletedElementIds: string[];
    modifiedElementIds: string[];
    attributeIds: string[];
    relationIds: string[];
    classIds: string[];
    validationChangeSet: ValidationChangeSet;
}
export interface QuickFixPreviewResult {
    diagnosticId: string;
    actionId: string;
    previewDocument: CanonicalModelDocument;
    changes: CanonicalModelChangeSummary;
    messages: string[];
}
export type QuickFixApplicationStatus = 'APPLIED_RESOLVED' | 'APPLIED_UNRESOLVED' | 'APPLIED_WITH_ERRORS' | 'NO_CHANGES' | 'UNDONE' | 'REDONE';
export interface QuickFixApplicationResult {
    diagnosticId: string;
    actionId: string;
    status: QuickFixApplicationStatus;
    diagnosticResolved: boolean;
    document: ModelDocument;
    changes: CanonicalModelChangeSummary;
    validation: OrchestratedValidationResult;
    undoAvailable: boolean;
    redoAvailable: boolean;
    messages: string[];
}
