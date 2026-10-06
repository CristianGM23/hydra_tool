import type { MetaAttribute, MetaClass, MetaModel, MetaPrimitiveType, MetaRelation } from '../metamodel-designer/types';
import { derivedValidationDocumentId } from '../validation/stableIdentifierPolicy';
import type {
    ActionDefinition,
    ActionModel,
    ActionOperation,
    BinaryExpressionOperator,
    ExpressionDefinition,
    ExpressionModel,
    ExpressionNode,
    ExpressionValueKind,
    ExpressionValueType,
    ValidationContext,
    ValidationModel,
    ValidationRule,
    ValidationRuleSet
} from '../validation/types';

let sequence = 0;

export function createValidationDesignerId(prefix: string): string {
    sequence += 1;
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return `${prefix}:${crypto.randomUUID()}`;
    }
    return `${prefix}:${Date.now().toString(36)}:${sequence.toString(36)}`;
}

export function createEmptyExpressionModel(metamodelId: string): ExpressionModel {
    return { schemaVersion: '1.0', id: metamodelId, metamodelId, expressions: [], annotations: {} };
}

export function createEmptyActionModel(metamodelId: string, expressionModelId: string): ActionModel {
    return { schemaVersion: '1.0', id: derivedValidationDocumentId(metamodelId, 'actions'), metamodelId, expressionModelId, actions: [], annotations: {} };
}

export function createEmptyValidationModel(metamodelId: string, expressionModelId: string, actionModelId: string): ValidationModel {
    return {
        schemaVersion: '1.0',
        id: derivedValidationDocumentId(metamodelId, 'validations'),
        metamodelId,
        expressionModelId,
        actionModelId,
        ruleSets: [],
        rules: [],
        importedSources: [],
        annotations: {}
    };
}

export function createDefaultRuleSet(index: number): ValidationRuleSet {
    return {
        id: createValidationDesignerId('rule-set'),
        name: index === 0 ? 'General validations' : `Validation group ${index + 1}`,
        description: '',
        enabled: true,
        order: index,
        ruleIds: [],
        annotations: {}
    };
}

export function createBooleanLiteral(value = true): ExpressionNode {
    return {
        kind: 'LITERAL',
        nodeId: createValidationDesignerId('node'),
        valueType: { kind: 'BOOLEAN' },
        value
    };
}

export function createSelfNode(): ExpressionNode {
    return { kind: 'SELF', nodeId: createValidationDesignerId('node') };
}

export function createDefaultExpression(contextClassId?: string): ExpressionDefinition {
    return {
        id: createValidationDesignerId('expression'),
        name: 'New validation condition',
        description: '',
        contextClassId,
        declaredReturnType: { kind: 'BOOLEAN' },
        root: createBooleanLiteral(true),
        annotations: {}
    };
}

export function createAttributeComparisonExpression(
    attribute: MetaAttribute,
    contextClassId: string,
    operator: BinaryExpressionOperator = 'EQUALS'
): ExpressionDefinition {
    const type = expressionTypeForAttribute(attribute);
    return {
        ...createDefaultExpression(contextClassId),
        name: `${attribute.name} condition`,
        root: {
            kind: 'BINARY',
            nodeId: createValidationDesignerId('node'),
            operator,
            left: {
                kind: 'ATTRIBUTE_ACCESS',
                nodeId: createValidationDesignerId('node'),
                target: createSelfNode(),
                attributeId: attribute.id
            },
            right: {
                kind: 'LITERAL',
                nodeId: createValidationDesignerId('node'),
                valueType: type,
                value: defaultLiteralValue(type)
            }
        }
    };
}

export function createDefaultValidationRule(metamodel: MetaModel, expression: ExpressionDefinition): ValidationRule {
    const firstClass = metamodel.classes[0];
    const context: ValidationContext = firstClass === undefined
        ? { kind: 'MODEL' }
        : { kind: 'CLASS', classId: firstClass.id };
    return {
        id: createValidationDesignerId('rule'),
        name: 'New validation',
        code: createUniqueRuleCode('NEW_VALIDATION', []),
        description: '',
        enabled: true,
        context,
        severity: 'ERROR',
        conditionExpressionId: expression.id,
        messageTemplate: 'The validation condition is not satisfied.',
        triggers: ['ON_SAVE', 'MANUAL'],
        dependencies: [],
        quickFixIds: [],
        executionMode: 'INCREMENTAL',
        authoritativeRepresentation: 'CANONICAL',
        conversionStatus: 'FULL',
        annotations: {}
    };
}

export function createDefaultAction(): ActionDefinition {
    return {
        id: createValidationDesignerId('action'),
        name: 'New quick fix',
        description: '',
        enabled: true,
        parameters: [],
        operations: [],
        transactionPolicy: 'ATOMIC',
        undoPolicy: 'REQUIRED',
        annotations: {}
    };
}

