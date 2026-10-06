import type { MetamodelImportPreview } from '../metamodel-designer/importTypes';

export type UpdateSiteConfidence = 'EXACT' | 'RECOVERED' | 'DERIVED' | 'INFERRED';
export type UpdateSiteRecoveryMethod = 'DIRECT_METAMODEL' | 'GENMODEL' | 'PLUGIN_REGISTRY' | 'EMF_BYTECODE' | 'CODE_INFERENCE';
export type UpdateSiteBundleKind = 'MODEL_PLUGIN' | 'EDIT_PLUGIN' | 'EDITOR_PLUGIN' | 'GMF_DIAGRAM_PLUGIN'
    | 'SIRIUS_VIEWPOINT_PLUGIN' | 'XTEXT_LANGUAGE_PLUGIN' | 'RUNTIME_PLUGIN' | 'UI_PLUGIN'
    | 'FEATURE' | 'THIRD_PARTY_DEPENDENCY';
export type UpdateSiteImportStage = 'SOURCE' | 'P2_METADATA' | 'FEATURES' | 'BUNDLES' | 'METAMODEL_RECOVERY'
    | 'GRAMMAR_ANALYSIS' | 'LAYER_RECOVERY' | 'RECONCILIATION' | 'MATERIALIZATION'
    | 'RELEASE_ANALYSIS' | 'SEMANTIC_RECOVERY';
export type UpdateSiteLogSeverity = 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR';
export type UpdateSiteFileStatus = 'RECOVERED' | 'PARTIAL' | 'NOT_RECOVERABLE' | 'SUPPORTING' | 'FAILED';
export type UpdateSiteReleaseRisk = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH';
export type UpdateSiteSemanticKind = 'COMMAND' | 'HANDLER' | 'MENU' | 'EDITOR' | 'VIEW' | 'PROPERTY_PAGE'
    | 'PREFERENCE_PAGE' | 'FORM' | 'VALIDATION' | 'FILE_EXTENSION' | 'GENERATED_ITEM_PROVIDER'
    | 'RUNTIME_EXTENSION' | 'UNREPRESENTABLE';

