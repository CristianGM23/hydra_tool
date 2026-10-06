import type { VisualEdgeStyle, VisualNodeStyle } from '../visual-style/visualStyleTypes';

/** Extensiones neutrales conservadas sin acoplar el núcleo a una tecnología. */
export type MetaAnnotations = Record<string, string>;

export type MetaClassKind = 'CLASS' | 'ABSTRACT_CLASS' | 'INTERFACE';
export type MetaOperationBehaviorKind = 'UNSPECIFIED' | 'QUERY' | 'COMMAND';
export type MetaRelationKind = 'REFERENCE' | 'AGGREGATION' | 'CONTAINMENT' | 'DEPENDENCY' | 'INHERITANCE' | 'IMPLEMENTATION';
export type MetaTypeReferenceKind = 'PRIMITIVE' | 'ENUM' | 'DATATYPE' | 'CLASS' | 'EXTERNAL';
export type MetaPrimitiveType = 'STRING' | 'BOOLEAN' | 'BYTE' | 'SHORT' | 'INTEGER' | 'LONG' | 'FLOAT' | 'DOUBLE' | 'DECIMAL' | 'BIG_INTEGER' | 'CHARACTER' | 'DATE' | 'BINARY' | 'OBJECT';

export interface Multiplicity {
    lowerBound: number;
    upperBound: number | null;
}

export interface MetaTypeReference {
    kind: MetaTypeReferenceKind;
    targetId: string;
    externalName: string;
}

export interface MetaAttribute {
    id: string;
    name: string;
    type: MetaTypeReference;
    multiplicity: Multiplicity;
    ordered: boolean;
    unique: boolean;
    defaultValue: string;
    annotations: MetaAnnotations;
}


export interface MetaParameter {
    id: string;
    name: string;
    type: MetaTypeReference;
    multiplicity: Multiplicity;
    ordered: boolean;
    unique: boolean;
    annotations: MetaAnnotations;
}

export interface MetaOperation {
    id: string;
    name: string;
    returnType: MetaTypeReference | null;
    returnMultiplicity: Multiplicity | null;
    ordered: boolean;
    unique: boolean;
    parameters: MetaParameter[];
    exceptionTypes: MetaTypeReference[];
    behaviorKind: MetaOperationBehaviorKind;
    annotations: MetaAnnotations;
}

export interface MetaClass {
    id: string;
    name: string;
    kind: MetaClassKind;
    attributes: MetaAttribute[];
    operations: MetaOperation[];
    annotations: MetaAnnotations;
}

export interface MetaEnumLiteral {
    id: string;
    name: string;
    value: number;
    annotations: MetaAnnotations;
}

export interface MetaEnum {
    id: string;
    name: string;
    literals: MetaEnumLiteral[];
    annotations: MetaAnnotations;
}

export interface MetaDataType {
    id: string;
    name: string;
    annotations: MetaAnnotations;
}

export interface MetaRelation {
    id: string;
    name: string;
    sourceTypeId: string;
    targetTypeId: string;
    kind: MetaRelationKind;
    sourceMultiplicity: Multiplicity | null;
    targetMultiplicity: Multiplicity | null;
    oppositeRelationId: string;
    ordered: boolean;
    unique: boolean;
    annotations: MetaAnnotations;
}

/** Contrato semántico único compartido por React y Spring. */
export interface MetaModel {
    schemaVersion: '2.1';
    id: string;
    name: string;
    classes: MetaClass[];
    enums: MetaEnum[];
    dataTypes: MetaDataType[];
    relations: MetaRelation[];
    annotations: MetaAnnotations;
}

export interface ProjectionResult {
    targetId: string;
    status: 'NOT_REQUESTED' | 'UP_TO_DATE' | 'GENERATED_WITH_WARNINGS' | 'FAILED' | 'STALE';
    artifactPath: string;
    warnings: unknown[];
    errorMessage: string;
}


export type EcoreGenerationMode = 'ON_SAVE' | 'MANUAL' | 'DISABLED';

export interface EcoreAttributeConfiguration {
    changeable: boolean;
    transientFeature: boolean;
    volatileFeature: boolean;
    unsettable: boolean;
    derived: boolean;
    id: boolean;
}

export interface EcoreRelationConfiguration {
    changeable: boolean;
    resolveProxies: boolean;
    transientFeature: boolean;
    volatileFeature: boolean;
    unsettable: boolean;
    derived: boolean;
}

export interface EcoreDataTypeConfiguration {
    instanceClassName: string;
    serializable: boolean;
}

export interface EcoreProjectionConfiguration {
    enabled: boolean;
    generationMode: EcoreGenerationMode;
    requiredForRuntime: boolean;
    fileName: string;
    namespaceUri: string;
    namespacePrefix: string;
    attributeOptions: Record<string, EcoreAttributeConfiguration>;
    relationOptions: Record<string, EcoreRelationConfiguration>;
    dataTypeOptions: Record<string, EcoreDataTypeConfiguration>;
}

export interface TargetConfigurationDocument {
    schemaVersion: '1.0';
    metamodelId: string;
    ecore: EcoreProjectionConfiguration;
    additionalTargets: Record<string, Record<string, string>>;
}

export interface ValidationIssue {
    severity: 'ERROR' | 'WARNING' | 'INFO';
    code: string;
    path: string;
    message: string;
}

export interface ValidationReport {
    issues: ValidationIssue[];
}

