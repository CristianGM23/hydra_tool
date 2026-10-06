import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type {
    TransformationCheckReport,
    TransformationDefinitionDocument,
    TransformationLoadResult,
    TransformationSaveResponse,
    TransformationSummary,
    TransformationExecutionResult, TransformationSynchronizationRequest, TransformationSynchronizationResult,
    TransformationSynchronizationCheckpoint, TransformationSynchronizationSession, TransformationSynchronizationSessionCreateRequest,
    TransformationSynchronizationSessionLoadResult, TransformationSynchronizationSessionMutationResult,
    TransformationSynchronizationSessionSummary, TransformationSynchronizationSessionUpdateRequest,
    TransformationExecutionDirection,
    TransformationGenerationResult,
    TransformationTextToModelResult,
    TransformationExportLanguage,
    TransformationExportResult,
    TransformationTestCase,
    TransformationTestCaseSummary,
    TransformationTestCaseLoadResult,
    TransformationTestCaseSaveResponse,
    TransformationTestRunReport,
    TransformationQuickFixReport,
    TransformationParityRequest,
    TransformationParityReport,
    TransformationNativeRuntimeDescriptor,
    TransformationNativeRuntimeDiagnostic,
    TransformationNativeParityRequest,
    TransformationNativeParityReport,
    TransformationToolchainCatalog,
    TransformationToolchainProvisioningReport,
    TransformationToolchainProvisioningRequest,
    TransformationToolchainRemovalRequest,
} from './types';
import type { CanonicalModelDocument } from '../validation/types';

async function readJson<T>(response: Response): Promise<T> {
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<T>;
}

export async function listTransformations(): Promise<TransformationSummary[]> {
    return readJson<TransformationSummary[]>(await backendFetch('/api/transformations'));
}

export async function loadTransformation(id: string): Promise<TransformationLoadResult> {
    const response = await backendFetch(`/api/transformations/${encodeURIComponent(id)}`);
    const result = await readJson<TransformationLoadResult>(response);
    return { ...result, etag: response.headers.get('ETag') ?? result.etag };
}

export async function checkTransformation(document: TransformationDefinitionDocument): Promise<TransformationCheckReport> {
    return readJson<TransformationCheckReport>(await backendFetch('/api/transformations/check', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(document),
    }));
}

export async function createTransformation(document: TransformationDefinitionDocument): Promise<TransformationSaveResponse> {
    const response = await backendFetch('/api/transformations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(document),
    });
    const result = await readJson<TransformationSaveResponse>(response);
    return { ...result, etag: response.headers.get('ETag') ?? result.etag };
}

export async function replaceTransformation(document: TransformationDefinitionDocument, etag: string): Promise<TransformationSaveResponse> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (etag !== '') headers['If-Match'] = etag;
    const response = await backendFetch(`/api/transformations/${encodeURIComponent(document.id)}`, {
        method: 'PUT',
        headers,
        body: JSON.stringify(document),
    });
    const result = await readJson<TransformationSaveResponse>(response);
    return { ...result, etag: response.headers.get('ETag') ?? result.etag };
}

export async function deleteTransformation(id: string, etag: string): Promise<void> {
    const headers: Record<string, string> = {};
    if (etag !== '') headers['If-Match'] = etag;
    const response = await backendFetch(`/api/transformations/${encodeURIComponent(id)}`, { method: 'DELETE', headers });
    if (!response.ok) throw await readActionableApiError(response);
}