export interface UpdateSiteDependency {
    sourceId: string; targetId: string; versionRange: string; optional: boolean; resolved: boolean; origin: string;
}
export interface UpdateSiteArtifactDescriptor {
    path: string; kind: string; bundleId: string; sizeBytes: number; sha256: string;
}
export interface UpdateSiteFeatureDescriptor {
    id: string; version: string; name: string; sourcePath: string; pluginIds: string[]; dependencies: UpdateSiteDependency[];
}
export interface UpdateSiteBundleDescriptor {
    id: string; version: string; name: string; kind: UpdateSiteBundleKind; sourcePath: string;
    classificationSignals: string[]; dependencies: UpdateSiteDependency[]; artifacts: UpdateSiteArtifactDescriptor[];
    manifestHeaders: Record<string, string>;
}
export interface UpdateSiteRepositorySummary {
    name: string; sourceFingerprint: string; contentMetadataFound: boolean; artifactMetadataFound: boolean;
    repositoryFileCount: number; repositorySizeBytes: number; features: UpdateSiteFeatureDescriptor[];
    bundles: UpdateSiteBundleDescriptor[]; unownedArtifacts: UpdateSiteArtifactDescriptor[];
    missingDependencies: UpdateSiteDependency[]; dependencyCycles: string[][]; diagnostics: string[];
}
export interface UpdateSiteRecoveryCandidate {
    id: string; level: number; method: UpdateSiteRecoveryMethod; confidence: UpdateSiteConfidence;
    sourcePath: string; sourceBundleId: string; format: string; score: number; importable: boolean;
    rationale: string; warnings: string[];
}
export interface UpdateSiteElementProvenance {
    elementId: string; elementPath: string; origin: string; sourcePath: string; format: string;
    originalElement: string; method: UpdateSiteRecoveryMethod; confidence: UpdateSiteConfidence; warnings: string[];
}
export interface UpdateSitePaletteToolDraft {
    id: string; label: string; kind: string; targetElement: string; sourcePath: string; confidence: UpdateSiteConfidence;
}
export interface UpdateSitePaletteGroupDraft {
    id: string; label: string; order: number; tools: UpdateSitePaletteToolDraft[]; sourcePath: string; confidence: UpdateSiteConfidence;
}
export interface UpdateSiteRepresentationDraft {
    id: string; name: string; technology: string; kind: string; rootElements: string[]; sourcePath: string;
    confidence: UpdateSiteConfidence; warnings: string[];
}
export interface UpdateSiteMappingDraft {
    id: string; semanticElement: string; visualElement: string; mappingKind: string; sourcePath: string;
    confidence: UpdateSiteConfidence; diagnostic: string;
}
export interface UpdateSiteRecoveredLayers {
    palettes: UpdateSitePaletteGroupDraft[]; representations: UpdateSiteRepresentationDraft[];
    mappings: UpdateSiteMappingDraft[]; diagnostics: string[];
}
export interface UpdateSiteImportLogEntry {
    id: string; stage: UpdateSiteImportStage; severity: UpdateSiteLogSeverity; status: UpdateSiteFileStatus;
    title: string; message: string; sourcePath: string; artifactKind: string; purpose: string;
    recoveredAs: string; nextAction: string; confidence: UpdateSiteConfidence; details: string[];
}
export interface UpdateSiteFileOutcome {
    sourcePath: string; artifactKind: string; purpose: string; status: UpdateSiteFileStatus;
    recoveredItems: number; recoveredAs: string; explanation: string; nextAction: string; relatedElements: string[];
}
export interface UpdateSiteGrammarInspection {
    sourcePath: string; language: string; grammarName: string; parserRules: number; terminalRules: number;
    imports: number; assignments: number; crossReferences: number; actions: number;
    duplicateRules: string[]; possibleLeftRecursions: string[]; inferredEntryRule: string; entryInferenceMethod: string; entryInferenceConfidence: number;
    recursiveRules: string[]; automaticResolutionRules: string[]; diagnostics: string[];
}
export interface UpdateSiteDashboardSlice { id: string; label: string; value: number; explanation: string; }
export interface UpdateSiteSankeyNode { id: string; label: string; column: number; explanation: string; }
export interface UpdateSiteSankeyLink { source: string; target: string; value: number; explanation: string; }
export interface UpdateSiteImportDashboard {
    inspectedFiles: number; recoveredFiles: number; partialFiles: number; supportingFiles: number;
    unrecoverableFiles: number; failedFiles: number; recoveryCoverage: number;
    confidence: UpdateSiteDashboardSlice[]; fileStatus: UpdateSiteDashboardSlice[];
    bundleRoles: UpdateSiteDashboardSlice[]; stages: UpdateSiteDashboardSlice[];
    flowNodes: UpdateSiteSankeyNode[]; flowLinks: UpdateSiteSankeyLink[];
}
export interface UpdateSiteCandidateCoverage {
    candidateId: string; confidence: UpdateSiteConfidence; elements: number; uniqueElements: number;
    conflictingElements: number; coveragePercent: number; recommendation: string;
}
export interface UpdateSiteReconciliationConflict {
    elementKey: string; elementKind: string; displayName: string; candidateSignatures: Record<string, string>;
    recommendedCandidateId: string; explanation: string; blocking: boolean;
}
export interface UpdateSiteReconciliationReport {
    candidates: UpdateSiteCandidateCoverage[]; conflicts: UpdateSiteReconciliationConflict[];
    agreedElements: number; unionElements: number; agreementPercent: number;
    recommendedCandidateId: string; mergeReady: boolean; diagnostics: string[];
}
export interface UpdateSiteCompositeRepository {
    sourcePath: string; location: string; remote: boolean; presentInUpload: boolean; explanation: string;
}
export interface UpdateSiteVersionFamily {
    id: string; artifactType: string; versions: string[]; oldestVersion: string; latestVersion: string;
    multipleVersions: boolean; recommendation: string;
}
export interface UpdateSiteVersionDelta {
    id: string; artifactType: string; fromVersion: string; toVersion: string; addedArtifacts: string[];
    removedArtifacts: string[]; changedArtifacts: string[]; unchangedArtifacts: number;
    modelBearing: boolean; risk: UpdateSiteReleaseRisk; explanation: string; recommendation: string;
}
export interface UpdateSiteReleaseComparison {
    compositeRepositories: UpdateSiteCompositeRepository[]; versionFamilies: UpdateSiteVersionFamily[];
    deltas: UpdateSiteVersionDelta[]; unresolvedCompositeChildren: number; diagnostics: string[];
}
export interface UpdateSiteSemanticElementDraft {
    id: string; kind: UpdateSiteSemanticKind; label: string; implementation: string; sourcePath: string;
    extensionPoint: string; confidence: UpdateSiteConfidence; representable: boolean; neutralTarget: string;
    purpose: string; warnings: string[];
}
export interface UpdateSiteSemanticRecovery {
    elements: UpdateSiteSemanticElementDraft[]; representableCount: number; partialCount: number;
    unrepresentableCount: number; diagnostics: string[];
}
export interface UpdateSiteImportAnalysis {
    analysisId: string; generatedAt: string; repository: UpdateSiteRepositorySummary;
    candidates: UpdateSiteRecoveryCandidate[]; selectedCandidateId: string; metamodelPreview: MetamodelImportPreview | null;
    provenance: UpdateSiteElementProvenance[]; recoveredLayers: UpdateSiteRecoveredLayers;
    importable: boolean; diagnostics: string[];
    dashboard?: UpdateSiteImportDashboard; logs?: UpdateSiteImportLogEntry[]; fileOutcomes?: UpdateSiteFileOutcome[];
    grammarInspections?: UpdateSiteGrammarInspection[]; reconciliation?: UpdateSiteReconciliationReport;
    releaseComparison?: UpdateSiteReleaseComparison; semanticRecovery?: UpdateSiteSemanticRecovery;
    performance?: UpdateSiteAnalysisPerformance;
}
export interface UpdateSiteImportResult {
    analysisId: string; metamodel: { id: string; etag: string; preview: MetamodelImportPreview };
    recoveryPackagePath: string; recoveredLayers: UpdateSiteRecoveredLayers;
}
export interface UpdateSiteImportOptions {
    targetId: string; overwrite: boolean; strict: boolean; candidateId: string;
    recoverPalettes: boolean; recoverRepresentations: boolean; recoverMappings: boolean;
    persistRecoveryPackage: boolean;
}
export interface UpdateSiteReconciliationRequest {
    targetId: string; primaryCandidateId: string; conflictCandidateIds: Record<string, string>;
}
export interface UpdateSiteReconciliationResult {
    report: UpdateSiteReconciliationReport; preview: MetamodelImportPreview | null; applicable: boolean;
}
export interface UpdateSiteLayerSelection {
    paletteGroupIds: string[]; paletteToolIds: string[]; representationIds: string[];
    mappingIds: string[]; semanticElementIds: string[]; explicit: boolean;
}
export interface UpdateSiteExtensionAdapterDescriptor {
    id: string; label: string; extensionPointTokens: string[]; elementNames: string[];
    semanticKind: UpdateSiteSemanticKind; neutralTarget: string; purpose: string;
    idAttributes: string[]; implementationAttributes: string[];
    confidence: UpdateSiteConfidence; enabled: boolean;
}
export interface UpdateSiteLayerMaterializationRequest {
    targetId: string; candidateId: string; editorId: string; editorName: string;
    strict: boolean; persist: boolean; selection: UpdateSiteLayerSelection;
}
export interface UpdateSiteLayerMaterializationPreview {
    analysisId: string; editorDefinition: unknown; checkReport: { editorDefinitionId: string; diagnostics: Array<{ severity: string; code: string; message: string }> };
    selectedPaletteGroups: number; selectedTools: number; selectedRepresentations: number;
    selectedMappings: number; selectedSemanticElements: number; applicable: boolean; diagnostics: string[];
}
export interface UpdateSiteLayerMaterializationResult {
    preview: UpdateSiteLayerMaterializationPreview; metamodelId: string; metamodelEtag: string;
    editorId: string; editorEtag: string; persisted: boolean; rollbackAvailable: boolean; diagnostics: string[];
}
export interface UpdateSiteLayerMaterializationRollbackRequest {
    metamodelId: string; metamodelEtag: string; editorId: string; editorEtag: string;
}
export interface UpdateSiteLayerMaterializationRollbackResult {
    editorDeleted: boolean; metamodelDeleted: boolean; diagnostics: string[];
}

