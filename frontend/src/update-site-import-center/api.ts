import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { UpdateSiteImportAnalysis, UpdateSiteImportOptions, UpdateSiteImportResult,
    UpdateSiteLayerMaterializationRequest, UpdateSiteLayerMaterializationResult,
    UpdateSiteLayerMaterializationRollbackRequest, UpdateSiteLayerMaterializationRollbackResult,
    UpdateSiteReconciliationRequest, UpdateSiteReconciliationResult,
    UpdateSiteExtensionAdapterDescriptor, UpdateSiteLayerMergeRequest, UpdateSiteLayerMergeResult,
    UpdateSiteLayerMergeRollbackRequest, UpdateSiteLayerMergeRollbackResult,
    UpdateSiteRemoteAnalysisRequest, UpdateSiteRemoteAnalysisResult,
    UpdateSiteDeclarativeAdapterValidation, UpdateSiteDeclarativeAdapterImportResult,
    UpdateSiteAnalysisCacheMetrics, UpdateSiteRemoteSnapshotResult, UpdateSiteRemoteSnapshotAnalyzeRequest,
    UpdateSiteRemoteSnapshotDriftReport, UpdateSiteRemoteSnapshotLicenseEntry, UpdateSiteRemoteSnapshotLock,
    UpdateSiteAdapterFixture, UpdateSiteAdapterFixtureResult,
    UpdateSiteDeclarativeAdapterEntry, CapabilityPerformanceBudget, CapabilityPerformanceSample,
    CapabilityPerformanceReport, UpdateSiteDeclarativeAdapterVersionEntry,
    CapabilitySemanticMergeRequest, CapabilitySemanticMergePreview, CapabilitySemanticMergeResult,
    UpdateSiteP2MirrorResult, UpdateSiteAdapterBenchRequest, UpdateSiteAdapterBenchReport,
    UpdateSiteAdapterBenchComparison, UpdateSiteAnalysisPage, UpdateSiteElementProvenance,
    UpdateSiteCompactAnalysis, CapabilityPerformanceComparison, HydraReleaseGateRequest,
    HydraReleaseGateReport, CapabilityOrchestrationRequest, CapabilityOrchestrationPreview,
    CapabilityOrchestrationResult, UpdateSiteMirrorUpdateRequest, UpdateSiteMirrorUpdateReport,
    UpdateSiteMirrorChannel, UpdateSiteMirrorChannelState, UpdateSiteAdapterVerificationRequest,
    UpdateSiteAdapterVerificationReport, CapabilityPerformanceScenarioRequest,
    CapabilityPerformanceAutomationReport, HydraDistributionProvenanceRequest,
    HydraDistributionProvenanceReport, UpdateSiteRepositorySourceRequest, UpdateSiteRepositorySourceAnalysis } from './types';

function form(files: readonly File[], options: UpdateSiteImportOptions, includeImportOptions: boolean): FormData {
    const data = new FormData();
    for (const file of files) data.append('files', file, file.webkitRelativePath || file.name);
    data.append('targetId', options.targetId);
    data.append('strict', String(options.strict));
    data.append('candidateId', options.candidateId);
    data.append('recoverPalettes', String(options.recoverPalettes));
    data.append('recoverRepresentations', String(options.recoverRepresentations));
    data.append('recoverMappings', String(options.recoverMappings));
    if (includeImportOptions) {
        data.append('overwrite', String(options.overwrite));
        data.append('persistRecoveryPackage', String(options.persistRecoveryPackage));
    }
    return data;
}

function requestForm(files: readonly File[], request: unknown): FormData {
    const data = new FormData();
    for (const file of files) data.append('files', file, file.webkitRelativePath || file.name);
    data.append('request', JSON.stringify(request));
    return data;
}

async function json<T>(response: Response): Promise<T> {
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<T>;
}

