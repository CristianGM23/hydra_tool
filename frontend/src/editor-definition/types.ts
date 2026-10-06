import type { CanonicalModelDocument, ModelValidationReport, ValidationRunMode } from '../validation/types';
import type { QuickFixDescriptor, QuickFixExecutionRequest, QuickFixPreviewResult } from '../problems/types';
import type { CodeGenerationDefinition } from '../model-codegen/types';
export type RepresentationKind = 'DIAGRAM' | 'TREE' | 'TABLE' | 'FORM' | 'MATRIX' | 'DASHBOARD';
export type EditorValidationTrigger = 'ON_OPEN' | 'ON_CHANGE' | 'ON_REPRESENTATION_CHANGE' | 'MANUAL';
export interface EditorRuntimePolicy { validationTriggers:EditorValidationTrigger[]; autosave:boolean; recoverLatestSession:boolean; publishEvents:boolean; eventFlowsEnabled:boolean; gamificationEnabled:boolean; maximumOpenRepresentations:number; operationTimeoutMillis:number; }
export type EditorCapability = 'MODEL_EDITING' | 'MULTIPLE_REPRESENTATIONS' | 'OPERATIONS' | 'VALIDATION_RUNTIME' | 'QUICK_FIX_RUNTIME' | 'EVENTS' | 'EVENT_FLOWS' | 'GAMIFICATION' | 'IMPORT' | 'EXPORT' | 'CODE_GENERATION' | 'UNDO_REDO';
export type EditorImageFit = 'CONTAIN' | 'COVER' | 'FILL' | 'NONE';
export type EditorNodeLabelPosition = 'TOP' | 'CENTER' | 'BOTTOM' | 'OUTSIDE_TOP' | 'OUTSIDE_BOTTOM';
export type EditorBorderStyle = 'SOLID' | 'DASHED' | 'DOTTED' | 'DOUBLE' | 'NONE';

export interface ConditionalStyleRule { id:string; name:string; enabled:boolean; priority:number; conditionExpressionId:string; styles:Record<string,string>; }
export interface RepresentationLayerDefinition { id:string; name:string; defaultLayer:boolean; activeByDefault:boolean; userToggleable:boolean; nodeMappingIds:string[]; edgeMappingIds:string[]; toolIds:string[]; annotations:Record<string,string>; }
export interface RepresentationFilterDefinition { id:string; name:string; activeByDefault:boolean; mappingIds:string[]; conditionExpressionId:string; externalCondition:string; annotations:Record<string,string>; }

export interface NodeMapping { id: string; targetClassId: string; representationName: string; shape: 'RECTANGLE' | 'ROUNDED_RECTANGLE' | 'ELLIPSE' | 'DIAMOND' | 'TRIANGLE' | 'PENTAGON' | 'HEXAGON' | 'OCTAGON' | 'PARALLELOGRAM' | 'TRAPEZOID' | 'CYLINDER' | 'DOCUMENT' | 'CLOUD' | 'STAR' | 'CROSS' | 'SVG_PATH' | 'PROJECT_IMAGE'; defaultWidth: number; defaultHeight: number; minimumWidth: number; minimumHeight: number; labelExpressionId: string; imageAssetPath?:string; imageFit?:EditorImageFit; container?:boolean; containmentRelationIds?:string[]; containerPadding?:number; styles: Record<string,string>; conditionalStyles?:ConditionalStyleRule[]; annotations: Record<string,string>; }
export interface SemanticEdgeDescriptor { edgeClassId:string; sourceRelationId:string; targetRelationId:string; containmentRelationId:string; defaultValues:Record<string,unknown>; annotations:Record<string,string>; }
export interface EdgeMapping { id: string; relationId: string; semanticEdge?:SemanticEdgeDescriptor; sourceClassId: string; targetClassId: string; lineType: 'BEZIER' | 'STRAIGHT' | 'STEP' | 'SMOOTH_STEP' | 'ORTHOGONAL'; labelExpressionId: string; styles: Record<string,string>; conditionalStyles?:ConditionalStyleRule[]; annotations: Record<string,string>; }
export interface RepresentationDefinition { id: string; name: string; kind: RepresentationKind; rootClassIds: string[]; nodeMappings: NodeMapping[]; edgeMappings: EdgeMapping[]; toolIds: string[]; layers?:RepresentationLayerDefinition[]; filters?:RepresentationFilterDefinition[]; editable: boolean; annotations: Record<string,string>; }
interface EditorToolBase { id:string; label:string; description:string; icon:string; representationIds:string[]; visibleExpressionId:string; enabledExpressionId:string; }
export interface NodeCreationTool extends EditorToolBase { kind:'CREATE_NODE'; targetClassId:string; defaultValues:Record<string,unknown>; formId:string; }
export interface EdgeCreationTool extends EditorToolBase { kind:'CREATE_EDGE'; edgeMappingId:string; relationId:string; semanticEdge?:SemanticEdgeDescriptor; sourceClassIds:string[]; targetClassIds:string[]; reconnectable:boolean; }
export interface OperationEditorTool extends EditorToolBase { kind:'INVOKE_OPERATION'; operationId:string; }
export interface ActionEditorTool extends EditorToolBase { kind:'RUN_ACTION'; actionId:string; }
export interface ValidateEditorTool extends EditorToolBase { kind:'VALIDATE'; validationMode:string; }
export type EditorTool = NodeCreationTool | EdgeCreationTool | OperationEditorTool | ActionEditorTool | ValidateEditorTool;
export interface PaletteGroup { id:string; name:string; order:number; toolIds:string[]; initiallyCollapsed:boolean; }
export interface ToolPaletteDefinition { groups:PaletteGroup[]; tools:EditorTool[]; }
export type FormFieldWidget = 'TEXT'|'TEXTAREA'|'NUMBER'|'BOOLEAN'|'ENUM'|'DATE'|'COLOR'|'REFERENCE'|'MULTI_REFERENCE'|'FILE'|'CODE'|'EXPRESSION'|'READ_ONLY'|'CUSTOM';
export interface FormFieldDefinition { id:string; featureId:string; label:string; widget:FormFieldWidget; required:boolean; readOnly:boolean; visibleExpressionId:string; enabledExpressionId:string; defaultValue?:unknown; helpText:string; }
export interface FormSectionDefinition { id:string; title:string; order:number; columns:number; fields:FormFieldDefinition[]; }
export interface FormDefinition { id:string; name:string; targetClassId:string; sections:FormSectionDefinition[]; }
export interface EditorDefinitionModel { schemaVersion:'1.0'; id:string; metamodelId:string; name:string; representations:RepresentationDefinition[]; palette:ToolPaletteDefinition; forms:FormDefinition[]; capabilityManifest:{capabilities:EditorCapability[]}; runtimePolicy:EditorRuntimePolicy; codeGeneration:CodeGenerationDefinition; annotations:Record<string,string>; }
export type EditorDefinitionSelection =
    | { kind:'REPRESENTATION'; id:string }
    | { kind:'NODE_MAPPING'; id:string }
    | { kind:'EDGE_MAPPING'; id:string }
    | { kind:'PALETTE_GROUP'; id:string }
    | { kind:'TOOL'; id:string }
    | { kind:'FORM'; id:string }
    | null;