export type UpdateSiteMergeDecision = 'KEEP_EXISTING' | 'USE_RECOVERED' | 'SKIP';
export interface UpdateSiteLayerMergeRequest {
    targetMetamodelId: string; targetEditorId: string; expectedEtag: string; candidateId: string;
    strict: boolean; persist: boolean; selection: UpdateSiteLayerSelection;
    defaultDecision: UpdateSiteMergeDecision; decisions: Record<string, UpdateSiteMergeDecision>;
}
export interface UpdateSiteLayerMergeItem {
    key: string; kind: string; displayName: string; existingPresent: boolean; recoveredPresent: boolean;
    conflict: boolean; recommendedDecision: UpdateSiteMergeDecision; selectedDecision: UpdateSiteMergeDecision;
    explanation: string;
}
export interface UpdateSiteLayerMergePreview {
    transactionId: string; targetMetamodelId: string; targetEditorId: string; originalEtag: string;
    mergedEditorDefinition: unknown; checkReport: { editorDefinitionId: string; diagnostics: Array<{ severity: string; code: string; message: string }> };
    items: UpdateSiteLayerMergeItem[]; keptElements: number; recoveredElements: number; skippedElements: number;
    applicable: boolean; diagnostics: string[];
}
export interface UpdateSiteLayerMergeResult {
    preview: UpdateSiteLayerMergePreview; persisted: boolean; editorEtag: string;
    rollbackAvailable: boolean; diagnostics: string[];
}
export interface UpdateSiteLayerMergeRollbackRequest { transactionId: string; expectedCurrentEtag: string; }
export interface UpdateSiteLayerMergeRollbackResult { restored: boolean; editorEtag: string; diagnostics: string[]; }
export interface UpdateSiteRemoteAnalysisRequest {
    repositoryUrl: string; expectedSha256: string; maximumDepth: number; useCache: boolean;
    options: UpdateSiteImportOptions;
}
export interface UpdateSiteRemoteRepositoryFetch {
    url: string; virtualPath: string; bytes: number; sha256: string; cacheHit: boolean; depth: number;
}
export interface UpdateSiteRemoteAnalysisResult {
    analysis: UpdateSiteImportAnalysis; fetchedResources: UpdateSiteRemoteRepositoryFetch[];
    downloadedBytes: number; cacheHits: number; diagnostics: string[];
}
export interface UpdateSiteDeclarativeAdapterDocument {
    schemaVersion: string; adapter: UpdateSiteExtensionAdapterDescriptor;
    annotations: Record<string, string>;
}
export interface UpdateSiteDeclarativeAdapterValidation {
    valid: boolean; document: UpdateSiteDeclarativeAdapterDocument | null; fingerprint: string;
    errors: string[]; warnings: string[];
}
export interface UpdateSiteDeclarativeAdapterImportResult {
    validation: UpdateSiteDeclarativeAdapterValidation; persisted: boolean; replaced: boolean; fileName: string;
}
export interface UpdateSiteAnalysisPerformance {
    cacheHit: boolean; totalMillis: number; repositoryBytes: number; repositoryFiles: number; cacheEntries: number;
    stageMillis?: Record<string, number>;
}
export interface UpdateSiteAnalysisCacheMetrics { entries: number; hits: number; misses: number; writes: number; maximumEntries: number; }