export function createDefaultOperation(kind: ActionOperation['kind'], metamodel: MetaModel): ActionOperation {
    const firstClass = metamodel.classes[0];
    const attributes = metamodel.classes.flatMap(metaClass => metaClass.attributes);
    const firstAttribute = attributes[0];
    const firstTextAttribute = attributes.find(attribute => attribute.type.kind === 'PRIMITIVE' && (attribute.type.targetId === 'STRING' || attribute.type.targetId === 'CHARACTER'));
    const firstRelation = metamodel.relations[0];
    const firstOperation = metamodel.classes.flatMap(metaClass => metaClass.operations).find(operation => operation.behaviorKind === 'COMMAND');
    const target = { kind: 'SELF' as const };
    const operationId = createValidationDesignerId('operation');
    switch (kind) {
        case 'SET_ATTRIBUTE':
            return { kind, operationId, target, attributeId: firstAttribute?.id ?? '', value: { literal: '' } };
        case 'CLEAR_ATTRIBUTE':
            return { kind, operationId, target, attributeId: firstAttribute?.id ?? '' };
        case 'COPY_ATTRIBUTE':
            return { kind, operationId, source: target, sourceAttributeId: firstAttribute?.id ?? '', target, targetAttributeId: firstAttribute?.id ?? '' };
        case 'ADD_RELATION':
            return { kind, operationId, relationId: firstRelation?.id ?? '', source: target, target: { kind: 'SELECTION' } };
        case 'REMOVE_RELATION':
            return { kind, operationId, relationId: firstRelation?.id ?? '', source: target, target: { kind: 'SELECTION' } };
        case 'CREATE_ELEMENT':
            return { kind, operationId, classId: firstClass?.id ?? '', resultName: 'createdElement' };
        case 'DELETE_ELEMENT':
            return { kind, operationId, target };
        case 'TRANSFORM_TEXT':
            return { kind, operationId, target, attributeId: firstTextAttribute?.id ?? '', transformation: 'TRIM' };
        case 'EXECUTE_ACTION':
            return { kind, operationId, actionId: '', arguments: {} };
        case 'INVOKE_OPERATION':
            return { kind, operationId, target, metamodelOperationId: firstOperation?.id ?? '', arguments: firstOperation?.parameters.map(() => ({ literal: '' })) ?? [] };
    }
}

export function createNodeForKind(kind: ExpressionNode['kind'], metamodel: MetaModel, contextClassId?: string): ExpressionNode {
    const firstClass = metamodel.classes.find(item => item.id === contextClassId) ?? metamodel.classes[0];
    const firstAttribute = firstClass?.attributes[0] ?? metamodel.classes.flatMap(item => item.attributes)[0];
    const firstRelation = metamodel.relations.find(item => item.sourceTypeId === firstClass?.id) ?? metamodel.relations[0];
    const firstOperation = firstClass?.operations.find(operation => operation.behaviorKind !== 'COMMAND') ?? metamodel.classes.flatMap(item => item.operations).find(operation => operation.behaviorKind !== 'COMMAND');
    const nodeId = createValidationDesignerId('node');
    switch (kind) {
        case 'LITERAL': return { kind, nodeId, valueType: { kind: 'BOOLEAN' }, value: true };
        case 'SELF': return { kind, nodeId };
        case 'MODEL': return { kind, nodeId };
        case 'CLASS_INSTANCES': return { kind, nodeId, classId: firstClass?.id ?? '' };
        case 'ATTRIBUTE_ACCESS': return { kind, nodeId, target: createSelfNode(), attributeId: firstAttribute?.id ?? '' };
        case 'RELATION_NAVIGATION': return { kind, nodeId, target: createSelfNode(), relationId: firstRelation?.id ?? '' };
        case 'UNARY': return { kind, nodeId, operator: 'NOT', operand: createBooleanLiteral(true) };
        case 'BINARY': return { kind, nodeId, operator: 'EQUALS', left: createBooleanLiteral(true), right: createBooleanLiteral(true) };
        case 'COLLECTION': return { kind, nodeId, operator: 'COUNT', source: { kind: 'CLASS_INSTANCES', nodeId: createValidationDesignerId('node'), classId: firstClass?.id ?? '' } };
        case 'CONDITIONAL': return { kind, nodeId, condition: createBooleanLiteral(true), whenTrue: createBooleanLiteral(true), whenFalse: createBooleanLiteral(false) };
        case 'FUNCTION': return { kind, nodeId, functionName: '', arguments: [] };
        case 'VARIABLE': return { kind, nodeId, variableName: 'value' };
        case 'OPERATION_CALL': return { kind, nodeId, target: createSelfNode(), operationId: firstOperation?.id ?? '', arguments: firstOperation?.parameters.map(parameter => createLiteralForMetaType(parameter.type)) ?? [] };
    }
}

function createLiteralForMetaType(type: MetaAttribute['type']): ExpressionNode {
    const valueType: ExpressionValueType = type.kind === 'ENUM'
        ? { kind: 'ENUM', enumId: type.targetId }
        : type.kind === 'CLASS'
            ? { kind: 'OBJECT', classId: type.targetId }
            : type.kind === 'PRIMITIVE'
                ? { kind: primitiveExpressionKind(type.targetId as MetaPrimitiveType) }
                : { kind: 'ANY' };
    return { kind: 'LITERAL', nodeId: createValidationDesignerId('node'), valueType, value: defaultLiteralValue(valueType) };
}

