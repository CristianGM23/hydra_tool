export type ExpressionValueKind =
    | 'BOOLEAN' | 'INTEGER' | 'DECIMAL' | 'STRING' | 'DATE' | 'DATETIME'
    | 'BINARY' | 'ENUM' | 'OBJECT' | 'COLLECTION' | 'MODEL' | 'ANY' | 'NULL';

export interface ExpressionValueType {
    kind: ExpressionValueKind;
    classId?: string;
    enumId?: string;
    elementType?: ExpressionValueType;
}

export type UnaryExpressionOperator =
    | 'NOT' | 'IS_NULL' | 'IS_NOT_NULL' | 'IS_EMPTY' | 'IS_NOT_EMPTY'
    | 'NEGATE' | 'SIZE' | 'TO_UPPER' | 'TO_LOWER';

export type BinaryExpressionOperator =
    | 'AND' | 'OR' | 'EQUALS' | 'NOT_EQUALS' | 'GREATER_THAN' | 'GREATER_OR_EQUAL'
    | 'LESS_THAN' | 'LESS_OR_EQUAL' | 'ADD' | 'SUBTRACT' | 'MULTIPLY' | 'DIVIDE'
    | 'CONTAINS' | 'STARTS_WITH' | 'ENDS_WITH' | 'MATCHES';

export type CollectionExpressionOperator =
    | 'EXISTS' | 'FOR_ALL' | 'NONE' | 'IS_UNIQUE' | 'SELECT' | 'COUNT' | 'SUM' | 'MIN' | 'MAX';

interface ExpressionNodeBase { nodeId: string; }
export interface LiteralExpressionNode extends ExpressionNodeBase { kind: 'LITERAL'; valueType: ExpressionValueType; value: unknown; }
export interface SelfExpressionNode extends ExpressionNodeBase { kind: 'SELF'; }
export interface ModelExpressionNode extends ExpressionNodeBase { kind: 'MODEL'; }
export interface ClassInstancesExpressionNode extends ExpressionNodeBase { kind: 'CLASS_INSTANCES'; classId: string; }
export interface AttributeAccessExpressionNode extends ExpressionNodeBase { kind: 'ATTRIBUTE_ACCESS'; target: ExpressionNode; attributeId: string; }
export interface RelationNavigationExpressionNode extends ExpressionNodeBase { kind: 'RELATION_NAVIGATION'; target: ExpressionNode; relationId: string; }
export interface UnaryExpressionNode extends ExpressionNodeBase { kind: 'UNARY'; operator: UnaryExpressionOperator; operand: ExpressionNode; }
export interface BinaryExpressionNode extends ExpressionNodeBase { kind: 'BINARY'; operator: BinaryExpressionOperator; left: ExpressionNode; right: ExpressionNode; }
export interface CollectionExpressionNode extends ExpressionNodeBase { kind: 'COLLECTION'; operator: CollectionExpressionOperator; source: ExpressionNode; expression?: ExpressionNode; }
export interface ConditionalExpressionNode extends ExpressionNodeBase { kind: 'CONDITIONAL'; condition: ExpressionNode; whenTrue: ExpressionNode; whenFalse: ExpressionNode; }
export interface FunctionExpressionNode extends ExpressionNodeBase { kind: 'FUNCTION'; functionName: string; arguments: ExpressionNode[]; }
export interface VariableExpressionNode extends ExpressionNodeBase { kind: 'VARIABLE'; variableName: string; }
export interface OperationCallExpressionNode extends ExpressionNodeBase { kind: 'OPERATION_CALL'; target: ExpressionNode; operationId: string; arguments: ExpressionNode[]; }

export type ExpressionNode =
    | LiteralExpressionNode | SelfExpressionNode | ModelExpressionNode | ClassInstancesExpressionNode
    | AttributeAccessExpressionNode | RelationNavigationExpressionNode | UnaryExpressionNode
    | BinaryExpressionNode | CollectionExpressionNode | ConditionalExpressionNode | FunctionExpressionNode
    | VariableExpressionNode | OperationCallExpressionNode;