export interface UpdateSiteRemoteSnapshotResource {
    url: string; virtualPath: string; bytes: number; sha256: string; depth: number; objectPath: string;
}
export interface UpdateSiteRemoteSnapshotLicenseEntry {
    virtualPath: string; author: string; origin: string; license: string; obligations: string;
    compatible: boolean; redistributionAllowed: boolean;
}
export interface UpdateSiteRemoteSnapshotLock {
    schemaVersion: string; snapshotId: string; rootUrl: string; treeSha256: string; capturedAt: string;
    totalBytes: number; resources: UpdateSiteRemoteSnapshotResource[]; licenses: UpdateSiteRemoteSnapshotLicenseEntry[];
}
export interface UpdateSiteRemoteSnapshotResult {
    lock: UpdateSiteRemoteSnapshotLock; analysis: UpdateSiteImportAnalysis; reused: boolean; diagnostics: string[];
}
export interface UpdateSiteRemoteSnapshotAnalyzeRequest { snapshotId: string; options: UpdateSiteImportOptions; }
export interface UpdateSiteRemoteSnapshotDriftItem {
    url: string; expectedSha256: string; actualSha256: string; status: 'UNCHANGED' | 'CHANGED' | 'UNAVAILABLE'; explanation: string;
}
export interface UpdateSiteRemoteSnapshotDriftReport {
    snapshotId: string; drifted: boolean; unchanged: number; changed: number; unavailable: number;
    items: UpdateSiteRemoteSnapshotDriftItem[];
}
export interface UpdateSiteAdapterFixture { extensionPoint: string; elementName: string; attributes: Record<string, string>; }
export interface UpdateSiteAdapterFixtureResult {
    validAdapter: boolean; matched: boolean; resolvedId: string; implementation: string; neutralTarget: string;
    explanations: string[]; errors: string[];
}
export interface UpdateSiteDeclarativeAdapterEntry {
    document: UpdateSiteDeclarativeAdapterDocument; fileName: string; fingerprint: string;
    version: string; availableVersions: string[];
}
export interface UpdateSiteDeclarativeAdapterVersionEntry {
    adapterId: string; version: string; fileName: string; fingerprint: string; current: boolean;
}
export interface CapabilityPerformanceBudget {
    capabilityId: string; maxRenderMillis: number; maxHeapBytes: number; maxRequests: number;
    maxResponseBytes: number; maxAnalysisMillis: number;
}
export interface CapabilityPerformanceSample {
    runId: string; capabilityId: string; capturedAt: string; renderMillis: number; heapBytes: number;
    requestCount: number; responseBytes: number; analysisMillis: number;
    stageMillis: Record<string, number>; chunkBytes: Record<string, number>;
}
export interface CapabilityPerformanceEvaluation { passed: boolean; violations: string[]; recommendations: string[]; }
export interface CapabilityPerformanceReport {
    reportId: string; budget: CapabilityPerformanceBudget; sample: CapabilityPerformanceSample;
    evaluation: CapabilityPerformanceEvaluation;
}