export interface EditorDefinitionDiagnostic { severity:'ERROR'|'WARNING'|'INFO'; code:string; elementId:string; path:string; message:string; }
export interface EditorDefinitionCheckReport { editorDefinitionId:string; diagnostics:EditorDefinitionDiagnostic[]; valid?:boolean; }
export interface EditorDefinitionSummary { id:string; metamodelId:string; name:string; representationCount:number; toolCount:number; formCount:number; etag:string; }
export interface EditorDefinitionSaveResponse { editorDefinition:EditorDefinitionModel; etag:string; checkReport:EditorDefinitionCheckReport; }

export interface ExecutablePaletteGroup { id:string; name:string; order:number; initiallyCollapsed:boolean; tools:EditorTool[]; }
export interface ExecutableRepresentationPlan {
    id:string;
    name:string;
    kind:RepresentationKind;
    rootClassIds:string[];
    editable:boolean;
    nodeMappingsByClassId:Record<string,NodeMapping[]>;
    edgeMappingsByRelationId:Record<string,EdgeMapping[]>;
    edgeMappingsBySemanticClassId?:Record<string,EdgeMapping[]>;
    toolsById:Record<string,EditorTool>;
    paletteGroups:ExecutablePaletteGroup[];
    layers?:RepresentationLayerDefinition[];
    filters?:RepresentationFilterDefinition[];
    annotations:Record<string,string>;
}
export interface ExecutableEditorPlan {
    schemaVersion:'1.0';
    id:string;
    sourceEditorDefinitionId:string;
    sourceEtag:string;
    metamodelId:string;
    name:string;
    compiledAt:string;
    representationsById:Record<string,ExecutableRepresentationPlan>;
    formsById:Record<string,FormDefinition>;
    formsByTargetClassId:Record<string,FormDefinition>;
    capabilityManifest:{capabilities:EditorCapability[]};
    runtimePolicy:EditorRuntimePolicy;
    codeGeneration?:CodeGenerationDefinition;
    annotations:Record<string,string>;
}
export interface EditorDefinitionCompilationResult { plan:ExecutableEditorPlan; checkReport:EditorDefinitionCheckReport; cacheHit:boolean; }