export interface ExpressionDefinition {
    id: string;
    name: string;
    description?: string;
    contextClassId?: string;
    declaredReturnType?: ExpressionValueType;
    root: ExpressionNode;
    annotations?: Record<string, string>;
}

export interface ExpressionModel {
    schemaVersion: '1.0';
    id: string;
    metamodelId: string;
    expressions: ExpressionDefinition[];
    annotations: Record<string, string>;
}

export type ExpressionDiagnosticSeverity = 'ERROR' | 'WARNING' | 'INFO';
export interface ExpressionDiagnostic {
    severity: ExpressionDiagnosticSeverity;
    code: string;
    expressionId: string;
    nodeId: string;
    path: string;
    message: string;
}
export type ExpressionDependencyKind = 'CLASS' | 'ATTRIBUTE' | 'RELATION' | 'OPERATION' | 'MODEL';
export interface ExpressionDependency { kind: ExpressionDependencyKind; elementId: string; }
export interface ExpressionCheckResult {
    expressionId: string;
    inferredType: ExpressionValueType;
    diagnostics: ExpressionDiagnostic[];
    dependencies: ExpressionDependency[];
}
export interface ExpressionModelCheckReport {
    expressionModelId: string;
    diagnostics: ExpressionDiagnostic[];
    expressions: ExpressionCheckResult[];
}
export interface ExpressionModelSaveResponse {
    expressionModel: ExpressionModel;
    checkReport: ExpressionModelCheckReport;
    etag: string;
}

export interface CanonicalModelElement {
    id: string;
    classId: string;
    attributes: Record<string, unknown>;
    annotations: Record<string, string>;
}
export interface CanonicalModelRelation {
    id: string;
    relationId: string;
    sourceElementId: string;
    targetElementId: string;
    annotations: Record<string, string>;
}
export interface CanonicalModelDocument {
    schemaVersion: '1.0';
    id: string;
    metamodelId: string;
    metamodelEtag?: string;
    name?: string;
    rootElementIds: string[];
    elements: CanonicalModelElement[];
    relations: CanonicalModelRelation[];
    annotations: Record<string, string>;
}
export interface ExpressionEvaluationResult { expressionId: string; valueType: ExpressionValueType; value: unknown; }

export type ActionSelectorKind = 'SELF' | 'SELECTION' | 'PARAMETER' | 'CREATED_ELEMENT' | 'ELEMENT_ID';
export interface ActionSelector { kind: ActionSelectorKind; parameterName?: string; elementId?: string; }
export interface ActionValueSource { literal?: unknown; expressionId?: string; parameterName?: string; }
export type ActionParameterType = 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'ELEMENT_ID' | 'CLASS_ID' | 'RELATION_ID' | 'ATTRIBUTE_ID';
export interface ActionParameter { name: string; type: ActionParameterType; required: boolean; }
export type TextTransformation = 'UPPERCASE' | 'LOWERCASE' | 'CAPITALIZE' | 'TRIM';