export interface HydraCapabilityPackage {
    manifest: Record<string, unknown> | null;
    canonicalDocuments: Record<string, unknown>;
    resources: Record<string, string>;
}
export type CapabilitySemanticMergeDecision = 'KEEP_EXISTING' | 'USE_RECOVERED' | 'MERGE' | 'SKIP';
export interface CapabilitySemanticMergeRequest {
    existingPackage: HydraCapabilityPackage; recoveredPackage: HydraCapabilityPackage;
    targetMetamodelId: string; expectedEtags: Record<string, string>; layerOrder: string[];
    defaultDecision: CapabilitySemanticMergeDecision; decisions: Record<string, CapabilitySemanticMergeDecision>;
    persist: boolean;
}
export interface CapabilitySemanticMergeItem {
    key: string; documentPath: string; jsonPointer: string; semanticKind: string; displayName: string;
    existingPresent: boolean; recoveredPresent: boolean; conflict: boolean;
    recommendedDecision: CapabilitySemanticMergeDecision; selectedDecision: CapabilitySemanticMergeDecision;
    dependencyReferences: string[]; explanation: string;
}
export interface CapabilitySemanticMergeConflict {
    key: string; type: string; severity: string; existingFingerprint: string;
    recoveredFingerprint: string; explanation: string;
}
export interface CapabilitySemanticMergeDependency {
    sourceKey: string; targetReference: string; resolved: boolean; explanation: string;
}
export interface CapabilitySemanticMergePreview {
    previewId: string; mergedPackage: HydraCapabilityPackage; items: CapabilitySemanticMergeItem[];
    conflicts: CapabilitySemanticMergeConflict[]; dependencies: CapabilitySemanticMergeDependency[];
    keptElements: number; recoveredElements: number; mergedElements: number; skippedElements: number;
    applicable: boolean; diagnostics: string[];
}
export interface CapabilityLayerTransactionResult {
    preview: unknown; transactionId: string; persisted: boolean; rollbackAvailable: boolean;
    appliedEtags: Record<string, string>; diagnostics: string[];
}
export interface CapabilitySemanticMergeResult {
    preview: CapabilitySemanticMergePreview; transaction: CapabilityLayerTransactionResult;
}