export async function executeTransformation(id: string, sourceModel: CanonicalModelDocument, direction:TransformationExecutionDirection = 'FORWARD'): Promise<TransformationExecutionResult> {
    const suffix = direction === 'FORWARD' ? 'execute' : `execute/${direction}`;
    return readJson<TransformationExecutionResult>(await backendFetch(`/api/transformations/${encodeURIComponent(id)}/${suffix}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(sourceModel),
    }));
}

export async function previewTransformation(document: TransformationDefinitionDocument, sourceModel: CanonicalModelDocument, direction:TransformationExecutionDirection = 'FORWARD'): Promise<TransformationExecutionResult> {
    return readJson<TransformationExecutionResult>(await backendFetch('/api/transformations/preview', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ transformation: document, sourceModel, direction }),
    }));
}

export async function previewModelToText(document: TransformationDefinitionDocument, sourceModel: CanonicalModelDocument): Promise<TransformationGenerationResult> {
    return readJson<TransformationGenerationResult>(await backendFetch('/api/transformations/preview-model-to-text', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ transformation: document, sourceModel }),
    }));
}

export async function previewTextToModel(document: TransformationDefinitionDocument, text: string): Promise<TransformationTextToModelResult> {
    return readJson<TransformationTextToModelResult>(await backendFetch('/api/transformations/preview-text-to-model', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ transformation: document, text }),
    }));
}

export async function listTransformationExportLanguages(): Promise<TransformationExportLanguage[]> {
    return readJson<TransformationExportLanguage[]>(await backendFetch('/api/transformations/export-languages'));
}

export async function exportTransformation(document: TransformationDefinitionDocument, language: TransformationExportLanguage, direction:TransformationExecutionDirection = 'FORWARD'): Promise<TransformationExportResult> {
    return readJson<TransformationExportResult>(await backendFetch('/api/transformations/export', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ transformation: document, language, direction }),
    }));
}

export async function verifyTransformationParity(request: TransformationParityRequest): Promise<TransformationParityReport> {
    return readJson<TransformationParityReport>(await backendFetch('/api/transformations/parity/verify', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
    }));
}

export async function listTransformationNativeRuntimes(): Promise<TransformationNativeRuntimeDescriptor[]> {
    return readJson<TransformationNativeRuntimeDescriptor[]>(await backendFetch('/api/transformations/parity/native/runtimes'));
}

export async function diagnoseTransformationNativeRuntime(language: TransformationExportLanguage): Promise<TransformationNativeRuntimeDiagnostic> {
    return readJson<TransformationNativeRuntimeDiagnostic>(await backendFetch(`/api/transformations/parity/native/runtimes/${encodeURIComponent(language)}/diagnose`, {
        method: 'POST',
    }));
}

export async function executeTransformationNativeParity(request: TransformationNativeParityRequest): Promise<TransformationNativeParityReport> {
    return readJson<TransformationNativeParityReport>(await backendFetch('/api/transformations/parity/native/execute', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
    }));
}

export async function loadTransformationToolchainCatalog(): Promise<TransformationToolchainCatalog> {
    return readJson<TransformationToolchainCatalog>(await backendFetch('/api/transformations/parity/native/toolchains'));
}

export async function installTransformationToolchain(language:TransformationExportLanguage, request:TransformationToolchainProvisioningRequest): Promise<TransformationToolchainProvisioningReport> {
    return readJson<TransformationToolchainProvisioningReport>(await backendFetch(`/api/transformations/parity/native/toolchains/${encodeURIComponent(language)}/install`, {
        method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(request),
    }));
}

export async function uninstallTransformationToolchain(language:TransformationExportLanguage, request:TransformationToolchainRemovalRequest): Promise<TransformationToolchainProvisioningReport> {
    return readJson<TransformationToolchainProvisioningReport>(await backendFetch(`/api/transformations/parity/native/toolchains/${encodeURIComponent(language)}/uninstall`, {
        method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(request),
    }));
}


export async function listTransformationTests(transformationId:string): Promise<TransformationTestCaseSummary[]> {
    return readJson<TransformationTestCaseSummary[]>(await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/tests`));
}

export async function loadTransformationTest(transformationId:string,testCaseId:string): Promise<TransformationTestCaseLoadResult> {
    const response=await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/tests/${encodeURIComponent(testCaseId)}`);
    const result=await readJson<TransformationTestCaseLoadResult>(response);
    return {...result,etag:response.headers.get('ETag')??result.etag};
}

export async function createTransformationTest(transformationId:string,testCase:TransformationTestCase): Promise<TransformationTestCaseSaveResponse> {
    const response=await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/tests`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(testCase)});
    const result=await readJson<TransformationTestCaseSaveResponse>(response);
    return {...result,etag:response.headers.get('ETag')??result.etag};
}

export async function replaceTransformationTest(transformationId:string,testCase:TransformationTestCase,etag:string): Promise<TransformationTestCaseSaveResponse> {
    const headers:Record<string,string>={'Content-Type':'application/json'};if(etag!=='')headers['If-Match']=etag;
    const response=await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/tests/${encodeURIComponent(testCase.id)}`,{method:'PUT',headers,body:JSON.stringify(testCase)});
    const result=await readJson<TransformationTestCaseSaveResponse>(response);
    return {...result,etag:response.headers.get('ETag')??result.etag};
}

export async function deleteTransformationTest(transformationId:string,testCaseId:string,etag:string): Promise<void> {
    const headers:Record<string,string>={};if(etag!=='')headers['If-Match']=etag;
    const response=await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/tests/${encodeURIComponent(testCaseId)}`,{method:'DELETE',headers});
    if(!response.ok)throw await readActionableApiError(response);
}

export async function runTransformationTestSuite(transformationId:string): Promise<TransformationTestRunReport> {
    return readJson<TransformationTestRunReport>(await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/tests/run`,{method:'POST'}));
}

export async function runTransformationTest(transformationId:string,testCaseId:string): Promise<TransformationTestRunReport> {
    return readJson<TransformationTestRunReport>(await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/tests/${encodeURIComponent(testCaseId)}/run`,{method:'POST'}));
}

