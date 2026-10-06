import { backendFetch } from '../api/backendFetch';
import { readValidationJson } from '../validation/http';
import type { ModelValidationReport } from '../validation/types';
import type { LiveValidationRequest, OrchestratedValidationResult, QuickFixApplicationResult, QuickFixDescriptor, QuickFixExecutionRequest, QuickFixPreviewResult } from './types';

const route = (modelId: string): string => `/api/models/${encodeURIComponent(modelId)}/validation-runtime`;

export function defaultLiveValidationRequest(): LiveValidationRequest {
    return {
        mode: 'FULL', selectionElementIds: [], classId: '',
        changeSet: { elementIds: [], classIds: [], attributeIds: [], relationIds: [], operationIds: [], modelChanged: false },
        variables: {}, gate: 'NONE'
    };
}

export async function validateLiveModel(modelId: string, request: LiveValidationRequest = defaultLiveValidationRequest()): Promise<OrchestratedValidationResult> {
    return readValidationJson<OrchestratedValidationResult>(await backendFetch(`${route(modelId)}/validate`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function loadLatestValidationReport(modelId: string): Promise<ModelValidationReport> {
    return readValidationJson<ModelValidationReport>(await backendFetch(`${route(modelId)}/latest`));
}

export async function loadDiagnosticQuickFixes(modelId: string, diagnosticId: string): Promise<QuickFixDescriptor[]> {
    return readValidationJson<QuickFixDescriptor[]>(await backendFetch(`${route(modelId)}/problems/${encodeURIComponent(diagnosticId)}/quick-fixes`));
}

export async function previewQuickFix(modelId: string, diagnosticId: string, actionId: string, request: QuickFixExecutionRequest): Promise<QuickFixPreviewResult> {
    return readValidationJson<QuickFixPreviewResult>(await backendFetch(`${route(modelId)}/problems/${encodeURIComponent(diagnosticId)}/quick-fixes/${encodeURIComponent(actionId)}/preview`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function applyQuickFix(modelId: string, diagnosticId: string, actionId: string, request: QuickFixExecutionRequest): Promise<QuickFixApplicationResult> {
    return readValidationJson<QuickFixApplicationResult>(await backendFetch(`${route(modelId)}/problems/${encodeURIComponent(diagnosticId)}/quick-fixes/${encodeURIComponent(actionId)}/apply`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function undoQuickFix(modelId: string): Promise<QuickFixApplicationResult> {
    return readValidationJson<QuickFixApplicationResult>(await backendFetch(`${route(modelId)}/quick-fixes/undo`, { method: 'POST' }));
}

export async function redoQuickFix(modelId: string): Promise<QuickFixApplicationResult> {
    return readValidationJson<QuickFixApplicationResult>(await backendFetch(`${route(modelId)}/quick-fixes/redo`, { method: 'POST' }));
}
