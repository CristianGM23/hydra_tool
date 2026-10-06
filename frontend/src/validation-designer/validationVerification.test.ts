import { describe, expect, it } from 'vitest';
import type {
    ActionModel, ActionModelCheckReport, ExpressionModel, ExpressionModelCheckReport,
    OperationBehaviorModel, OperationBehaviorSaveResponse, ValidationModel, ValidationModelCheckReport
} from '../validation/types';
import { verifiedRuleIdsFromSignatures, verifiedRuleSignaturesFromReports } from './validationVerification';

const expressions: ExpressionModel = {
    schemaVersion: '1.0', id: 'expressions', metamodelId: 'mm', annotations: {},
    expressions: [
        { id: 'rule-expression', name: 'Rule', root: { nodeId: 'rule-node', kind: 'LITERAL', valueType: { kind: 'BOOLEAN' }, value: true } },
        { id: 'operation-expression', name: 'Operation', root: { nodeId: 'operation-node', kind: 'LITERAL', valueType: { kind: 'BOOLEAN' }, value: true } },
        { id: 'unrelated-expression', name: 'Unrelated', root: { nodeId: 'unrelated-node', kind: 'LITERAL', valueType: { kind: 'BOOLEAN' }, value: true } }
    ]
};
const actions: ActionModel = {
    schemaVersion: '1.0', id: 'actions', metamodelId: 'mm', expressionModelId: expressions.id, annotations: {}, actions: [{
        id: 'quick-fix', name: 'Quick fix', enabled: true, parameters: [], transactionPolicy: 'ATOMIC', undoPolicy: 'REQUIRED',
        operations: [{ kind: 'INVOKE_OPERATION', operationId: 'invoke', target: { kind: 'SELF' }, metamodelOperationId: 'operation-used', arguments: [] }]
    }]
};
const behaviors: OperationBehaviorModel = {
    schemaVersion: '1.0', metamodelId: 'mm', annotations: {}, bindings: [
        { operationId: 'operation-used', implementationKind: 'EXPRESSION', expressionId: 'operation-expression' },
        { operationId: 'operation-unrelated', implementationKind: 'EXPRESSION', expressionId: 'unrelated-expression' }
    ]
};
const validations: ValidationModel = {
    schemaVersion: '1.0', id: 'validations', metamodelId: 'mm', expressionModelId: expressions.id, actionModelId: actions.id,
    annotations: {}, ruleSets: [], importedSources: [], rules: [{
        id: 'rule', name: 'Rule', code: 'RULE', enabled: true, context: { kind: 'MODEL' }, severity: 'ERROR',
        conditionExpressionId: 'rule-expression', messageTemplate: 'Invalid', triggers: ['MANUAL'], dependencies: [], quickFixIds: ['quick-fix'],
        executionMode: 'INCREMENTAL', authoritativeRepresentation: 'CANONICAL', conversionStatus: 'FULL'
    }]
};
const validationReport: ValidationModelCheckReport = {
    validationModelId: validations.id, diagnostics: [], rules: [{ ruleId: 'rule', diagnostics: [], inferredDependencies: [] }]
};
const expressionReport: ExpressionModelCheckReport = {
    expressionModelId: expressions.id, diagnostics: [], expressions: expressions.expressions.map(expression => ({
        expressionId: expression.id, inferredType: { kind: 'BOOLEAN' }, diagnostics: [], dependencies: []
    }))
};
const actionReport: ActionModelCheckReport = {
    actionModelId: actions.id, diagnostics: [], actions: [{ actionId: 'quick-fix', diagnostics: [] }]
};
const behaviorValidation = (path?: string): OperationBehaviorSaveResponse['validation'] => ({ issues: path === undefined ? [] : [{
    severity: 'ERROR', code: 'BROKEN', path, message: 'Broken binding'
}] });

describe('validation verification dependency scoping', () => {
    it('keeps a rule verified when an unrelated operation binding fails', () => {
        const signatures = verifiedRuleSignaturesFromReports(validations, expressions, actions, behaviors,
            validationReport, expressionReport, actionReport, behaviorValidation('$.bindings[1].expressionId'));
        expect(verifiedRuleIdsFromSignatures(validations, expressions, actions, behaviors, signatures)).toEqual(new Set(['rule']));
    });

    it('does not mark a rule verified when a binding it actually uses fails', () => {
        const signatures = verifiedRuleSignaturesFromReports(validations, expressions, actions, behaviors,
            validationReport, expressionReport, actionReport, behaviorValidation('$.bindings[0].expressionId'));
        expect(signatures).toEqual({});
    });

    it('invalidates only edits inside the verified dependency closure', () => {
        const signatures = verifiedRuleSignaturesFromReports(validations, expressions, actions, behaviors,
            validationReport, expressionReport, actionReport, behaviorValidation());
        const unrelatedEdit: ExpressionModel = { ...expressions, expressions: expressions.expressions.map(expression =>
            expression.id === 'unrelated-expression' ? { ...expression, name: 'Unrelated changed' } : expression) };
        expect(verifiedRuleIdsFromSignatures(validations, unrelatedEdit, actions, behaviors, signatures)).toEqual(new Set(['rule']));
        const relevantEdit: ExpressionModel = { ...expressions, expressions: expressions.expressions.map(expression =>
            expression.id === 'operation-expression' ? { ...expression, name: 'Operation changed' } : expression) };
        expect(verifiedRuleIdsFromSignatures(validations, relevantEdit, actions, behaviors, signatures)).toEqual(new Set());
    });
});
