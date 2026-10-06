import type { MetaAttribute, MetaClass, MetaModel } from '../metamodel-designer/types';
import type { ExpressionDefinition } from '../validation/types';
import type {
    TransformationBinding,
    TransformationBindingKind,
    TransformationDefinitionDocument,
    TransformationDirectionality,
    TransformationEndpoint,
    TransformationGenerationRule,
    TransformationKind,
    TransformationRule,
    TransformationRuleDirection,
    TransformationValuePolicy,
} from './types';

export function createTransformationId(prefix: string): string {
    const token = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    return `${prefix}-${token}`;
}

export function createEmptyTransformation(sourceMetamodelId = '', targetMetamodelId = ''): TransformationDefinitionDocument {
    return {
        schemaVersion: '1.1', id: createTransformationId('transformation'), name: 'New transformation', kind: 'M2M', directionality: 'UNIDIRECTIONAL',
        synchronizationPolicy: { conflictPolicy:'FAIL_ON_CONFLICT', propagateDeletions:false, requireTraceForUpdates:true },
        sources: [endpoint('source', 'SOURCE', sourceMetamodelId, 'source')], targets: [endpoint('target', 'TARGET', targetMetamodelId, 'target')],
        rules: [], generationRules: [], executionPolicy: { mode: 'BATCH', deterministic: true, failOnBindingError: true },
        tracePolicy: { enabled: true, retainBindingDetails: true }, annotations: {},
    };
}

export function withTransformationKind(document: TransformationDefinitionDocument, kind: TransformationKind): TransformationDefinitionDocument {
    if (kind === document.kind) return document;
    const source = document.sources.length === 0 ? [endpoint('source', 'SOURCE', '', 'source')] : document.sources;
    const targets = document.targets.length === 0 ? [endpoint('target', 'TARGET', '', 'target')] : document.targets;
    return {
        ...document, kind, directionality: kind === 'M2M' ? document.directionality : 'UNIDIRECTIONAL', sources: source, targets,
        textToModel: kind === 'T2M' ? (document.textToModel ?? { grammarId:'', relativePath:'model.hydra', postTransformationId:'' }) : document.textToModel,
        generationRules: document.generationRules ?? [],
    };
}

export function withTransformationDirectionality(document: TransformationDefinitionDocument, directionality: TransformationDirectionality): TransformationDefinitionDocument {
    if (directionality === document.directionality) return document;
    const rules = directionality === 'UNIDIRECTIONAL'
        ? document.rules.map(rule => rule.direction === 'REVERSE' ? { ...rule, enabled:false } : rule)
        : document.rules.map(rule => ({ ...rule, direction:rule.direction ?? 'FORWARD', correspondenceId:rule.correspondenceId || rule.id || createTransformationId('correspondence') }));
    return {
        ...document,
        directionality,
        rules,
        tracePolicy: directionality === 'BIDIRECTIONAL' ? { ...document.tracePolicy, enabled:true } : document.tracePolicy,
    };
}

export function withEndpointMetamodel(document: TransformationDefinitionDocument, role: 'SOURCE' | 'TARGET', metamodelId: string): TransformationDefinitionDocument {
    const collection = role === 'SOURCE' ? document.sources : document.targets;
    const fallback = endpoint(role === 'SOURCE' ? 'source' : 'target', role, metamodelId, role === 'SOURCE' ? 'source' : 'target');
    const next = collection.length === 0 ? [fallback] : collection.map((item, index) => index === 0 ? { ...item, metamodelId, metamodelFingerprint: '' } : item);
    return role === 'SOURCE' ? { ...document, sources: next } : { ...document, targets: next };
}

export function createRule(document: TransformationDefinitionDocument, sourceClass: MetaClass, targetClass: MetaClass,
    direction: TransformationRuleDirection = 'FORWARD'): TransformationRule {
    const id = createTransformationId('rule');
    const backward = direction === 'REVERSE';
    const inputEndpoint = backward ? document.targets[0] : document.sources[0];
    const outputEndpoint = backward ? document.sources[0] : document.targets[0];
    const usedAliases = document.rules.filter(item => item.direction === direction).flatMap(item => item.targets.map(target => target.alias));
    const targetAlias = uniqueAlias(targetClass.name || 'target', usedAliases);
    const opposite = document.rules.find(item => item.direction !== direction && item.sourceTypeId === targetClass.id
        && item.targets.some(target => target.typeId === sourceClass.id));
    const correspondenceId = opposite?.correspondenceId || createTransformationId('correspondence');
    return {
        id, name: `${sourceClass.name} to ${targetClass.name}`, enabled: true, sourceEndpointId: inputEndpoint?.id ?? (backward ? 'target' : 'source'),
        sourceTypeId: sourceClass.id, sourceAlias: sourceClass.name ? lowerFirst(sourceClass.name) : 'source',
        targets: [{ id: createTransformationId('target-pattern'), endpointId: outputEndpoint?.id ?? (backward ? 'source' : 'target'), typeId: targetClass.id, alias: targetAlias, annotations: {} }],
        bindings: [], annotations: {}, direction, correspondenceId,
    };
}

