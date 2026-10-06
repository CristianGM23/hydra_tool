import type { CanonicalModelDocument, ExpressionDefinition } from '../validation/types';

export type TransformationKind = 'M2M' | 'M2T' | 'T2M';
export type TransformationEndpointRole = 'SOURCE' | 'TARGET';
export type TransformationDirectionality = 'UNIDIRECTIONAL' | 'BIDIRECTIONAL';
export type TransformationRuleDirection = 'FORWARD' | 'REVERSE';
export type TransformationExecutionDirection = 'FORWARD' | 'REVERSE';
export type TransformationConflictPolicy = 'FAIL_ON_CONFLICT' | 'FORWARD_WINS' | 'REVERSE_WINS';
export type TransformationBindingKind = 'DIRECT' | 'CONSTANT' | 'EXPRESSION' | 'TRACE_EQUIVALENT';
export type TransformationExecutionMode = 'BATCH';
export type TransformationCollectionMode = 'PRESERVE' | 'FIRST' | 'LAST' | 'COUNT';
export type TransformationConversionMode = 'NONE' | 'TO_STRING' | 'TO_INTEGER' | 'TO_LONG' | 'TO_DOUBLE' | 'TO_BOOLEAN';
export type TransformationBindingErrorPolicy = 'FAIL' | 'SKIP_VALUE' | 'USE_FALLBACK';
export type GenerationOverwritePolicy = 'FAIL' | 'REPLACE';

export interface TransformationEndpoint {
    id: string;
    role: TransformationEndpointRole;
    metamodelId: string;
    metamodelFingerprint: string;
    alias: string;
    required: boolean;
    annotations: Record<string, string>;
}

export interface TransformationTargetPattern {
    id: string;
    endpointId: string;
    typeId: string;
    alias: string;
    annotations: Record<string, string>;
}

export interface TransformationValuePolicy {
    collectionMode: TransformationCollectionMode;
    conversionMode: TransformationConversionMode;
    enumMapping: Record<string, string>;
    errorPolicy: TransformationBindingErrorPolicy;
    fallbackValue?: unknown;
}

export interface TransformationBinding {
    id: string;
    targetAlias: string;
    targetFeatureId: string;
    kind: TransformationBindingKind;
    sourceFeatureId: string;
    literalValue?: unknown;
    expression?: ExpressionDefinition;
    valuePolicy?: TransformationValuePolicy;
    annotations: Record<string, string>;
}

export interface TransformationRule {
    id: string;
    name: string;
    enabled: boolean;
    sourceEndpointId: string;
    sourceTypeId: string;
    sourceAlias: string;
    guard?: ExpressionDefinition;
    targets: TransformationTargetPattern[];
    bindings: TransformationBinding[];
    annotations: Record<string, string>;
    direction: TransformationRuleDirection;
    correspondenceId: string;
}


export interface TransformationGenerationRule {
    id: string;
    name: string;
    enabled: boolean;
    sourceEndpointId: string;
    contextTypeId: string;
    guard?: ExpressionDefinition;
    outputPathTemplate: string;
    contentTemplate: string;
    overwritePolicy: GenerationOverwritePolicy;
    annotations: Record<string, string>;
}

export interface TransformationTextToModelConfiguration {
    grammarId: string;
    relativePath: string;
    postTransformationId: string;
}

export interface TransformationExecutionPolicy {
    mode: TransformationExecutionMode;
    deterministic: boolean;
    failOnBindingError: boolean;
}

export interface TransformationTracePolicy {
    enabled: boolean;
    retainBindingDetails: boolean;
}

export interface TransformationSynchronizationPolicy {
    conflictPolicy: TransformationConflictPolicy;
    propagateDeletions: boolean;
    requireTraceForUpdates: boolean;
}

export interface TransformationDefinitionDocument {
    schemaVersion: '1.0' | '1.1';
    id: string;
    name: string;
    kind: TransformationKind;
    directionality: TransformationDirectionality;
    synchronizationPolicy: TransformationSynchronizationPolicy;
    sources: TransformationEndpoint[];
    targets: TransformationEndpoint[];
    rules: TransformationRule[];
    generationRules?: TransformationGenerationRule[];
    textToModel?: TransformationTextToModelConfiguration;
    executionPolicy: TransformationExecutionPolicy;
    tracePolicy: TransformationTracePolicy;
    annotations: Record<string, string>;
}