export async function transformationQuickFixes(document:TransformationDefinitionDocument): Promise<TransformationQuickFixReport> {
    return readJson<TransformationQuickFixReport>(await backendFetch('/api/transformations/quick-fixes',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(document)}));
}

export async function previewTransformationSynchronization(document: TransformationDefinitionDocument, synchronization: TransformationSynchronizationRequest): Promise<TransformationSynchronizationResult> {
    return readJson<TransformationSynchronizationResult>(await backendFetch('/api/transformations/preview-synchronize', {
        method:'POST', headers:{ 'Content-Type':'application/json' }, body:JSON.stringify({ transformation:document, synchronization }),
    }));
}

export async function synchronizeTransformation(id:string, synchronization:TransformationSynchronizationRequest):Promise<TransformationSynchronizationResult>{
    return readJson<TransformationSynchronizationResult>(await backendFetch(`/api/transformations/${encodeURIComponent(id)}/synchronize`, {
        method:'POST', headers:{ 'Content-Type':'application/json' }, body:JSON.stringify(synchronization),
    }));
}


export async function listTransformationSynchronizationSessions(transformationId:string): Promise<TransformationSynchronizationSessionSummary[]> {
    return readJson<TransformationSynchronizationSessionSummary[]>(await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/synchronization-sessions`));
}

export async function createTransformationSynchronizationSession(transformationId:string, request:TransformationSynchronizationSessionCreateRequest): Promise<TransformationSynchronizationSessionMutationResult> {
    const response=await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/synchronization-sessions`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)});
    const result=await readJson<TransformationSynchronizationSessionMutationResult>(response);
    return {...result,etag:response.headers.get('ETag')??result.etag};
}

export async function loadTransformationSynchronizationSession(transformationId:string,sessionId:string): Promise<TransformationSynchronizationSessionLoadResult> {
    const response=await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/synchronization-sessions/${encodeURIComponent(sessionId)}`);
    const session=await readJson<TransformationSynchronizationSession>(response);
    return {session,etag:response.headers.get('ETag')??''};
}

export async function synchronizeTransformationSession(transformationId:string,sessionId:string,request:TransformationSynchronizationSessionUpdateRequest,etag:string): Promise<TransformationSynchronizationSessionMutationResult> {
    const headers:Record<string,string>={'Content-Type':'application/json'}; if(etag!=='')headers['If-Match']=etag;
    const response=await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/synchronization-sessions/${encodeURIComponent(sessionId)}/synchronize`,{method:'POST',headers,body:JSON.stringify(request)});
    const result=await readJson<TransformationSynchronizationSessionMutationResult>(response);
    return {...result,etag:response.headers.get('ETag')??result.etag};
}

export async function rebaseTransformationSynchronizationSession(transformationId:string,sessionId:string,request:TransformationSynchronizationSessionCreateRequest,etag:string): Promise<TransformationSynchronizationSessionMutationResult> {
    const headers:Record<string,string>={'Content-Type':'application/json'}; if(etag!=='')headers['If-Match']=etag;
    const response=await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/synchronization-sessions/${encodeURIComponent(sessionId)}/rebase`,{method:'POST',headers,body:JSON.stringify(request)});
    const result=await readJson<TransformationSynchronizationSessionMutationResult>(response);
    return {...result,etag:response.headers.get('ETag')??result.etag};
}

export async function listTransformationSynchronizationHistory(transformationId:string,sessionId:string): Promise<TransformationSynchronizationCheckpoint[]> {
    return readJson<TransformationSynchronizationCheckpoint[]>(await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/synchronization-sessions/${encodeURIComponent(sessionId)}/history`));
}

export async function restoreTransformationSynchronizationSession(transformationId:string,sessionId:string,revision:number,etag:string): Promise<TransformationSynchronizationSessionMutationResult> {
    const headers:Record<string,string>={}; if(etag!=='')headers['If-Match']=etag;
    const response=await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/synchronization-sessions/${encodeURIComponent(sessionId)}/restore/${revision}`,{method:'POST',headers});
    const result=await readJson<TransformationSynchronizationSessionMutationResult>(response);
    return {...result,etag:response.headers.get('ETag')??result.etag};
}

export async function deleteTransformationSynchronizationSession(transformationId:string,sessionId:string,etag:string): Promise<void> {
    const headers:Record<string,string>={}; if(etag!=='')headers['If-Match']=etag;
    const response=await backendFetch(`/api/transformations/${encodeURIComponent(transformationId)}/synchronization-sessions/${encodeURIComponent(sessionId)}`,{method:'DELETE',headers});
    if(!response.ok)throw await readActionableApiError(response);
}
