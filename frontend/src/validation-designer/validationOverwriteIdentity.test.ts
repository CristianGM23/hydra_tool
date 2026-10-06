import { describe, expect, it } from 'vitest';
import { createDefaultExpression, createDefaultValidationRule, createEmptyExpressionModel, createEmptyValidationModel } from './validationDesignerFactory';
import { validationOverwriteIdentities } from './validationOverwriteIdentity';
import type { MetaModel } from '../metamodel-designer/types';

const metamodel: MetaModel = {
    schemaVersion: '2.1', id: 'people', name: 'People', classes: [{ id: 'person', name: 'Person', kind: 'CLASS', attributes: [], operations: [], annotations: {} }],
    enums: [], dataTypes: [], relations: [], annotations: {}
};

describe('validationOverwriteIdentities', () => {
    it('uses each validation rule rather than the container document as overwrite identity', () => {
        const expression = createDefaultExpression('person');
        const rule = { ...createDefaultValidationRule(metamodel, expression), id: 'rule-one', name: 'Unique name' };
        const expressions = { ...createEmptyExpressionModel('people'), expressions: [expression] };
        const validations = { ...createEmptyValidationModel('people', expressions.id, 'people-actions'), rules: [rule] };
        const identities = validationOverwriteIdentities(validations, expressions);
        expect(identities).toHaveLength(1);
        expect(identities[0]).toMatchObject({ stableId: 'rule-one', uniqueName: 'Unique name', entityKind: 'VALIDATION_RULE' });
    });

    it('treats equivalent conditions as the same semantic signature even when AST node IDs change', () => {
        const first = createDefaultExpression('person');
        const second = structuredClone(first);
        second.id = 'expr-two';
        second.root.nodeId = 'different-node-id';
        const firstRule = { ...createDefaultValidationRule(metamodel, first), id: 'rule-one', name: 'First', conditionExpressionId: first.id };
        const secondRule = { ...createDefaultValidationRule(metamodel, second), id: 'rule-two', name: 'Second', conditionExpressionId: second.id };
        const firstIdentities = validationOverwriteIdentities({ ...createEmptyValidationModel('people', 'exprs', 'actions'), rules: [firstRule] }, { ...createEmptyExpressionModel('people'), id: 'exprs', expressions: [first] });
        const secondIdentities = validationOverwriteIdentities({ ...createEmptyValidationModel('people', 'exprs', 'actions'), rules: [secondRule] }, { ...createEmptyExpressionModel('people'), id: 'exprs', expressions: [second] });
        expect(firstIdentities[0]?.semanticSignature).toBe(secondIdentities[0]?.semanticSignature);
    });
});
