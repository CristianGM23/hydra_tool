import type { MetaAttribute, MetaClass, MetaModel, MetaRelation } from '../metamodel-designer/types';
import type { ActionModelCheckReport, ExpressionModelCheckReport, ValidationModelCheckReport, ValidationRule } from '../validation/types';
import type { ExpressionDefinition, ExpressionNode, ValidationDependency } from '../validation/types';

export interface ClassAttributeOption { owner: MetaClass; attribute: MetaAttribute; }

export function findClass(metamodel: MetaModel, classId?: string): MetaClass | undefined {
    return metamodel.classes.find(item => item.id === classId);
}

export function allAttributes(metamodel: MetaModel): ClassAttributeOption[] {
    return metamodel.classes.flatMap(owner => owner.attributes.map(attribute => ({ owner, attribute })));
}

export function attributesForContext(metamodel: MetaModel, classId?: string): ClassAttributeOption[] {
    if (classId === undefined || classId === '') return allAttributes(metamodel);
    const result: ClassAttributeOption[] = [];
    const visited = new Set<string>();
    const visit = (currentId: string): void => {
        if (visited.has(currentId)) return;
        visited.add(currentId);
        const owner = findClass(metamodel, currentId);
        if (owner === undefined) return;
        result.push(...owner.attributes.map(attribute => ({ owner, attribute })));
        metamodel.relations
            .filter(relation => relation.kind === 'INHERITANCE' && relation.sourceTypeId === currentId)
            .forEach(relation => visit(relation.targetTypeId));
    };
    visit(classId);
    return result;
}

export function relationsForContext(metamodel: MetaModel, classId?: string): MetaRelation[] {
    if (classId === undefined || classId === '') return metamodel.relations;
    return metamodel.relations.filter(relation => relation.sourceTypeId === classId || relation.targetTypeId === classId);
}

export function ruleGroupId(ruleId: string, ruleSets: { id: string; ruleIds: string[] }[]): string {
    return ruleSets.find(item => item.ruleIds.includes(ruleId))?.id ?? '';
}

export function countRuleDiagnostics(rule: ValidationRule, report: ValidationModelCheckReport | null): number {
    return report?.rules.find(item => item.ruleId === rule.id)?.diagnostics.length ?? 0;
}

export function totalDiagnosticCount(reports: {
    expression: ExpressionModelCheckReport | null;
    action: ActionModelCheckReport | null;
    validation: ValidationModelCheckReport | null;
}): { errors: number; warnings: number; information: number } {
    const severities = [
        ...(reports.expression?.diagnostics ?? []),
        ...(reports.action?.diagnostics ?? []),
        ...(reports.validation?.diagnostics ?? [])
    ].map(item => item.severity);
    return {
        errors: severities.filter(item => item === 'ERROR').length,
        warnings: severities.filter(item => item === 'WARNING').length,
        information: severities.filter(item => item === 'INFO').length
    };
}

export function inferExpressionDependencies(expression: ExpressionDefinition): ValidationDependency[] {
    const keys = new Set<string>();
    const dependencies: ValidationDependency[] = [];
    const add = (kind: ValidationDependency['kind'], elementId: string): void => {
        const key = `${kind}:${elementId}`;
        if (keys.has(key)) return;
        keys.add(key);
        dependencies.push({ kind, elementId });
    };
    if (expression.contextClassId !== undefined && expression.contextClassId !== '') add('CLASS', expression.contextClassId);
    const visit = (node: ExpressionNode): void => {
        switch (node.kind) {
            case 'LITERAL':
            case 'SELF':
            case 'VARIABLE': return;
            case 'MODEL': add('MODEL', ''); return;
            case 'CLASS_INSTANCES': add('CLASS', node.classId); return;
            case 'ATTRIBUTE_ACCESS': add('ATTRIBUTE', node.attributeId); visit(node.target); return;
            case 'RELATION_NAVIGATION': add('RELATION', node.relationId); visit(node.target); return;
            case 'UNARY': visit(node.operand); return;
            case 'BINARY': visit(node.left); visit(node.right); return;
            case 'COLLECTION': visit(node.source); if (node.expression !== undefined) visit(node.expression); return;
            case 'CONDITIONAL': visit(node.condition); visit(node.whenTrue); visit(node.whenFalse); return;
            case 'FUNCTION': node.arguments.forEach(visit); return;
            case 'OPERATION_CALL': add('OPERATION', node.operationId); visit(node.target); node.arguments.forEach(visit); return;
        }
    };
    visit(expression.root);
    return dependencies;
}