export type TransformationDiagnosticSeverity = 'ERROR' | 'WARNING' | 'INFO';

export interface TransformationDiagnostic {
    severity: TransformationDiagnosticSeverity;
    code: string;
    ruleId: string;
    bindingId: string;
    path: string;
    title: string;
    message: string;
    solution: string;
}

export interface TransformationCheckReport {
    transformationId: string;
    diagnostics: TransformationDiagnostic[];
}

export interface TransformationSummary {
    id: string;
    name: string;
    kind: TransformationKind;
    directionality: TransformationDirectionality;
    sourceMetamodelId: string;
    targetMetamodelId: string;
    ruleCount: number;
    etag: string;
}

export interface TransformationLoadResult {
    transformation: TransformationDefinitionDocument;
    etag: string;
    checkReport: TransformationCheckReport;
}

export interface TransformationSaveResponse {
    transformation: TransformationDefinitionDocument;
    checkReport: TransformationCheckReport;
    etag: string;
}

export interface TransformationTraceLink { sourceElementId: string; ruleId: string; targetAlias: string; targetElementId: string; correspondenceId: string; direction: TransformationExecutionDirection; }
export interface TransformationBindingTrace { ruleId: string; bindingId: string; kind: TransformationBindingKind; sourceElementId: string; targetElementId: string; sourceFeatureId: string; targetFeatureId: string; }
export interface TransformationTrace { executionId: string; transformationId: string; sourceModelId: string; targetModelId: string; links: TransformationTraceLink[]; bindingTraces: TransformationBindingTrace[]; direction: TransformationExecutionDirection; }
export interface TransformationExecutionResult { targetModel: CanonicalModelDocument; trace: TransformationTrace; }

export type TransformationSynchronizationSide = 'SOURCE' | 'TARGET' | 'BOTH';
export type TransformationSynchronizationMode = 'BATCH' | 'INCREMENTAL';
export interface TransformationModelDelta { addedElementIds:string[]; removedElementIds:string[]; modifiedElementIds:string[]; modifiedAttributeIds:Record<string,string[]>; addedRelationIds:string[]; removedRelationIds:string[]; modifiedRelationIds:string[]; }
export interface TransformationSynchronizationConflict { correspondenceId:string; sourceElementId:string; targetElementId:string; sourceFeatures:string[]; targetFeatures:string[]; reason:string; }
export interface TransformationSynchronizationRequest { sourceModel:CanonicalModelDocument; targetModel:CanonicalModelDocument; changedSide:TransformationSynchronizationSide; mode?:TransformationSynchronizationMode; baselineSourceModel?:CanonicalModelDocument|null; baselineTargetModel?:CanonicalModelDocument|null; baselineTrace?:TransformationTrace|null; }
export interface TransformationSynchronizationResult { sourceModel:CanonicalModelDocument; targetModel:CanonicalModelDocument; appliedDirection:TransformationExecutionDirection; conflictResolution:string; trace:TransformationTrace|null; mode:TransformationSynchronizationMode; appliedDirections:TransformationExecutionDirection[]; sourceDelta:TransformationModelDelta; targetDelta:TransformationModelDelta; conflicts:TransformationSynchronizationConflict[]; blocked:boolean; noOp:boolean; }

export interface TransformationSynchronizationSession { schemaVersion:'1.0'; id:string; transformationId:string; transformationEtag:string; revision:number; sourceBaseline:CanonicalModelDocument; targetBaseline:CanonicalModelDocument; baselineTrace:TransformationTrace; createdAtEpochMillis:number; updatedAtEpochMillis:number; }
export interface TransformationSynchronizationSessionSummary { id:string; transformationId:string; transformationEtag:string; stale:boolean; revision:number; sourceModelId:string; targetModelId:string; createdAtEpochMillis:number; updatedAtEpochMillis:number; }
export interface TransformationSynchronizationCheckpoint { schemaVersion:'1.0'; id:string; sessionId:string; transformationId:string; transformationEtag:string; revision:number; sourceBaseline:CanonicalModelDocument; targetBaseline:CanonicalModelDocument; baselineTrace:TransformationTrace; operation:string; conflictResolution:string; appliedDirections:TransformationExecutionDirection[]; sourceDelta:TransformationModelDelta; targetDelta:TransformationModelDelta; conflicts:TransformationSynchronizationConflict[]; createdAtEpochMillis:number; }
export interface TransformationSynchronizationSessionCreateRequest { sourceModel:CanonicalModelDocument; targetModel:CanonicalModelDocument; initializeFrom:Exclude<TransformationSynchronizationSide,'BOTH'>; }
export interface TransformationSynchronizationSessionUpdateRequest { sourceModel:CanonicalModelDocument; targetModel:CanonicalModelDocument; }
export interface TransformationSynchronizationSessionMutationResult { session:TransformationSynchronizationSession; synchronization:TransformationSynchronizationResult|null; advanced:boolean; restoredFromRevision:number|null; etag:string; }
export interface TransformationSynchronizationSessionLoadResult { session:TransformationSynchronizationSession; etag:string; }