export function expressionTypeForAttribute(attribute: MetaAttribute): ExpressionValueType {
    switch (attribute.type.kind) {
        case 'ENUM': return { kind: 'ENUM', enumId: attribute.type.targetId };
        case 'CLASS': return { kind: 'OBJECT', classId: attribute.type.targetId };
        case 'PRIMITIVE': return { kind: primitiveExpressionKind(attribute.type.targetId as MetaPrimitiveType) };
        case 'DATATYPE':
        case 'EXTERNAL': return { kind: 'ANY' };
    }
}

export function primitiveExpressionKind(type: MetaPrimitiveType): ExpressionValueKind {
    switch (type) {
        case 'BOOLEAN': return 'BOOLEAN';
        case 'BYTE':
        case 'SHORT':
        case 'INTEGER':
        case 'LONG':
        case 'BIG_INTEGER': return 'INTEGER';
        case 'FLOAT':
        case 'DOUBLE':
        case 'DECIMAL': return 'DECIMAL';
        case 'DATE': return 'DATE';
        case 'BINARY': return 'BINARY';
        case 'STRING':
        case 'CHARACTER': return 'STRING';
        case 'OBJECT': return 'ANY';
    }
}

export function defaultLiteralValue(type: ExpressionValueType): unknown {
    switch (type.kind) {
        case 'BOOLEAN': return false;
        case 'INTEGER':
        case 'DECIMAL': return 0;
        case 'STRING':
        case 'DATE':
        case 'DATETIME':
        case 'ENUM': return '';
        case 'COLLECTION': return [];
        default: return null;
    }
}

export function createUniqueRuleCode(base: string, existingCodes: string[]): string {
    const normalized = base.trim().toUpperCase().replace(/[^A-Z0-9_]+/g, '_').replace(/^_+|_+$/g, '') || 'VALIDATION';
    const codes = new Set(existingCodes);
    if (!codes.has(normalized)) return normalized;
    let suffix = 2;
    while (codes.has(`${normalized}_${suffix}`)) suffix += 1;
    return `${normalized}_${suffix}`;
}

export function relationLabel(relation: MetaRelation, metamodel: MetaModel): string {
    const source = metamodel.classes.find(item => item.id === relation.sourceTypeId)?.name ?? relation.sourceTypeId;
    const target = metamodel.classes.find(item => item.id === relation.targetTypeId)?.name ?? relation.targetTypeId;
    return `${source}.${relation.name || relation.kind.toLowerCase()} → ${target}`;
}

export function classLabel(metaClass: MetaClass | undefined): string {
    return metaClass?.name ?? 'Model';
}

export function cloneExpressionWithFreshIds(expression: ExpressionDefinition): ExpressionDefinition {
    return {
        ...structuredClone(expression),
        id: createValidationDesignerId('expression'),
        name: `${expression.name} copy`,
        root: cloneNodeWithFreshIds(expression.root)
    };
}

function cloneNodeWithFreshIds(node: ExpressionNode): ExpressionNode {
    const nodeId = createValidationDesignerId('node');
    switch (node.kind) {
        case 'LITERAL': return { ...node, nodeId, valueType: structuredClone(node.valueType), value: structuredClone(node.value) };
        case 'SELF':
        case 'MODEL': return { ...node, nodeId };
        case 'CLASS_INSTANCES': return { ...node, nodeId };
        case 'ATTRIBUTE_ACCESS': return { ...node, nodeId, target: cloneNodeWithFreshIds(node.target) };
        case 'RELATION_NAVIGATION': return { ...node, nodeId, target: cloneNodeWithFreshIds(node.target) };
        case 'UNARY': return { ...node, nodeId, operand: cloneNodeWithFreshIds(node.operand) };
        case 'BINARY': return { ...node, nodeId, left: cloneNodeWithFreshIds(node.left), right: cloneNodeWithFreshIds(node.right) };
        case 'COLLECTION': return { ...node, nodeId, source: cloneNodeWithFreshIds(node.source), expression: node.expression === undefined ? undefined : cloneNodeWithFreshIds(node.expression) };
        case 'CONDITIONAL': return { ...node, nodeId, condition: cloneNodeWithFreshIds(node.condition), whenTrue: cloneNodeWithFreshIds(node.whenTrue), whenFalse: cloneNodeWithFreshIds(node.whenFalse) };
        case 'FUNCTION': return { ...node, nodeId, arguments: node.arguments.map(cloneNodeWithFreshIds) };
        case 'VARIABLE': return { ...node, nodeId };
        case 'OPERATION_CALL': return { ...node, nodeId, target: cloneNodeWithFreshIds(node.target), arguments: node.arguments.map(cloneNodeWithFreshIds) };
    }
}