export interface UpdateSiteP2MirrorArtifact {
    classifier: string; id: string; version: string; path: string; bytes: number; sha256: string; license: string;
}
export interface UpdateSiteP2MirrorManifest {
    schemaVersion: string; mirrorId: string; snapshotId: string; snapshotTreeSha256: string;
    mirrorSha256: string; createdAt: string; artifacts: UpdateSiteP2MirrorArtifact[];
    contentIndexSha256: string; artifactIndexSha256: string; licenseReviewComplete: boolean;
}
export interface UpdateSiteP2MirrorValidation {
    valid: boolean; declaredUnits: number; declaredArtifacts: number; verifiedArtifacts: number;
    missingArtifacts: string[]; diagnostics: string[];
}
export interface UpdateSiteP2MirrorResult {
    manifest: UpdateSiteP2MirrorManifest; validation: UpdateSiteP2MirrorValidation; reused: boolean;
}

export interface UpdateSiteAdapterBenchCase {
    name: string; fixture: UpdateSiteAdapterFixture; expectedMatch: boolean;
}
export interface UpdateSiteAdapterBenchRequest {
    adapterId: string; version: string; document: string; fixtures: UpdateSiteAdapterBenchCase[];
    includeMutations: boolean;
}
export interface UpdateSiteAdapterBenchCaseResult {
    name: string; expectedMatch: boolean; actualMatch: boolean; passed: boolean;
    resolvedId: string; implementation: string; explanations: string[]; errors: string[];
}
export interface UpdateSiteAdapterMutationResult {
    fixtureName: string; mutation: string; baselineMatched: boolean; mutatedMatched: boolean;
    killed: boolean; explanation: string;
}
export interface UpdateSiteAdapterCoverage {
    coveredTokens: number; totalTokens: number; coveredElements: number; totalElements: number;
    coveredIdAttributes: number; totalIdAttributes: number; coveredImplementationAttributes: number;
    totalImplementationAttributes: number; uncoveredTokens: string[]; uncoveredElements: string[];
    uncoveredIdAttributes: string[]; uncoveredImplementationAttributes: string[];
    redundantRules: string[]; unreachableRules: string[];
}
export interface UpdateSiteAdapterBenchReport {
    reportId: string; adapterId: string; adapterVersion: string; adapterFingerprint: string;
    createdAt: string; passedFixtures: number; failedFixtures: number; killedMutations: number;
    survivingMutations: number; coverage: UpdateSiteAdapterCoverage;
    fixtures: UpdateSiteAdapterBenchCaseResult[]; mutations: UpdateSiteAdapterMutationResult[];
    diagnostics: string[];
}
export interface UpdateSiteAdapterBenchComparison {
    baselineReportId: string; candidateReportId: string; fixturePassDelta: number;
    killedMutationDelta: number; tokenCoverageDelta: number; elementCoverageDelta: number;
    regression: boolean; diagnostics: string[];
}