export interface MetamodelSaveResponse {
    metamodel: MetaModel;
    validation: ValidationReport;
    projections: ProjectionResult[];
    etag: string;
}

export interface CanonicalMetamodelSummary {
    id: string;
    name: string;
    schemaVersion: string;
    etag?: string;
}

/** Describe la posición, el tamaño y el estilo de un elemento del metamodelo. */
export interface MetamodelDiagramNode {
    elementId: string;
    x: number;
    y: number;
    width: number;
    height: number;
    style?: Partial<VisualNodeStyle>;
}

export type MetamodelConnectionKind = MetaRelationKind;

/** Describe los puntos, metadatos y estilo visual de una relación. */
export interface MetamodelDiagramEdge {
    relationId: string;
    sourceHandle: string;
    targetHandle: string;
    kind?: MetamodelConnectionKind;
    sourceClassId?: string;
    targetClassId?: string;
    label?: string;
    style?: Partial<VisualEdgeStyle>;
    routePoints?: MetamodelRoutePoint[];
    labelPosition?: MetamodelEdgeLabelPosition;
}

export interface MetamodelRoutePoint {
    x: number;
    y: number;
}

export interface MetamodelEdgeLabelPosition {
    x: number;
    y: number;
    segmentIndex: number;
}

/** Describe la disposición visual del metamodelo. */
export interface MetamodelDiagramDocument {
    metamodelId: string;
    nodes: MetamodelDiagramNode[];
    edges: MetamodelDiagramEdge[];
}

export type MetamodelHandleRole = 'SOURCE' | 'TARGET';

/** La selección semántica de relaciones usa un único discriminador. */
export type MetamodelSelection =
    | { kind: 'MODEL'; id: string }
    | { kind: 'CLASS'; id: string }
    | { kind: 'ENUM'; id: string }
    | { kind: 'DATA_TYPE'; id: string }
    | { kind: 'ATTRIBUTE'; id: string; ownerClassId: string }
    | { kind: 'OPERATION'; id: string; ownerClassId: string }
    | { kind: 'PARAMETER'; id: string; ownerClassId: string; operationId: string }
    | { kind: 'RELATION'; id: string };

export interface MetamodelExportFormatDescriptor {
    id: string;
    displayName: string;
    extension: string;
    category: 'SEMANTIC' | 'GRAMMAR' | 'DOCUMENTATION' | 'DIAGRAM' | 'CODE' | 'BUNDLE';
}

export interface MetamodelDeletionImpact {
    metamodelId: string;
    canonicalFile?: string;
    canonicalExists?: boolean;
    targetConfigurationFile?: string;
    targetConfigurationExists?: boolean;
    projectionManifestFile?: string;
    projectionManifestExists?: boolean;
    ecoreFile: string;
    ecoreExists: boolean;
    diagramFile: string;
    diagramExists: boolean;
    expressionFile?: string;
    expressionExists?: boolean;
    validationFile?: string;
    validationExists?: boolean;
    actionFile?: string;
    actionExists?: boolean;
    eventDefinitionFile?: string;
    eventDefinitionExists?: boolean;
    eventBindingFile?: string;
    eventBindingExists?: boolean;
    editorDefinitionFiles?: string[];
    integrationProfileFiles?: string[];
    notationFiles: string[];
    modelFiles?: string[];
    exportFiles: string[];
    dependentArtifactFiles?: string[];
    warning: string;
}

export interface MetamodelDeletionResult {
    metamodelId: string;
    deletedFiles: string[];
    pendingFiles?: string[];
}

export interface MetamodelValidationSummary { errors: number; warnings: number; total: number; }
export interface MetamodelValidationDiagnostic {
    severity: 'ERROR' | 'WARNING' | 'INFO'; code: string; category: string; title: string; whatHappened: string;
    location: string; situation: string; involvedElements: string[]; howToFix: string[]; exportImpact: string; path: string;
}
export interface MetamodelValidationAnalysis {
    metamodelId: string; metamodelName: string; fingerprint: string; validatedAt: string; valid: boolean; generationReady: boolean;
    summary: MetamodelValidationSummary; diagnostics: MetamodelValidationDiagnostic[];
}
export interface MetamodelValidationGateStatus {
    metamodelId: string; validationRequired: boolean; validated: boolean; current: boolean; stale: boolean; valid: boolean;
    generationReady: boolean; currentFingerprint: string; validatedFingerprint: string; validatedAt: string;
    summary: MetamodelValidationSummary; message: string;
}
export interface MetamodelProjectExportDescriptor {
    id: string; displayName: string; description: string; technology: string; requiresEditorDefinition: boolean;
    validationRequired: boolean; extension: string; defaultSettings: Record<string, string>;
}
export interface MetamodelProjectArtifactDescriptor { kind: string; documentId: string; displayName: string; etag: string; editorDefinition: boolean; }
export interface MetamodelProjectArtifactCatalog { metamodelId: string; artifacts: MetamodelProjectArtifactDescriptor[]; }
export interface MetamodelProjectArtifactRef { kind: string; documentId: string; }
export interface MetamodelProjectExportOptions {
    includeExistingLayers: boolean; selectionMode: 'ALL_COMPATIBLE' | 'EXPLICIT'; selectedArtifacts: MetamodelProjectArtifactRef[];
    editorId: string; settings: Record<string, string>;
}