export interface SetAttributeOperation { kind: 'SET_ATTRIBUTE'; operationId: string; target: ActionSelector; attributeId: string; value: ActionValueSource; }
export interface ClearAttributeOperation { kind: 'CLEAR_ATTRIBUTE'; operationId: string; target: ActionSelector; attributeId: string; }
export interface CopyAttributeOperation { kind: 'COPY_ATTRIBUTE'; operationId: string; source: ActionSelector; sourceAttributeId: string; target: ActionSelector; targetAttributeId: string; }
export interface AddRelationOperation { kind: 'ADD_RELATION'; operationId: string; relationId: string; source: ActionSelector; target: ActionSelector; }
export interface RemoveRelationOperation { kind: 'REMOVE_RELATION'; operationId: string; relationId: string; source: ActionSelector; target: ActionSelector; }
export interface CreateElementOperation { kind: 'CREATE_ELEMENT'; operationId: string; classId: string; resultName: string; containmentRelationId?: string; container?: ActionSelector; }
export interface DeleteElementOperation { kind: 'DELETE_ELEMENT'; operationId: string; target: ActionSelector; }
export interface TransformTextOperation { kind: 'TRANSFORM_TEXT'; operationId: string; target: ActionSelector; attributeId: string; transformation: TextTransformation; }
export interface ExecuteActionOperation { kind: 'EXECUTE_ACTION'; operationId: string; actionId: string; arguments: Record<string, ActionValueSource>; }
export interface InvokeOperationOperation { kind: 'INVOKE_OPERATION'; operationId: string; target: ActionSelector; metamodelOperationId: string; arguments: ActionValueSource[]; }
export type ActionOperation = SetAttributeOperation | ClearAttributeOperation | CopyAttributeOperation | AddRelationOperation | RemoveRelationOperation | CreateElementOperation | DeleteElementOperation | TransformTextOperation | ExecuteActionOperation | InvokeOperationOperation;
export type ActionTransactionPolicy = 'ATOMIC' | 'BEST_EFFORT';
export type ActionUndoPolicy = 'REQUIRED' | 'OPTIONAL' | 'NOT_SUPPORTED';
export interface ActionDefinition {
    id: string;
    name: string;
    description?: string;
    enabled: boolean;
    parameters: ActionParameter[];
    preconditionExpressionId?: string;
    operations: ActionOperation[];
    transactionPolicy: ActionTransactionPolicy;
    undoPolicy: ActionUndoPolicy;
    annotations?: Record<string, string>;
}
export interface ActionModel { schemaVersion: '1.0'; id: string; metamodelId: string; expressionModelId: string; actions: ActionDefinition[]; annotations: Record<string, string>; }
export type ActionDiagnosticSeverity = 'ERROR' | 'WARNING' | 'INFO';
export interface ActionDiagnostic { severity: ActionDiagnosticSeverity; code: string; actionId: string; operationId: string; path: string; message: string; }
export interface ActionCheckResult { actionId: string; diagnostics: ActionDiagnostic[]; }
export interface ActionModelCheckReport { actionModelId: string; diagnostics: ActionDiagnostic[]; actions: ActionCheckResult[]; }
export interface ActionModelSaveResponse { actionModel: ActionModel; checkReport: ActionModelCheckReport; etag: string; }

export type OperationImplementationKind = 'UNIMPLEMENTED' | 'EXPRESSION' | 'ACTION' | 'EXTERNAL';
export interface OperationBinding { operationId: string; implementationKind: OperationImplementationKind; expressionId?: string; actionId?: string; externalReference?: string; annotations?: Record<string, string>; }
export interface OperationBehaviorModel { schemaVersion: '1.0'; metamodelId: string; bindings: OperationBinding[]; annotations: Record<string, string>; }
export interface OperationBehaviorSaveResponse { behaviorModel: OperationBehaviorModel; validation: { issues: Array<{ severity: 'ERROR' | 'WARNING' | 'INFO'; code: string; path: string; message: string }> }; etag: string; }

export interface OperationRuntimeDescriptor {
    operationId: string;
    ownerClassId: string;
    ownerClassName: string;
    applicableClassIds: string[];
    applicableClassNames: string[];
    name: string;
    behaviorKind: 'UNSPECIFIED' | 'QUERY' | 'COMMAND';
    parameters: Array<{ id: string; name: string; typeLabel: string; required: boolean; many: boolean }>;
    returnTypeLabel: string;
    implementationKind: OperationImplementationKind;
    enabled: boolean;
    surfaces: Array<'BUTTON' | 'FORM' | 'PALETTE' | 'MENU'>;
}
export interface OperationRuntimeCatalog { metamodelId: string; operations: OperationRuntimeDescriptor[]; }
export interface OperationInvocationRequest { targetElementId: string; arguments: Record<string, unknown>; selectionElementIds?: string[]; }
export interface OperationInvocationResult { operationId: string; value?: unknown; document: import('../types/editor').ModelDocument; undoAvailable: boolean; messages: string[]; }


