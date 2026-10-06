import type { DesignerStructureNode } from '../designer-ui/DesignerStructureTree';
import type { MetaModel } from '../metamodel-designer/types';
import type { ActionModel, ExpressionModel, OperationBehaviorModel, ValidationModel, ValidationRule } from '../validation/types';
import type { ValidationStructureSelection } from './validationStructureTypes';

export function buildValidationStructure(
    metamodel: MetaModel,
    validations: ValidationModel,
    expressions: ExpressionModel,
    actions: ActionModel,
    behaviors: OperationBehaviorModel,
    verifiedRuleIds: ReadonlySet<string> = new Set()
): DesignerStructureNode<ValidationStructureSelection>[] {
    const rulesById = new Map(validations.rules.map(rule => [rule.id, rule]));
    const actionsById = new Map(actions.actions.map(action => [action.id, action]));
    const expressionOwners = new Map(validations.rules.map(rule => [rule.conditionExpressionId, rule.id]));
    const groupedRuleIds = new Set(validations.ruleSets.flatMap(ruleSet => ruleSet.ruleIds));
    const ruleNode = (rule: ValidationRule): DesignerStructureNode<ValidationStructureSelection> => ({
        id: `validation:RULE:${rule.id}`,
        label: rule.name || rule.code,
        detail: rule.code,
        badge: rule.enabled ? rule.severity : 'DISABLED',
        icon: '●',
        iconTone: verifiedRuleIds.has(rule.id) ? 'success' : 'danger',
        iconLabel: verifiedRuleIds.has(rule.id) ? 'Validation verified' : 'Validation pending verification',
        data: { kind: 'RULE' as const, id: rule.id },
        children: [
            { id: `validation-rule-context:${rule.id}`, label: 'Context', detail: `${rule.context.kind}${rule.context.classId ? ` · ${rule.context.classId}` : ''}`, badge: 'CONTEXT', data: { kind: 'RULE' as const, id: rule.id } },
            { id: `validation-rule-expression:${rule.id}`, label: 'Condition expression', detail: rule.conditionExpressionId, badge: 'EXPRESSION', data: { kind: 'RULE' as const, id: rule.id } },
            { id: `validation-rule-fixes:${rule.id}`, label: 'Available quick fixes', badge: String(rule.quickFixIds.length), data: { kind: 'RULE' as const, id: rule.id }, children: rule.quickFixIds.map(actionId => ({ id: `validation-rule-fix:${rule.id}:${actionId}`, label: actionsById.get(actionId)?.name || actionId, detail: actionId, badge: 'QUICK FIX', data: { kind: 'ACTION' as const, id: actionId } })) }
        ]
    });
    const ruleSets = validations.ruleSets.map(ruleSet => ({
        id: `validation:RULE_SET:${ruleSet.id}`,
        label: ruleSet.name || 'Unnamed rule set',
        detail: ruleSet.id,
        badge: ruleSet.enabled ? `${ruleSet.ruleIds.length} RULES` : 'DISABLED',
        data: { kind: 'RULE_SET' as const, id: ruleSet.id },
        children: ruleSet.ruleIds.map(ruleId => rulesById.get(ruleId)).filter((rule): rule is ValidationRule => rule !== undefined).map(ruleNode)
    }));
    const ungroupedRules = validations.rules.filter(rule => !groupedRuleIds.has(rule.id)).map(ruleNode);
    const actionNodes = actions.actions.map(action => ({
        id: `validation:ACTION:${action.id}`,
        label: action.name || 'Unnamed quick fix',
        detail: action.id,
        badge: action.enabled ? action.transactionPolicy : 'DISABLED',
        data: { kind: 'ACTION' as const, id: action.id },
        children: [
            { id: `validation-action-parameters:${action.id}`, label: 'Parameters', badge: String(action.parameters.length), data: { kind: 'ACTION' as const, id: action.id }, children: action.parameters.map(parameter => ({ id: `validation-action-parameter:${action.id}:${parameter.name}`, label: parameter.name, detail: parameter.type, badge: parameter.required ? 'REQUIRED' : 'PARAMETER', data: { kind: 'ACTION' as const, id: action.id } })) },
            { id: `validation-action-operations:${action.id}`, label: 'Operations', badge: String(action.operations.length), data: { kind: 'ACTION' as const, id: action.id }, children: action.operations.map(operation => ({ id: `validation-action-operation:${action.id}:${operation.operationId}`, label: operation.kind, detail: operation.operationId, badge: 'OPERATION', data: { kind: 'ACTION' as const, id: action.id } })) }
        ]
    }));
    const expressionNodes = expressions.expressions.map(expression => ({
        id: `validation:EXPRESSION:${expression.id}`,
        label: expression.name || 'Unnamed expression',
        detail: expression.id,
        badge: expression.root.kind,
        data: expressionOwners.has(expression.id) ? { kind: 'RULE' as const, id: expressionOwners.get(expression.id)! } : { kind: 'EXPRESSION' as const, id: expression.id }
    }));
    const operationNames = new Map(metamodel.classes.flatMap(metaClass => metaClass.operations.map(operation => [operation.id, `${metaClass.name}.${operation.name}`] as const)));
    const operationNodes = behaviors.bindings.map(binding => ({
        id: `validation:OPERATION:${binding.operationId}`,
        label: operationNames.get(binding.operationId) || binding.operationId,
        detail: binding.operationId,
        badge: binding.implementationKind,
        data: { kind: 'OPERATION' as const, id: binding.operationId }
    }));
    return [{
        id: 'validation-root',
        label: 'Validation and behavior definitions',
        detail: validations.id,
        badge: 'VALIDATION',
        children: [
            { id: 'validation-rule-sets', label: 'Rule sets', badge: String(ruleSets.length), children: ruleSets },
            { id: 'validation-ungrouped-rules', label: 'Ungrouped rules', badge: String(ungroupedRules.length), children: ungroupedRules },
            { id: 'validation-actions', label: 'Quick fixes and actions', badge: String(actionNodes.length), children: actionNodes },
            { id: 'validation-expressions', label: 'Expressions', badge: String(expressionNodes.length), children: expressionNodes },
            { id: 'validation-operations', label: 'Operation bindings', badge: String(operationNodes.length), children: operationNodes }
        ]
    }];
}
