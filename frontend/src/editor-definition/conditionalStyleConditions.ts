import type { ExpressionDefinition, ExpressionModel, ExpressionNode } from '../validation/types';
import type { ConditionalStyleRule } from './types';

/**
 * Returns a structural fingerprint for a condition. Generated node IDs, names,
 * descriptions and annotations are deliberately ignored, so copying the same
 * condition produces the same fingerprint.
 */
export function conditionalStyleConditionFingerprint(expression: ExpressionDefinition): string {
    return stable({
        contextClassId: expression.contextClassId ?? '',
        declaredReturnType: expression.declaredReturnType ?? null,
        root: stripGeneratedNodeIds(expression.root)
    });
}

/** Finds every conditional-style rule that shares its condition with another rule. */
export function duplicateConditionalStyleRuleIds(rules: ConditionalStyleRule[], expressionModel: ExpressionModel): Set<string> {
    const expressionById = new Map(expressionModel.expressions.map(expression => [expression.id, expression]));
    const byFingerprint = new Map<string, string[]>();
    for (const rule of rules) {
        if (rule.conditionExpressionId.trim() === '') continue;
        const expression = expressionById.get(rule.conditionExpressionId);
        if (expression === undefined) continue;
        const fingerprint = conditionalStyleConditionFingerprint(expression);
        const ids = byFingerprint.get(fingerprint) ?? [];
        ids.push(rule.id);
        byFingerprint.set(fingerprint, ids);
    }
    return new Set(Array.from(byFingerprint.values()).filter(ids => ids.length > 1).flat());
}

export function conditionDuplicatesAnotherRule(ruleId: string, expression: ExpressionDefinition, rules: ConditionalStyleRule[], expressionModel: ExpressionModel): boolean {
    const fingerprint = conditionalStyleConditionFingerprint(expression);
    const expressionById = new Map(expressionModel.expressions.map(item => [item.id, item]));
    return rules.some(rule => {
        if (rule.id === ruleId || rule.conditionExpressionId.trim() === '') return false;
        const other = expressionById.get(rule.conditionExpressionId);
        return other !== undefined && conditionalStyleConditionFingerprint(other) === fingerprint;
    });
}

function stripGeneratedNodeIds(node: ExpressionNode): unknown {
    const rest = { ...node } as Record<string, unknown>;
    delete rest.nodeId;
    const result: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(rest)) result[key] = stripValue(value);
    return result;
}

function stripValue(value: unknown): unknown {
    if (Array.isArray(value)) return value.map(stripValue);
    if (value !== null && typeof value === 'object') {
        if ('kind' in value && 'nodeId' in value) return stripGeneratedNodeIds(value as ExpressionNode);
        return Object.fromEntries(Object.entries(value as Record<string, unknown>).sort(([left], [right]) => left.localeCompare(right)).map(([key, child]) => [key, stripValue(child)]));
    }
    return value;
}

function stable(value: unknown): string {
    if (Array.isArray(value)) return `[${value.map(stable).join(',')}]`;
    if (value !== null && typeof value === 'object') {
        return `{${Object.entries(value as Record<string, unknown>).sort(([left], [right]) => left.localeCompare(right)).map(([key, child]) => `${JSON.stringify(key)}:${stable(child)}`).join(',')}}`;
    }
    return JSON.stringify(value) ?? 'undefined';
}