export interface UpdateSiteAnalysisPage<T> {
    analysisId: string; collection: string; page: number; size: number; totalElements: number;
    totalPages: number; estimatedBytes: number; items: T[];
}
export interface UpdateSiteCompactAnalysis {
    analysis: UpdateSiteImportAnalysis; collectionSizes: Record<string, number>;
    estimatedFullResponseBytes: number;
}
export interface CapabilityPerformanceComparison {
    baselineReportId: string; candidateReportId: string; capabilityId: string;
    renderDelta: number; heapDelta: number; requestDelta: number; responseDelta: number;
    analysisDelta: number; stageDeltas: Record<string, number>; chunkDeltas: Record<string, number>;
    regression: boolean; diagnostics: string[];
}

export type HydraReleaseGateStatus = 'PASS' | 'WARNING' | 'FAIL';
export interface HydraReleaseGateCheck { code: string; status: HydraReleaseGateStatus; message: string; evidence: string; }
export interface HydraReleaseGateRequest {
    productId: string; version: string; capabilityPackage: HydraCapabilityPackage;
    semanticMerge: CapabilitySemanticMergePreview | null; mirrorId: string;
    adapterBenchReportIds: string[]; performanceBaselineReportId: string;
    performanceCandidateReportId: string; requireSemanticMerge: boolean; requireMirror: boolean;
    requireAdapterBench: boolean; requirePerformance: boolean; annotations: Record<string, string>;
}
export interface HydraReleaseGateReport {
    reportId: string; productId: string; version: string; createdAt: string; passed: boolean;
    packageFingerprint: string; mirrorId: string; adapterBenchReportIds: string[];
    performanceBaselineReportId: string; performanceCandidateReportId: string;
    checks: HydraReleaseGateCheck[]; attestationSha256: string; annotations: Record<string, string>;
}

export interface CapabilityOrchestrationSource {
    sourceId: string; precedence: number; afterSourceIds: string[];
    capabilityPackage: HydraCapabilityPackage; defaultDecision: CapabilitySemanticMergeDecision;
    decisions: Record<string, CapabilitySemanticMergeDecision>;
}
export interface CapabilityOrchestrationRequest {
    basePackage: HydraCapabilityPackage; sources: CapabilityOrchestrationSource[];
    targetMetamodelId: string; expectedEtags: Record<string, string>; layerOrder: string[]; persist: boolean;
}
export interface CapabilityOrchestrationStep {
    index: number; sourceId: string; previewId: string; policyFingerprint: string;
    inputFingerprint: string; outputFingerprint: string; applicable: boolean; diagnostics: string[];
}
export interface CapabilityOrchestrationPreview {
    orchestrationId: string; mergedPackage: HydraCapabilityPackage; precedenceOrder: string[];
    steps: CapabilityOrchestrationStep[]; applicable: boolean; diagnostics: string[];
}
export interface CapabilityOrchestrationResult {
    preview: CapabilityOrchestrationPreview; transaction: CapabilityLayerTransactionResult;
}

export type UpdateSiteMirrorChannel = 'INTERNAL' | 'BETA' | 'STABLE';
export type UpdateSiteMirrorDeltaKind = 'ADDED' | 'REMOVED' | 'CHANGED' | 'UNCHANGED';
export interface UpdateSiteMirrorDeltaItem {
    coordinate: string; classifier: string; baselineVersion: string; candidateVersion: string;
    baselineSha256: string; candidateSha256: string; kind: UpdateSiteMirrorDeltaKind;
}
export interface UpdateSiteMirrorUpdateRequest {
    baselineMirrorId: string; candidateMirrorId: string; channel: UpdateSiteMirrorChannel;
    promote: boolean; allowRemovals: boolean; retainPrevious: boolean; validateRollback: boolean;
}
export interface UpdateSiteMirrorUpdateReport {
    reportId: string; baselineMirrorId: string; candidateMirrorId: string; channel: UpdateSiteMirrorChannel;
    createdAt: string; added: number; removed: number; changed: number; unchanged: number;
    deltas: UpdateSiteMirrorDeltaItem[]; rollbackValid: boolean; promotable: boolean;
    promoted: boolean; diagnostics: string[];
}
export interface UpdateSiteMirrorChannelState {
    channel: UpdateSiteMirrorChannel; currentMirrorId: string; updatedAt: string; history: string[];
}