export type ValidationContextKind = 'CLASS' | 'ATTRIBUTE' | 'RELATION' | 'OBJECT' | 'MODEL' | 'REPRESENTATION' | 'SELECTION' | 'PROJECT';
export interface ValidationContext { kind: ValidationContextKind; classId?: string; featureId?: string; }
export type ValidationSeverity = 'ERROR' | 'WARNING' | 'INFO';
export type ValidationTrigger = 'ON_CREATE' | 'ON_ATTRIBUTE_CHANGE' | 'ON_RELATION_CHANGE' | 'ON_DELETE' | 'ON_OPEN' | 'ON_SAVE' | 'MANUAL' | 'BEFORE_EXPORT' | 'BACKGROUND' | 'FULL_VALIDATION';
export type ValidationDependencyKind = 'CLASS' | 'ATTRIBUTE' | 'RELATION' | 'OPERATION' | 'MODEL';
export interface ValidationDependency { kind: ValidationDependencyKind; elementId: string; }
export type ValidationExecutionMode = 'INCREMENTAL' | 'FULL_ONLY' | 'MANUAL_ONLY' | 'EXTERNAL';
export type AuthoritativeRepresentation = 'CANONICAL' | 'OCL' | 'EVL' | 'ATL' | 'SIRIUS_AQL' | 'JAVA' | 'JAVASCRIPT' | 'BLOCKLY' | 'XTEXT' | 'EXTERNAL';
export type ConversionStatus = 'FULL' | 'PARTIAL' | 'EXTERNAL' | 'UNSUPPORTED';
export interface ValidationProvenance { technology?: string; sourceFile?: string; sourceRule?: string; sourceHash?: string; importedAt?: string; originalFragment?: string; }
export interface ValidationRule {
    id: string;
    name: string;
    code: string;
    description?: string;
    enabled: boolean;
    context: ValidationContext;
    severity: ValidationSeverity;
    conditionExpressionId: string;
    messageTemplate: string;
    triggers: ValidationTrigger[];
    dependencies: ValidationDependency[];
    quickFixIds: string[];
    executionMode: ValidationExecutionMode;
    authoritativeRepresentation: AuthoritativeRepresentation;
    conversionStatus: ConversionStatus;
    provenance?: ValidationProvenance;
    annotations?: Record<string, string>;
}
export interface ValidationRuleSet { id: string; name: string; description?: string; enabled: boolean; order: number; ruleIds: string[]; annotations?: Record<string, string>; }
export interface ImportedValidationSource { id: string; technology: string; fileName: string; contentHash: string; conversionStatus: ConversionStatus; importedAt?: string; annotations?: Record<string, string>; }
export interface ValidationModel {
    schemaVersion: '1.0';
    id: string;
    metamodelId: string;
    expressionModelId: string;
    actionModelId?: string;
    ruleSets: ValidationRuleSet[];
    rules: ValidationRule[];
    importedSources: ImportedValidationSource[];
    annotations: Record<string, string>;
}
export type ValidationModelDiagnosticSeverity = 'ERROR' | 'WARNING' | 'INFO';
export interface ValidationModelDiagnostic { severity: ValidationModelDiagnosticSeverity; code: string; ruleId: string; path: string; message: string; }
export interface ValidationRuleCheckResult { ruleId: string; diagnostics: ValidationModelDiagnostic[]; inferredDependencies: ValidationDependency[]; }
export interface ValidationModelCheckReport { validationModelId: string; diagnostics: ValidationModelDiagnostic[]; rules: ValidationRuleCheckResult[]; }
export interface ValidationModelSaveResponse { validationModel: ValidationModel; checkReport: ValidationModelCheckReport; etag: string; }