export function createGenerationRule(document: TransformationDefinitionDocument, contextClass: MetaClass): TransformationGenerationRule {
    return {
        id:createTransformationId('generation-rule'), name:`Generate ${contextClass.name}`, enabled:true,
        sourceEndpointId:document.sources[0]?.id ?? 'source', contextTypeId:contextClass.id,
        outputPathTemplate:`generated/{{attr:${contextClass.attributes[0]?.id ?? 'name'}}}.txt`,
        contentTemplate:`${contextClass.name} {{attr:${contextClass.attributes[0]?.id ?? 'name'}}}`, overwritePolicy:'FAIL', annotations:{},
    };
}

export function createGuard(rule: TransformationRule): ExpressionDefinition { return booleanExpression(`${rule.id}-guard`, `${rule.name} condition`, rule.sourceTypeId); }
export function createGenerationGuard(rule: TransformationGenerationRule): ExpressionDefinition { return booleanExpression(`${rule.id}-guard`, `${rule.name} condition`, rule.contextTypeId); }

export function createBindingExpression(rule: TransformationRule, targetAttribute: MetaAttribute): ExpressionDefinition {
    return {
        id:createTransformationId('binding-expression'), name:`${rule.name} ${targetAttribute.name}`, description:'', contextClassId:rule.sourceTypeId,
        declaredReturnType:{ kind:'ANY' }, root:{ kind:'LITERAL', nodeId:createTransformationId('node'), valueType:{ kind:'NULL' }, value:null },
        annotations:{ 'hydra:transformationBinding':'true' },
    };
}

export function defaultValuePolicy(): TransformationValuePolicy {
    return { collectionMode:'PRESERVE', conversionMode:'NONE', enumMapping:{}, errorPolicy:'FAIL' };
}

export function setAttributeBinding(rule: TransformationRule, targetAttribute: MetaAttribute, kind: TransformationBindingKind, sourceAttribute?: MetaAttribute): TransformationRule {
    const targetAlias = rule.targets[0]?.alias ?? 'target';
    const existing = rule.bindings.find(item => item.targetAlias === targetAlias && item.targetFeatureId === targetAttribute.id);
    if (kind === 'TRACE_EQUIVALENT') return rule;
    const binding: TransformationBinding = {
        id:existing?.id ?? createTransformationId('binding'), targetAlias, targetFeatureId:targetAttribute.id, kind,
        sourceFeatureId:kind === 'DIRECT' ? sourceAttribute?.id ?? '' : '',
        literalValue:kind === 'CONSTANT' ? existing?.literalValue ?? '' : undefined,
        expression:kind === 'EXPRESSION' ? existing?.expression ?? createBindingExpression(rule, targetAttribute) : undefined,
        valuePolicy:existing?.valuePolicy ?? defaultValuePolicy(), annotations:existing?.annotations ?? {},
    };
    return { ...rule, bindings:[...rule.bindings.filter(item => item.id !== existing?.id), binding] };
}

export function addDirectBinding(rule: TransformationRule, sourceAttribute: MetaAttribute, targetAttribute: MetaAttribute): TransformationRule {
    return setAttributeBinding(rule, targetAttribute, 'DIRECT', sourceAttribute);
}

export function mapCompatibleNames(rule: TransformationRule, sourceClass: MetaClass, targetClass: MetaClass): TransformationRule {
    return targetClass.attributes.reduce((current, targetAttribute) => {
        const sourceAttribute = sourceClass.attributes.find(candidate => candidate.name.localeCompare(targetAttribute.name, undefined, { sensitivity: 'base' }) === 0);
        return sourceAttribute === undefined ? current : addDirectBinding(current, sourceAttribute, targetAttribute);
    }, rule);
}

export function sourceClassForRule(rule: TransformationRule, source: MetaModel | null): MetaClass | undefined { return source?.classes.find(item => item.id === rule.sourceTypeId); }
export function targetClassForRule(rule: TransformationRule, target: MetaModel | null): MetaClass | undefined { return target?.classes.find(item => item.id === rule.targets[0]?.typeId); }
export function replaceRule(document: TransformationDefinitionDocument, rule: TransformationRule): TransformationDefinitionDocument { return { ...document, rules:document.rules.map(item => item.id === rule.id ? rule : item) }; }
export function replaceGenerationRule(document: TransformationDefinitionDocument, rule: TransformationGenerationRule): TransformationDefinitionDocument { return { ...document, generationRules:(document.generationRules ?? []).map(item => item.id === rule.id ? rule : item) }; }

function booleanExpression(id:string,name:string,contextClassId:string):ExpressionDefinition {
    return { id, name, description:'', contextClassId, declaredReturnType:{ kind:'BOOLEAN' }, root:{ kind:'LITERAL', nodeId:createTransformationId('node'), valueType:{ kind:'BOOLEAN' }, value:true }, annotations:{ 'hydra:transformationGuard':'true' } };
}
function endpoint(id: string, role: 'SOURCE' | 'TARGET', metamodelId: string, alias: string): TransformationEndpoint { return { id, role, metamodelId, metamodelFingerprint: '', alias, required: true, annotations: {} }; }
function lowerFirst(value: string): string { return value.length === 0 ? value : `${value[0].toLowerCase()}${value.slice(1)}`; }
function uniqueAlias(base: string, used: string[]): string { const normalized = lowerFirst(base.replace(/[^A-Za-z0-9_]/g, '')) || 'target'; if (!used.includes(normalized)) return normalized; let index = 2; while (used.includes(`${normalized}${index}`)) index += 1; return `${normalized}${index}`; }