export interface UpdateSiteAdapterVerificationRequest {
    adapterId: string; version: string; document: string; fuzzCases: number; invariants: string[];
}
export interface UpdateSiteAdapterVerificationFailure {
    caseName: string; invariant: string; fixture: UpdateSiteAdapterFixture;
    minimizedFixture: UpdateSiteAdapterFixture; explanation: string;
}
export interface UpdateSiteAdapterVerificationReport {
    reportId: string; adapterId: string; adapterVersion: string; adapterFingerprint: string;
    createdAt: string; benchReportId: string; generatedFixtures: number; fuzzCases: number;
    invariantChecks: number; failures: number; failureDetails: UpdateSiteAdapterVerificationFailure[];
    passed: boolean; diagnostics: string[];
}

export interface CapabilityPerformancePercentiles {
    renderP50: number; renderP95: number; heapP50: number; heapP95: number;
    requestsP50: number; requestsP95: number; responseP50: number; responseP95: number;
    analysisP50: number; analysisP95: number; stageP95: Record<string, number>; chunkP95: Record<string, number>;
}
export interface CapabilityPerformanceScenarioRequest {
    scenarioId: string; capabilityId: string; warmupCount: number; samples: CapabilityPerformanceSample[];
    budget: CapabilityPerformanceBudget; allowedNoisePercent: number; approveBaseline: boolean;
}
export interface CapabilityPerformanceAutomationReport {
    reportId: string; scenarioId: string; capabilityId: string; createdAt: string; warmupCount: number;
    measuredSamples: number; allowedNoisePercent: number; observedNoisePercent: number; noisy: boolean;
    percentiles: CapabilityPerformancePercentiles; performanceReportId: string; baselineId: string;
    regression: boolean; passed: boolean; diagnostics: string[];
}

export type HydraLicensePolicy = 'ALLOW_UNKNOWN' | 'COMPATIBLE_ONLY' | 'STRICT';
export interface HydraSbomComponent {
    componentId: string; type: string; name: string; version: string; sha256: string;
    license: string; origin: string; relationships: string[]; attributes: Record<string, string>;
}
export interface HydraDistributionProvenanceRequest {
    productId: string; version: string; channel: UpdateSiteMirrorChannel; licensePolicy: HydraLicensePolicy;
    capabilityPackage: HydraCapabilityPackage; orchestrationId: string; mirrorUpdateReportId: string;
    adapterVerificationReportIds: string[]; performanceAutomationReportId: string;
    releaseGateReportId: string; annotations: Record<string, string>;
}
export interface HydraDistributionProvenanceReport {
    reportId: string; schemaVersion: string; productId: string; version: string;
    channel: UpdateSiteMirrorChannel; licensePolicy: HydraLicensePolicy; createdAt: string;
    components: HydraSbomComponent[]; valid: boolean; diagnostics: string[];
    attestationSha256: string; annotations: Record<string, string>;
}

export interface UpdateSiteRepositorySourceRequest {
    repositoryUrl: string;
    reference: string;
    subdirectory: string;
    profileId: string;
    options: UpdateSiteImportOptions;
}
export interface UpdateSiteDetectedRoot {
    path: string;
    score: number;
    markers: string[];
    recommended: boolean;
}
export interface UpdateSiteRepositorySourceSummary {
    snapshotId: string;
    repositoryUrl: string;
    provider: 'GITHUB' | 'GITLAB' | 'GIT' | string;
    reference: string;
    selectedSubdirectory: string;
    fileCount: number;
    totalBytes: number;
    skippedPaths: string[];
    diagnostics: string[];
    detectedRoots: UpdateSiteDetectedRoot[];
}
export interface UpdateSiteRepositorySourceAnalysis {
    analysis: UpdateSiteImportAnalysis;
    source: UpdateSiteRepositorySourceSummary;
}