export async function analyzeUpdateSite(files: readonly File[], options: UpdateSiteImportOptions): Promise<UpdateSiteImportAnalysis> {
    return json(await backendFetch('/api/update-site-imports/analyze', { method: 'POST', body: form(files, options, false) }));
}

export async function importUpdateSite(files: readonly File[], options: UpdateSiteImportOptions): Promise<UpdateSiteImportResult> {
    return json(await backendFetch('/api/update-site-imports/import', { method: 'POST', body: form(files, options, true) }));
}

export async function reconcileUpdateSite(files: readonly File[], request: UpdateSiteReconciliationRequest): Promise<UpdateSiteReconciliationResult> {
    return json(await backendFetch('/api/update-site-imports/reconcile', { method: 'POST', body: requestForm(files, request) }));
}

export async function materializeUpdateSiteLayers(files: readonly File[], request: UpdateSiteLayerMaterializationRequest): Promise<UpdateSiteLayerMaterializationResult> {
    return json(await backendFetch('/api/update-site-imports/materialize', { method: 'POST', body: requestForm(files, request) }));
}

export async function rollbackUpdateSiteMaterialization(request: UpdateSiteLayerMaterializationRollbackRequest): Promise<UpdateSiteLayerMaterializationRollbackResult> {
    return json(await backendFetch('/api/update-site-imports/materialize/rollback', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function loadUpdateSiteExtensionAdapters(): Promise<UpdateSiteExtensionAdapterDescriptor[]> {
    return json(await backendFetch('/api/update-site-imports/extension-adapters'));
}


export async function mergeUpdateSiteLayers(files: readonly File[], request: UpdateSiteLayerMergeRequest): Promise<UpdateSiteLayerMergeResult> {
    return json(await backendFetch('/api/update-site-imports/merge', { method: 'POST', body: requestForm(files, request) }));
}

export async function rollbackUpdateSiteMerge(request: UpdateSiteLayerMergeRollbackRequest): Promise<UpdateSiteLayerMergeRollbackResult> {
    return json(await backendFetch('/api/update-site-imports/merge/rollback', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}


export async function analyzeUpdateSiteRepository(request: UpdateSiteRepositorySourceRequest): Promise<UpdateSiteRepositorySourceAnalysis> {
    return json(await backendFetch('/api/update-site-imports/repository/analyze', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function analyzeUpdateSiteRepositorySnapshot(snapshotId: string, options: UpdateSiteImportOptions): Promise<UpdateSiteImportAnalysis> {
    return json(await backendFetch(`/api/update-site-imports/repository/snapshots/${encodeURIComponent(snapshotId)}/analyze`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(options)
    }));
}

export async function importUpdateSiteRepositorySnapshot(snapshotId: string, options: UpdateSiteImportOptions): Promise<UpdateSiteImportResult> {
    return json(await backendFetch(`/api/update-site-imports/repository/snapshots/${encodeURIComponent(snapshotId)}/import`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(options)
    }));
}

export async function reconcileUpdateSiteRepositorySnapshot(snapshotId: string, request: UpdateSiteReconciliationRequest): Promise<UpdateSiteReconciliationResult> {
    return json(await backendFetch(`/api/update-site-imports/repository/snapshots/${encodeURIComponent(snapshotId)}/reconcile`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function materializeUpdateSiteRepositorySnapshot(snapshotId: string, request: UpdateSiteLayerMaterializationRequest): Promise<UpdateSiteLayerMaterializationResult> {
    return json(await backendFetch(`/api/update-site-imports/repository/snapshots/${encodeURIComponent(snapshotId)}/materialize`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function mergeUpdateSiteRepositorySnapshot(snapshotId: string, request: UpdateSiteLayerMergeRequest): Promise<UpdateSiteLayerMergeResult> {
    return json(await backendFetch(`/api/update-site-imports/repository/snapshots/${encodeURIComponent(snapshotId)}/merge`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function analyzeRemoteUpdateSite(request: UpdateSiteRemoteAnalysisRequest): Promise<UpdateSiteRemoteAnalysisResult> {
    return json(await backendFetch('/api/update-site-imports/remote/analyze', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function previewDeclarativeAdapter(content: string): Promise<UpdateSiteDeclarativeAdapterValidation> {
    return json(await backendFetch('/api/update-site-imports/extension-adapters/preview', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: content
    }));
}

export async function importDeclarativeAdapter(content: string, overwrite: boolean): Promise<UpdateSiteDeclarativeAdapterImportResult> {
    return json(await backendFetch(`/api/update-site-imports/extension-adapters/import?overwrite=${overwrite}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: content
    }));
}

export async function loadUpdateSiteCacheMetrics(): Promise<UpdateSiteAnalysisCacheMetrics> {
    return json(await backendFetch('/api/update-site-imports/performance/cache'));
}

export async function clearUpdateSiteCache(): Promise<void> {
    const response = await backendFetch('/api/update-site-imports/performance/cache', { method: 'DELETE' });
    if (!response.ok) throw await readActionableApiError(response);
}


export async function createRemoteUpdateSiteSnapshot(request: UpdateSiteRemoteAnalysisRequest): Promise<UpdateSiteRemoteSnapshotResult> {
    return json(await backendFetch('/api/update-site-imports/remote/snapshots', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}


export async function importRemoteUpdateSiteSnapshot(file: File, options: UpdateSiteImportOptions): Promise<UpdateSiteRemoteSnapshotResult> {
    const data = new FormData(); data.append('file', file); data.append('options', JSON.stringify(options));
    return json(await backendFetch('/api/update-site-imports/remote/snapshots/import', { method: 'POST', body: data }));
}

export async function analyzeRemoteUpdateSiteSnapshot(request: UpdateSiteRemoteSnapshotAnalyzeRequest): Promise<UpdateSiteImportAnalysis> {
    return json(await backendFetch('/api/update-site-imports/remote/snapshots/analyze', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function importRemoteSnapshotRecovery(snapshotId: string, options: UpdateSiteImportOptions): Promise<UpdateSiteImportResult> {
    return json(await backendFetch(`/api/update-site-imports/remote/snapshots/${encodeURIComponent(snapshotId)}/import`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(options)
    }));
}

export async function reconcileRemoteSnapshot(snapshotId: string, request: UpdateSiteReconciliationRequest): Promise<UpdateSiteReconciliationResult> {
    return json(await backendFetch(`/api/update-site-imports/remote/snapshots/${encodeURIComponent(snapshotId)}/reconcile`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function materializeRemoteSnapshot(snapshotId: string, request: UpdateSiteLayerMaterializationRequest): Promise<UpdateSiteLayerMaterializationResult> {
    return json(await backendFetch(`/api/update-site-imports/remote/snapshots/${encodeURIComponent(snapshotId)}/materialize`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function mergeRemoteSnapshotLayers(snapshotId: string, request: UpdateSiteLayerMergeRequest): Promise<UpdateSiteLayerMergeResult> {
    return json(await backendFetch(`/api/update-site-imports/remote/snapshots/${encodeURIComponent(snapshotId)}/merge`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function checkRemoteUpdateSiteSnapshotDrift(snapshotId: string): Promise<UpdateSiteRemoteSnapshotDriftReport> {
    return json(await backendFetch(`/api/update-site-imports/remote/snapshots/${encodeURIComponent(snapshotId)}/drift`, { method: 'POST' }));
}

export async function reviewRemoteUpdateSiteSnapshotLicenses(snapshotId: string,
    licenses: UpdateSiteRemoteSnapshotLicenseEntry[]): Promise<UpdateSiteRemoteSnapshotLock> {
    return json(await backendFetch(`/api/update-site-imports/remote/snapshots/${encodeURIComponent(snapshotId)}/licenses`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ licenses })
    }));
}

export async function downloadRemoteUpdateSiteSnapshot(snapshotId: string): Promise<Blob> {
    const response = await backendFetch(`/api/update-site-imports/remote/snapshots/${encodeURIComponent(snapshotId)}/export`);
    if (!response.ok) throw await readActionableApiError(response);
    return response.blob();
}

export async function testDeclarativeAdapter(content: string, fixture: UpdateSiteAdapterFixture): Promise<UpdateSiteAdapterFixtureResult> {
    return json(await backendFetch('/api/update-site-imports/extension-adapters/test', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ document: content, fixture })
    }));
}

export async function loadDeclarativeAdapterDocuments(): Promise<UpdateSiteDeclarativeAdapterEntry[]> {
    return json(await backendFetch('/api/update-site-imports/extension-adapters/documents'));
}

export async function setDeclarativeAdapterEnabled(adapterId: string, enabled: boolean): Promise<UpdateSiteDeclarativeAdapterEntry> {
    return json(await backendFetch(`/api/update-site-imports/extension-adapters/${encodeURIComponent(adapterId)}/activation`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled })
    }));
}

export async function loadCapabilityPerformanceBudgets(): Promise<CapabilityPerformanceBudget[]> {
    return json(await backendFetch('/api/performance/capabilities/budgets'));
}

export async function evaluateCapabilityPerformance(sample: CapabilityPerformanceSample): Promise<CapabilityPerformanceReport> {
    return json(await backendFetch('/api/performance/capabilities/reports', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(sample)
    }));
}

export async function deleteDeclarativeAdapter(adapterId: string): Promise<void> {
    const response = await backendFetch(`/api/update-site-imports/extension-adapters/${encodeURIComponent(adapterId)}`, { method: 'DELETE' });
    if (!response.ok) throw await readActionableApiError(response);
}

export async function downloadDeclarativeAdapter(adapterId: string): Promise<Blob> {
    const response = await backendFetch(`/api/update-site-imports/extension-adapters/${encodeURIComponent(adapterId)}/export`);
    if (!response.ok) throw await readActionableApiError(response);
    return response.blob();
}


export async function loadDeclarativeAdapterVersions(adapterId: string): Promise<UpdateSiteDeclarativeAdapterVersionEntry[]> {
    return json(await backendFetch(`/api/update-site-imports/extension-adapters/${encodeURIComponent(adapterId)}/versions`));
}

export async function downloadDeclarativeAdapterVersion(adapterId: string, version: string): Promise<Blob> {
    const response = await backendFetch(`/api/update-site-imports/extension-adapters/${encodeURIComponent(adapterId)}/versions/${encodeURIComponent(version)}/export`);
    if (!response.ok) throw await readActionableApiError(response);
    return response.blob();
}

export async function loadCapabilityPerformanceReports(): Promise<CapabilityPerformanceReport[]> {
    return json(await backendFetch('/api/performance/capabilities/reports'));
}

export async function downloadCapabilityPerformanceHtml(reportId: string): Promise<Blob> {
    const response = await backendFetch(`/api/performance/capabilities/reports/${encodeURIComponent(reportId)}.html`);
    if (!response.ok) throw await readActionableApiError(response);
    return response.blob();
}


export async function previewCapabilitySemanticMerge(request: CapabilitySemanticMergeRequest): Promise<CapabilitySemanticMergePreview> {
    return json(await backendFetch('/api/capability-packages/semantic-merge/preview', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function applyCapabilitySemanticMerge(request: CapabilitySemanticMergeRequest): Promise<CapabilitySemanticMergeResult> {
    return json(await backendFetch('/api/capability-packages/semantic-merge/apply', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function createOfflineP2Mirror(snapshotId: string): Promise<UpdateSiteP2MirrorResult> {
    return json(await backendFetch(`/api/update-site-imports/remote/snapshots/${encodeURIComponent(snapshotId)}/mirror`, { method: 'POST' }));
}

export async function loadOfflineP2Mirror(mirrorId: string): Promise<UpdateSiteP2MirrorResult> {
    return json(await backendFetch(`/api/update-site-imports/mirrors/${encodeURIComponent(mirrorId)}`));
}

export async function downloadOfflineP2Mirror(mirrorId: string): Promise<Blob> {
    const response = await backendFetch(`/api/update-site-imports/mirrors/${encodeURIComponent(mirrorId)}/export`);
    if (!response.ok) throw await readActionableApiError(response);
    return response.blob();
}

export async function runAdapterBench(request: UpdateSiteAdapterBenchRequest): Promise<UpdateSiteAdapterBenchReport> {
    return json(await backendFetch('/api/update-site-imports/extension-adapters/bench', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function loadAdapterBenchReports(): Promise<UpdateSiteAdapterBenchReport[]> {
    return json(await backendFetch('/api/update-site-imports/extension-adapters/bench'));
}

export async function compareAdapterBenchReports(baseline: string, candidate: string): Promise<UpdateSiteAdapterBenchComparison> {
    return json(await backendFetch(`/api/update-site-imports/extension-adapters/bench/compare?baseline=${encodeURIComponent(baseline)}&candidate=${encodeURIComponent(candidate)}`));
}

export async function loadCompactUpdateSiteAnalysis(analysisId: string): Promise<UpdateSiteCompactAnalysis> {
    return json(await backendFetch(`/api/update-site-imports/analyses/${encodeURIComponent(analysisId)}/compact`));
}

export async function loadUpdateSiteEvidencePage(analysisId: string, page: number, size: number,
    query: string, confidence: string): Promise<UpdateSiteAnalysisPage<UpdateSiteElementProvenance>> {
    const parameters = new URLSearchParams({ page: String(page), size: String(size), query, confidence });
    return json(await backendFetch(`/api/update-site-imports/analyses/${encodeURIComponent(analysisId)}/evidence?${parameters.toString()}`));
}

export async function compareCapabilityPerformanceReports(baseline: string, candidate: string): Promise<CapabilityPerformanceComparison> {
    const parameters = new URLSearchParams({ baseline, candidate });
    return json(await backendFetch(`/api/performance/capabilities/reports/compare?${parameters.toString()}`));
}

export async function evaluateReleaseGate(request: HydraReleaseGateRequest): Promise<HydraReleaseGateReport> {
    return json(await backendFetch('/api/release-gates', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function loadReleaseGateReports(): Promise<HydraReleaseGateReport[]> {
    return json(await backendFetch('/api/release-gates'));
}

export async function downloadReleaseGateHtml(reportId: string): Promise<Blob> {
    const response = await backendFetch(`/api/release-gates/${encodeURIComponent(reportId)}/html`);
    if (!response.ok) throw await readActionableApiError(response);
    return response.blob();
}

export async function previewCapabilityOrchestration(request: CapabilityOrchestrationRequest): Promise<CapabilityOrchestrationPreview> {
    return json(await backendFetch('/api/capability-packages/orchestration/preview', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function applyCapabilityOrchestration(request: CapabilityOrchestrationRequest): Promise<CapabilityOrchestrationResult> {
    return json(await backendFetch('/api/capability-packages/orchestration/apply', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function evaluateMirrorUpdate(request: UpdateSiteMirrorUpdateRequest): Promise<UpdateSiteMirrorUpdateReport> {
    return json(await backendFetch('/api/update-site-imports/mirror-updates', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function loadMirrorChannel(channel: UpdateSiteMirrorChannel): Promise<UpdateSiteMirrorChannelState> {
    return json(await backendFetch(`/api/update-site-imports/mirror-updates/channels/${encodeURIComponent(channel)}`));
}

export async function rollbackMirrorChannel(channel: UpdateSiteMirrorChannel): Promise<UpdateSiteMirrorChannelState> {
    return json(await backendFetch(`/api/update-site-imports/mirror-updates/channels/${encodeURIComponent(channel)}/rollback`, { method: 'POST' }));
}

export async function verifyDeclarativeAdapter(request: UpdateSiteAdapterVerificationRequest): Promise<UpdateSiteAdapterVerificationReport> {
    return json(await backendFetch('/api/update-site-imports/extension-adapters/verification', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function runPerformanceAutomation(request: CapabilityPerformanceScenarioRequest): Promise<CapabilityPerformanceAutomationReport> {
    return json(await backendFetch('/api/performance/capabilities/automation', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function approvePerformanceBaseline(reportId: string): Promise<unknown> {
    return json(await backendFetch(`/api/performance/capabilities/automation/${encodeURIComponent(reportId)}/approve`, { method: 'POST' }));
}

export async function evaluateDistributionProvenance(request: HydraDistributionProvenanceRequest): Promise<HydraDistributionProvenanceReport> {
    return json(await backendFetch('/api/distribution-provenance', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}

export async function downloadDistributionSbom(reportId: string): Promise<Blob> {
    const response = await backendFetch(`/api/distribution-provenance/${encodeURIComponent(reportId)}/sbom`);
    if (!response.ok) throw await readActionableApiError(response);
    return response.blob();
}

export async function registerDistribution(provenanceReportId:string):Promise<unknown>{
    return json(await backendFetch(`/api/distribution-registry/entries?provenanceReportId=${encodeURIComponent(provenanceReportId)}`,{method:'POST'}));
}

export async function savePolicyRule(request:unknown):Promise<unknown>{
    return json(await backendFetch('/api/policies/rules',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)}));
}
export async function revokeDistributionSubject(request:unknown):Promise<unknown>{
    return json(await backendFetch('/api/policies/revocations',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)}));
}
export async function evaluatePolicyReport(provenanceReportId:string):Promise<unknown>{
    return json(await backendFetch('/api/policies/evaluate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({provenanceReportId,includeWarnings:true})}));
}
export async function createRecoveryBackup():Promise<unknown>{return json(await backendFetch('/api/recovery/backups',{method:'POST'}));}
export async function runRecoveryDrill(request:unknown):Promise<unknown>{
    return json(await backendFetch('/api/recovery/drills',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)}));
}



export async function saveFederatedCatalogSource(request:unknown):Promise<unknown>{
    return json(await backendFetch('/api/distribution-registry/federation/sources',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)}));
}
export async function synchronizeFederatedCatalog(request:unknown):Promise<unknown>{
    return json(await backendFetch('/api/distribution-registry/federation/synchronize',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)}));
}
export async function savePolicyBundle(request:unknown):Promise<unknown>{
    return json(await backendFetch('/api/policies/bundles',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)}));
}
export async function simulatePolicyBundle(bundleId:string,version:string,request:unknown):Promise<unknown>{
    return json(await backendFetch(`/api/policies/bundles/${encodeURIComponent(bundleId)}/${encodeURIComponent(version)}/simulate`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)}));
}
export async function activatePolicyBundle(bundleId:string,version:string,simulationReportId:string):Promise<unknown>{
    return json(await backendFetch(`/api/policies/bundles/${encodeURIComponent(bundleId)}/${encodeURIComponent(version)}/activate`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({simulationReportId})}));
}
export async function saveContinuityPlan(request:unknown):Promise<unknown>{
    return json(await backendFetch('/api/recovery/continuity/plans',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)}));
}
export async function runContinuityPlan(request:unknown):Promise<unknown>{
    return json(await backendFetch('/api/recovery/continuity/runs',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)}));
}
export async function compareContinuityRuns(baselineRunId:string,candidateRunId:string):Promise<unknown>{
    const parameters=new URLSearchParams({baselineRunId,candidateRunId});return json(await backendFetch(`/api/recovery/continuity/compare?${parameters.toString()}`));
}