export interface EditorModelSession {
    schemaVersion:'1.0'; id:string; metamodelId:string; editorId:string; sourceEditorEtag:string;
    activeRepresentationId:string; openRepresentationIds:string[]; model:CanonicalModelDocument; latestValidationReport:ModelValidationReport|null; revision:number; createdAt:string; updatedAt:string;
    annotations:Record<string,string>;
}
export interface EditorNodeAppearanceView { mappingId:string; name:string; shape:string; imageAssetPath?:string; }
export interface EditorRenderedNode {
    elementId:string; classId:string; className:string; mappingId:string; representationName:string;
    shape:string; label:string; width:number; height:number; minimumWidth?:number; minimumHeight?:number; attributes:Record<string,unknown>; styles:Record<string,string>;
    imageAssetPath?:string; imageFit?:EditorImageFit; container?:boolean; containmentRelationIds?:string[]; containerPadding?:number;
    availableAppearances?:EditorNodeAppearanceView[];
}
export interface EditorRenderedEdge {
    relationInstanceId:string; relationId:string; relationName:string; mappingId:string; sourceElementId:string;
    targetElementId:string; lineType:string; label:string; styles:Record<string,string>;
}
export interface EditorRepresentationRuntimeView { id:string; name:string; kind:RepresentationKind; editable:boolean; active:boolean; open:boolean; }
export interface EditorRuntimeToolState { toolId:string; visible:boolean; enabled:boolean; reason:string; }
export interface EditorSessionView {
    session:EditorModelSession; etag:string; representationId:string; representationName:string; representationKind:RepresentationKind; editable:boolean;
    nodes:EditorRenderedNode[]; edges:EditorRenderedEdge[]; paletteGroups:ExecutablePaletteGroup[]; toolStates?:Record<string,EditorRuntimeToolState>;
    selectedForm:FormDefinition|null; representations:EditorRepresentationRuntimeView[]; validationReport:ModelValidationReport|null; undoAvailable:boolean; redoAvailable:boolean;
}
export interface EditorSessionCreateRequest { sessionId:string; modelId:string; modelName:string; representationId:string; rootClassId:string; rootAttributes:Record<string,unknown>; }

export interface EditorSessionToolExecutionRequest {
    representationId:string;
    targetElementId:string;
    selectionElementIds:string[];
    arguments:Record<string,unknown>;
}
export interface EditorSessionToolExecutionResponse {
    view:EditorSessionView;
    returnValue?:unknown;
    messages:string[];
    validationReport?:import('../validation/types').ModelValidationReport|null;
}
export interface EditorSessionDiagramNodeView { elementId:string; x:number; y:number; width:number; height:number; rotationDegrees?:number; }
export interface EditorSessionDiagramPoint { x:number; y:number; }
export interface EditorSessionDiagramEdgeView { relationInstanceId:string; points:EditorSessionDiagramPoint[]; }
export interface EditorSessionDiagramDocument {
    schemaVersion:'1.0'; id:string; metamodelId:string; editorId:string; representationId:string;
    nodes:EditorSessionDiagramNodeView[]; edges:EditorSessionDiagramEdgeView[];
}
export interface EditorSessionDiagramLoadResult { diagram:EditorSessionDiagramDocument; etag:string; }

export interface EditorSessionQuickFixResult { view:EditorSessionView; diagnosticId:string; actionId:string; resolved:boolean; messages:string[]; }
export type { QuickFixDescriptor, QuickFixExecutionRequest, QuickFixPreviewResult, ValidationRunMode };

export interface VisualDefinitionImportDiagnostic {
    severity:string; code:string; source:string; location:string; element:string; message:string;
    explanation:string; suggestedAction:string; fixIds:string[]; confidence:string;
}
export interface VisualDefinitionImportFix {
    id:string; code:string; source:string; target:string; title:string; description:string;
    safety:'SAFE'|'REVIEW'|'MANUAL'|string; confidence:string; automatic:boolean; applied:boolean;
}
export interface VisualDefinitionImportAnalysis {
    filesInspected:number; elementsInspected:number; referencesChecked:number; unresolvedReferences:number;
    ambiguousReferences:number; invalidParameters:number; structuralProblems:number; warnings:number;
    safeCorrections:number; reviewCorrections:number; manualActions:number; importReady:boolean;
}
export type VisualMetamodelAssociationMode='EXISTING'|'EXTERNAL'|'EMBEDDED';
export interface VisualMetamodelAssociation { mode:VisualMetamodelAssociationMode|string; sourceName:string; alreadyPersisted:boolean; detectedFormat:string; explanation:string; }
export interface VisualImportedValidation { source:string; name:string; representationId:string; expression:string; message:string; severity:string; originalFragment:string; }
export interface VisualImportCommitResult { editorDefinition:EditorDefinitionModel; metamodelId:string; validationsImported:number; validationRuleIds:string[]; }
export interface VisualDefinitionImportResult {
    technology:'SIRIUS_DESKTOP'|'SIRIUS_WEB'|'GMF'|'DRAWIO'|string;
    editorDefinition:EditorDefinitionModel;
    analysis:VisualDefinitionImportAnalysis;
    diagnostics:VisualDefinitionImportDiagnostic[];
    fixes:VisualDefinitionImportFix[];
    recovered:Record<string,number>;
    javaServices:string[];
    validations:string[];
    validationDetails:VisualImportedValidation[];
    expressions:string[];
    preservedArtifacts:string[];
    associatedMetamodel:import('../metamodel-designer/types').MetaModel;
    metamodelAssociation:VisualMetamodelAssociation;
}