export interface TransformationTextArtifact { path:string; content:string; ruleId:string; sourceElementId:string; }
export interface TransformationGenerationResult { transformationId:string; artifacts:TransformationTextArtifact[]; }
export interface TransformationTextToModelResult { parsedModel:CanonicalModelDocument|null; projectedModel:CanonicalModelDocument|null; projectionTrace:TransformationTrace|null; diagnostics:unknown[]; grammarSnapshotId:string; }

export type TransformationExportLanguage = 'ATL' | 'ETL' | 'JAVA' | 'XTEND' | 'ACCELEO' | 'EGL' | 'PYTHON' | 'GO' | 'JAVASCRIPT' | 'TYPESCRIPT';
export type TransformationExportFidelity = 'EXACT' | 'CANONICAL_RUNTIME' | 'SUBSET';
export interface TransformationExportDiagnostic { severity:string; code:string; path:string; message:string; solution:string; }
export interface TransformationExportArtifact { path:string; content:string; }
export interface TransformationExportResult { transformationId:string; language:TransformationExportLanguage; fidelity:TransformationExportFidelity; compatible:boolean; diagnostics:TransformationExportDiagnostic[]; artifacts:TransformationExportArtifact[]; }

export type TransformationParityStatus = 'MATCHED'|'MISMATCHED'|'EXPORT_BLOCKED'|'INVALID_EVIDENCE';
export interface TransformationParityArtifact { path:string; content:string; }
export interface TransformationParityDifference { path:string; expected:string; actual:string; }
export interface TransformationParityRequest { transformation:TransformationDefinitionDocument; language:TransformationExportLanguage; direction:TransformationExecutionDirection; sourceModel:CanonicalModelDocument; nativeTargetModel?:CanonicalModelDocument|null; nativeArtifacts:TransformationParityArtifact[]; }
export interface TransformationParityReport { transformationId:string; language:TransformationExportLanguage; kind:TransformationKind; direction:TransformationExecutionDirection; status:TransformationParityStatus; compatible:boolean; matched:boolean; canonicalFingerprint:string; nativeFingerprint:string; exportDiagnostics:TransformationExportDiagnostic[]; differences:TransformationParityDifference[]; }

export type TransformationNativeRuntimeStatus = 'NOT_RUN'|'SUCCEEDED'|'DISABLED'|'NOT_CONFIGURED'|'CAPACITY_BUSY'|'TOOL_UNAVAILABLE'|'TIMED_OUT'|'FAILED'|'INVALID_RESULT'|'CANCELLED';
export type TransformationNativeRuntimeMode = 'NATIVE_EXPORT'|'CANONICAL_BUNDLE';
export type TransformationNativeRuntimeSource = 'NONE'|'EXPLICIT_COMMAND'|'MANAGED_BUNDLE';
export type TransformationNativeRuntimeHealth = 'DISABLED'|'NOT_CONFIGURED'|'CONFIGURATION_INVALID'|'UNVERIFIED'|'READY'|'TOOL_UNAVAILABLE'|'INCOMPATIBLE'|'FAILED';
export interface TransformationNativeRuntimeDescriptor { language:TransformationExportLanguage; enabled:boolean; configured:boolean; parityReady:boolean; mode:TransformationNativeRuntimeMode; protocolVersion:string; supportedKinds:TransformationKind[]; supportedDirections:TransformationExecutionDirection[]; fullBidirectionalSynchronization:boolean; incrementalSynchronization:boolean; source:TransformationNativeRuntimeSource; health:TransformationNativeRuntimeHealth; }
export interface TransformationNativeRuntimeDiagnostic { language:TransformationExportLanguage; health:TransformationNativeRuntimeHealth; source:TransformationNativeRuntimeSource; configured:boolean; compatible:boolean; bridgeName:string; bridgeVersion:string; toolchainName:string; toolchainVersion:string; protocolVersions:string[]; supportedKinds:TransformationKind[]; supportedDirections:TransformationExecutionDirection[]; fullBidirectionalSynchronization:boolean; incrementalSynchronization:boolean; durationMillis:number; diagnostics:string[]; }
export interface TransformationNativeRuntimeReport { language:TransformationExportLanguage; kind:TransformationKind; direction:TransformationExecutionDirection; status:TransformationNativeRuntimeStatus; durationMillis:number; exitCode:number|null; diagnostics:string[]; }
export interface TransformationNativeParityRequest { transformation:TransformationDefinitionDocument; language:TransformationExportLanguage; direction:TransformationExecutionDirection; sourceModel:CanonicalModelDocument; }
export interface TransformationNativeParityReport { transformationId:string; language:TransformationExportLanguage; kind:TransformationKind; execution:TransformationNativeRuntimeReport; parity:TransformationParityReport|null; }

