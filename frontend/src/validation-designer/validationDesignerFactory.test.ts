import { describe, expect, it } from 'vitest';
import { canonicalMetaModelFixture } from '../metamodel-designer/test-fixtures/canonicalMetaModelFixture';
import {
    cloneExpressionWithFreshIds,
    createAttributeComparisonExpression,
    createDefaultAction,
    createDefaultExpression,
    createDefaultOperation,
    createEmptyActionModel,
    createEmptyExpressionModel,
    createEmptyValidationModel,
    createUniqueRuleCode
} from './validationDesignerFactory';

function collectNodeIds(node: ReturnType<typeof createDefaultExpression>['root']): string[] {
    const ids = [node.nodeId];
    if (node.kind === 'ATTRIBUTE_ACCESS' || node.kind === 'RELATION_NAVIGATION') ids.push(...collectNodeIds(node.target));
    if (node.kind === 'UNARY') ids.push(...collectNodeIds(node.operand));
    if (node.kind === 'BINARY') ids.push(...collectNodeIds(node.left), ...collectNodeIds(node.right));
    if (node.kind === 'COLLECTION') {
        ids.push(...collectNodeIds(node.source));
        if (node.expression !== undefined) ids.push(...collectNodeIds(node.expression));
    }
    if (node.kind === 'CONDITIONAL') ids.push(...collectNodeIds(node.condition), ...collectNodeIds(node.whenTrue), ...collectNodeIds(node.whenFalse));
    if (node.kind === 'FUNCTION') node.arguments.forEach(argument => ids.push(...collectNodeIds(argument)));
    if (node.kind === 'OPERATION_CALL') { ids.push(...collectNodeIds(node.target)); node.arguments.forEach(argument => ids.push(...collectNodeIds(argument))); }
    return ids;
}

describe('validation designer factories', () => {
    it('creates linked empty canonical documents', () => {
        const expressions = createEmptyExpressionModel('shop');
        const actions = createEmptyActionModel('shop', expressions.id);
        const validations = createEmptyValidationModel('shop', expressions.id, actions.id);
        expect(actions.expressionModelId).toBe(expressions.id);
        expect(validations.expressionModelId).toBe(expressions.id);
        expect(validations.actionModelId).toBe(actions.id);
        expect(actions.id).toBe('shop-actions');
        expect(validations.id).toBe('shop-validations');
        expect(actions.id).toMatch(/^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/);
        expect(validations.id).toMatch(/^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/);
    });

    it('keeps derived validation document identifiers within the backend length contract', () => {
        const metamodelId = `m${'a'.repeat(119)}`;
        const expressions = createEmptyExpressionModel(metamodelId);
        const actions = createEmptyActionModel(metamodelId, expressions.id);
        const validations = createEmptyValidationModel(metamodelId, expressions.id, actions.id);
        expect(actions.id.length).toBeLessThanOrEqual(120);
        expect(validations.id.length).toBeLessThanOrEqual(120);
        expect(actions.id).toMatch(/^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/);
        expect(validations.id).toMatch(/^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$/);
    });

    it('creates an attribute comparison without external-language code', () => {
        const metamodel = canonicalMetaModelFixture();
        const attribute = metamodel.classes[1].attributes[0];
        const expression = createAttributeComparisonExpression(attribute, 'customer', 'NOT_EQUALS');
        expect(expression.contextClassId).toBe('customer');
        expect(JSON.stringify(expression)).not.toContain('OCL');
        expect(expression.root.kind).toBe('BINARY');
    });

    it('clones every expression node with a fresh stable identifier', () => {
        const expression = createDefaultExpression('customer');
        const clone = cloneExpressionWithFreshIds(expression);
        expect(clone.id).not.toBe(expression.id);
        expect(new Set(collectNodeIds(clone.root)).size).toBe(collectNodeIds(clone.root).length);
        expect(collectNodeIds(clone.root)).not.toEqual(collectNodeIds(expression.root));
    });

    it('creates every supported quick-fix operation', () => {
        const metamodel = canonicalMetaModelFixture();
        const action = createDefaultAction();
        const kinds = ['SET_ATTRIBUTE', 'CLEAR_ATTRIBUTE', 'COPY_ATTRIBUTE', 'ADD_RELATION', 'REMOVE_RELATION', 'CREATE_ELEMENT', 'DELETE_ELEMENT', 'TRANSFORM_TEXT', 'EXECUTE_ACTION', 'INVOKE_OPERATION'] as const;
        expect(kinds.map(kind => createDefaultOperation(kind, metamodel).kind)).toEqual(kinds);
        expect(action.transactionPolicy).toBe('ATOMIC');
        expect(action.undoPolicy).toBe('REQUIRED');
    });

    it('generates unique problem codes', () => {
        expect(createUniqueRuleCode('positive age', [])).toBe('POSITIVE_AGE');
        expect(createUniqueRuleCode('positive age', ['POSITIVE_AGE', 'POSITIVE_AGE_2'])).toBe('POSITIVE_AGE_3');
    });
});