export type ValidationRunMode = 'FULL' | 'INCREMENTAL' | 'SELECTION' | 'CLASS' | 'MANUAL' | 'ON_OPEN' | 'ON_SAVE' | 'BEFORE_EXPORT';
export type ValidationCompilationSeverity = 'ERROR' | 'WARNING' | 'INFO';
export interface ValidationCompilationDiagnostic { severity: ValidationCompilationSeverity; code: string; ruleId: string; message: string; }
export interface ValidationPlanSourceVersions { metamodelEtag: string; expressionModelEtag: string; actionModelEtag: string; validationModelEtag: string; operationBehaviorEtag: string; }
export interface CompiledValidationRule {
    ruleId: string;
    contextKind: ValidationContextKind;
    severity: ValidationSeverity;
    triggerSet: ValidationTrigger[];
    dependencyIds: string[];
    executable: boolean;
    compilationErrors: string[];
    name?: string;
    code?: string;
    order?: number;
    context?: ValidationContext;
    conditionExpressionId?: string;
    messageTemplate?: string;
    dependencies?: ValidationDependency[];
    quickFixIds?: string[];
    executionMode?: ValidationExecutionMode;
    ruleSetIds?: string[];
}
export interface ValidationDependencyIndex {
    classRules: Record<string, string[]>;
    attributeRules: Record<string, string[]>;
    relationRules: Record<string, string[]>;
    operationRules: Record<string, string[]>;
    triggerRules: Record<string, string[]>;
    modelRules: string[];
}
export interface ExecutableValidationPlan {
    schemaVersion: '1.0';
    metamodelId: string;
    metamodelEtag: string;
    validationModelId: string;
    validationModelEtag: string;
    compiledRules: CompiledValidationRule[];
    id?: string;
    expressionModelId?: string;
    actionModelId?: string;
    sourceFingerprint?: string;
    sourceVersions?: ValidationPlanSourceVersions;
    dependencyIndex?: ValidationDependencyIndex;
    diagnostics?: ValidationCompilationDiagnostic[];
    annotations?: Record<string, string>;
}
export interface ValidationCompilationResult { plan: ExecutableValidationPlan; diagnostics: ValidationCompilationDiagnostic[]; cacheHit: boolean; }
export interface ValidationChangeSet {
    elementIds: string[];
    classIds: string[];
    attributeIds: string[];
    relationIds: string[];
    operationIds: string[];
    modelChanged: boolean;
}
export interface ValidationExecutionRequest {
    mode: ValidationRunMode;
    model: CanonicalModelDocument;
    selectionElementIds: string[];
    classId?: string;
    changeSet: ValidationChangeSet;
    variables: Record<string, unknown>;
}
export type ValidationDiagnosticStatus = 'VIOLATION' | 'RULE_EXECUTION_ERROR' | 'BROKEN_RULE';
export type ValidationReportScopeKind = 'MODEL' | 'OBJECT' | 'FEATURE' | 'SELECTION' | 'RULE' | 'RULE_SET';
export interface ValidationReportScope { kind: ValidationReportScopeKind; elementId?: string; featureId?: string; ruleId?: string; ruleSetId?: string; }
export interface ValidationDiagnosticLocation { modelElementId?: string; featureId?: string; relationInstanceId?: string; }
export interface ValidationExecutionMetrics { evaluatedRules: number; skippedRules: number; durationMillis: number; }
export interface ModelValidationDiagnostic {
    id: string;
    kind: ValidationDiagnosticStatus;
    ruleId: string;
    severity: ValidationSeverity;
    message: string;
    location?: ValidationDiagnosticLocation;
    quickFixIds: string[];
    details?: Record<string, string>;
}
export interface ModelValidationReport {
    schemaVersion: '1.0';
    id: string;
    modelId: string;
    metamodelId: string;
    validationModelId: string;
    generatedAt: string;
    scope: ValidationReportScope;
    diagnostics: ModelValidationDiagnostic[];
    metrics: ValidationExecutionMetrics;
    planId?: string;
    mode?: ValidationRunMode;
    startedAt?: string;
    completedAt?: string;
    executedRuleIds?: string[];
    skippedRuleIds?: string[];
}