export type TransformationToolchainProvisioningStatus = 'INSTALLED'|'UPDATED'|'REMOVED'|'NO_CHANGE'|'BLOCKED'|'ROLLED_BACK';
export interface TransformationToolchainPackageDescriptor { language:TransformationExportLanguage; packageId:string; packageVersion:string; bridgeName:string; bridgeVersion:string; toolchainName:string; toolchainVersion:string; installable:boolean; installed:boolean; diagnostics:string[]; }
export interface TransformationToolchainInstallationDescriptor { language:TransformationExportLanguage; present:boolean; hydraManaged:boolean; packageId:string; packageVersion:string; diagnostics:string[]; }
export interface TransformationToolchainCatalog { provisioningEnabled:boolean; packages:TransformationToolchainPackageDescriptor[]; installations:TransformationToolchainInstallationDescriptor[]; diagnostics:string[]; }
export interface TransformationToolchainProvisioningRequest { packageId:string; packageVersion:string; expectedCurrentPackageId:string; expectedCurrentPackageVersion:string; }
export interface TransformationToolchainRemovalRequest { expectedPackageId:string; expectedPackageVersion:string; }
export interface TransformationToolchainProvisioningReport { language:TransformationExportLanguage; status:TransformationToolchainProvisioningStatus; packageId:string; packageVersion:string; diagnostic:TransformationNativeRuntimeDiagnostic|null; diagnostics:string[]; }

export interface TransformationExpectedArtifact { path:string; content:string; }
export interface TransformationTestCase {
    schemaVersion:'1.0'|'1.1'; id:string; transformationId:string; name:string; enabled:boolean;
    sourceModel?:CanonicalModelDocument|null; sourceText:string; expectedModel?:CanonicalModelDocument|null;
    expectedArtifacts:TransformationExpectedArtifact[]; annotations:Record<string,string>; executionDirection:TransformationExecutionDirection;
}
export interface TransformationTestCaseSummary { id:string; transformationId:string; name:string; enabled:boolean; etag:string; }
export interface TransformationTestCaseLoadResult { testCase:TransformationTestCase; etag:string; }
export interface TransformationTestCaseSaveResponse { testCase:TransformationTestCase; etag:string; }
export type TransformationTestStatus = 'PASS'|'FAIL'|'ERROR';
export interface TransformationTestCaseResult { testCaseId:string; name:string; status:TransformationTestStatus; differences:string[]; }
export interface TransformationRuleCoverage { ruleId:string; name:string; hits:number; covered:boolean; }
export interface TransformationBindingCoverage { ruleId:string; bindingId:string; hits:number; covered:boolean; }
export interface TransformationCoverageReport { ruleTotal:number; ruleCovered:number; bindingTotal:number; bindingCovered:number; rules:TransformationRuleCoverage[]; bindings:TransformationBindingCoverage[]; }
export interface TransformationTestRunReport { transformationId:string; total:number; passed:number; failed:number; errors:number; cases:TransformationTestCaseResult[]; coverage:TransformationCoverageReport; }
export interface TransformationQuickFix { id:string; diagnosticCode:string; ruleId:string; bindingId:string; title:string; description:string; safeAutomatic:boolean; proposedTransformation:TransformationDefinitionDocument; }
export interface TransformationQuickFixReport { transformationId:string; fixes:TransformationQuickFix[]; }
