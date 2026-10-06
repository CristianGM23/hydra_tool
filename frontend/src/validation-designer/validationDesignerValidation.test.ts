import { describe, expect, it } from 'vitest';
import { canonicalMetaModelFixture } from '../metamodel-designer/test-fixtures/canonicalMetaModelFixture';
import {
    createDefaultAction,
    createDefaultExpression,
    createDefaultOperation,
    createDefaultValidationRule,
    createEmptyActionModel,
    createEmptyExpressionModel,
    createEmptyValidationModel
} from './validationDesignerFactory';
import { findValidationDesignerBlockingError } from './validationDesignerValidation';

describe('validation designer preflight', () => {
    it('blocks incomplete quick fixes before JSON deserialization can fail', () => {
        const expressions = createEmptyExpressionModel('shop');
        const actions = createEmptyActionModel('shop', expressions.id);
        const validations = createEmptyValidationModel('shop', expressions.id, actions.id);
        actions.actions.push(createDefaultAction());
        expect(findValidationDesignerBlockingError(expressions, actions, validations)).toContain('at least one operation');
    });

    it('blocks invalid layer document IDs before a backend request is attempted', () => {
        const expressions = createEmptyExpressionModel('shop');
        const actions = { ...createEmptyActionModel('shop', expressions.id), id: 'shop:actions' };
        const validations = createEmptyValidationModel('shop', expressions.id, actions.id);
        expect(findValidationDesignerBlockingError(expressions, actions, validations)).toContain('quick-fix document');
    });

    it('accepts a complete canonical rule and quick fix', () => {
        const metamodel = canonicalMetaModelFixture();
        const expressions = createEmptyExpressionModel('shop');
        const expression = createDefaultExpression('customer');
        expressions.expressions.push(expression);
        const actions = createEmptyActionModel('shop', expressions.id);
        const action = { ...createDefaultAction(), operations: [createDefaultOperation('TRANSFORM_TEXT', metamodel)] };
        actions.actions.push(action);
        const validations = createEmptyValidationModel('shop', expressions.id, actions.id);
        validations.rules.push({ ...createDefaultValidationRule(metamodel, expression), quickFixIds: [action.id] });
        expect(findValidationDesignerBlockingError(expressions, actions, validations)).toBe('');
    });

    it('blocks a set-attribute operation without a fixed value or expression', () => {
        const metamodel = canonicalMetaModelFixture();
        const expressions = createEmptyExpressionModel('shop');
        const actions = createEmptyActionModel('shop', expressions.id);
        const operation = createDefaultOperation('SET_ATTRIBUTE', metamodel);
        if (operation.kind !== 'SET_ATTRIBUTE') throw new Error('Unexpected operation kind');
        actions.actions.push({ ...createDefaultAction(), operations: [{ ...operation, value: {} }] });
        const validations = createEmptyValidationModel('shop', expressions.id, actions.id);
        expect(findValidationDesignerBlockingError(expressions, actions, validations)).toContain('fixed value or an expression');
    });

});
