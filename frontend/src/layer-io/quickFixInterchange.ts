import type { MetaModel } from '../metamodel-designer/types';
import type { ActionDefinition, ActionModel, ActionOperation, ActionValueSource } from '../validation/types';
import { createDefaultAction, createValidationDesignerId } from '../validation-designer/validationDesignerFactory';
import { attributeName, className, findAttribute, findMetaClass, findRelation, parseBalancedBlocks, relationName, sanitizeIdentifier } from './interchangeSupport';
import type { LayerExportArtifact, LayerImportAnalysis, LayerInterchangeFormat } from './layerInterchangeTypes';
import { makeLayerInterchangeEnvelope, unwrapLayerInterchangeEnvelope } from './layerInterchangeEnvelope';

export type QuickFixInterchangeFormat = 'HYDRA_JSON' | 'EVL' | 'EOL';
export const QUICK_FIX_INTERCHANGE_FORMATS: readonly LayerInterchangeFormat<QuickFixInterchangeFormat>[] = [
    { id: 'HYDRA_JSON', label: 'HYDRA quick-fix JSON', description: 'Lossless HYDRA ActionModel interchange.', extensions: ['.quickfix.json', '.actions.json', '.json'], mimeType: 'application/json' },
    { id: 'EVL', label: 'EVL fix blocks', description: 'Imports and exports Epsilon Validation Language fix blocks. Common EOL repair statements are mapped to declarative HYDRA actions.', extensions: ['.evl'], mimeType: 'text/plain' },
    { id: 'EOL', label: 'Epsilon Object Language (EOL)', description: 'Imports/exports named EOL operations as editable quick fixes. HYDRA operation metadata preserves actions that have no direct EOL equivalent.', extensions: ['.eol'], mimeType: 'text/plain' }
];

export interface ImportedQuickFixLink { actionId: string; validationRuleName: string; }
export interface ImportedQuickFixLayer { actions: ActionDefinition[]; links: ImportedQuickFixLink[]; warnings: string[]; format: QuickFixInterchangeFormat; }

export function analyzeQuickFixImport(content: string, fileName: string, format: QuickFixInterchangeFormat, metamodel: MetaModel): LayerImportAnalysis {
    const imported = importQuickFixLayer(content, fileName, format, metamodel);
    const empty = imported.actions.filter(action => action.operations.length === 0);
    const unresolved = imported.warnings.filter(message => /not found|unresolved|retained only as source|could not/i.test(message));
    const operationCount = imported.actions.reduce((sum, action) => sum + action.operations.length, 0);
    return {
        summary: imported.actions.length === 0 ? 'No quick fix can be imported from this source.' : `${imported.actions.length} quick fix(es) identified with ${operationCount} mapped repair operation(s).`,
        canImport: imported.actions.length > 0,
        metrics: [
            { id: 'identified', label: 'Quick fixes identified', value: imported.actions.length, tone: imported.actions.length > 0 ? 'GOOD' : 'ERROR' },
            { id: 'operations', label: 'Repair operations mapped', value: operationCount, tone: operationCount > 0 ? 'GOOD' : 'WARNING' },
            { id: 'empty', label: 'Erroneous / empty fixes', value: empty.length, tone: empty.length > 0 ? 'WARNING' : 'GOOD' },
            { id: 'links', label: 'Validation links identified', value: imported.links.length, tone: 'NEUTRAL' },
            { id: 'unresolved', label: 'Unresolved constructs', value: unresolved.length, tone: unresolved.length > 0 ? 'WARNING' : 'GOOD' },
            { id: 'warnings', label: 'Warnings', value: imported.warnings.length, tone: imported.warnings.length > 0 ? 'WARNING' : 'GOOD' }
        ],
        diagnostics: [
            ...empty.map(action => ({ severity: 'WARNING' as const, category: 'EMPTY_QUICK_FIX', itemId: action.id, message: `${action.name}: no declarative repair operation could be mapped.` })),
            ...imported.warnings.map(message => ({ severity: 'WARNING' as const, category: 'IMPORT_WARNING', message }))
        ],
        warnings: imported.warnings
    };
}

export function importQuickFixLayer(content: string, fileName: string, format: QuickFixInterchangeFormat, metamodel: MetaModel): ImportedQuickFixLayer {
    if (format === 'HYDRA_JSON') {
        type Payload = ActionModel | { actionModel?: ActionModel; actions?: ActionDefinition[] };
        const envelope = unwrapLayerInterchangeEnvelope<Payload>(content, 'QUICK_FIX', metamodel.id);
        const parsed = envelope.payload;
        const actions = 'actions' in parsed && Array.isArray(parsed.actions) ? parsed.actions : 'actionModel' in parsed && parsed.actionModel !== undefined ? parsed.actionModel.actions : [];
        return { actions: actions.map(freshAction), links: [], warnings: envelope.warnings, format };
    }
    return format === 'EVL' ? importEvlFixes(content, fileName, metamodel) : importEolOperations(content, fileName, metamodel);
}

function importEvlFixes(content: string, fileName: string, metamodel: MetaModel): ImportedQuickFixLayer {
    const warnings: string[] = [];
    const actions: ActionDefinition[] = [];
    const links: ImportedQuickFixLink[] = [];
    const contexts = parseBalancedBlocks(content, /context\s+[A-Za-z_][\w!:]*\s*\{/gi);
    for (const context of contexts) {
        const contextName = context.header.match(/context\s+([A-Za-z_][\w!:]*)/i)?.[1] ?? '';
        const metaClass = findMetaClass(metamodel, contextName);
        const contextClassId = metaClass?.id ?? '';
        if (metaClass === undefined) warnings.push(`EVL context ${contextName} was not found. Its fixes were preserved as editable drafts; unresolved operations must be mapped to the current metamodel.`);
        const validationBlocks = parseBalancedBlocks(context.body, /(?:constraint|critique)\s+[A-Za-z_][\w-]*\s*\{/gi);
        for (const validationBlock of validationBlocks) {
            const validationName = validationBlock.header.match(/(?:constraint|critique)\s+([A-Za-z_][\w-]*)/i)?.[1] ?? '';
            const fixes = parseBalancedBlocks(validationBlock.body, /fix(?:\s+[A-Za-z_][\w-]*)?\s*\{/gi);
            for (const fix of fixes) {
                const title = extractField(fix.body, 'title');
                const headerName = fix.header.match(/fix\s+([A-Za-z_][\w-]*)/i)?.[1];
                const name = unquote(title) || headerName || `Imported fix ${actions.length + 1}`;
                const doBlock = parseBalancedBlocks(fix.body, /do\s*\{/gi)[0]?.body ?? '';
                const exact = hydraOperations(doBlock);
                const operations = exact.length > 0 ? exact : parseRepairStatements(doBlock, metamodel, contextClassId, warnings, name);
                const action = { ...createDefaultAction(), id: metadataId(fix.body) ?? createValidationDesignerId('action'), name, description: `Imported from EVL file ${fileName}.`, operations, annotations: { 'hydra.import.format': 'EVL', 'hydra.import.contextClassId': contextClassId, 'hydra.import.originalContext': contextName, ...(validationName === '' ? {} : { 'hydra.import.validationRuleName': validationName }) } };
                actions.push(action);
                if (validationName !== '') links.push({ actionId: action.id, validationRuleName: validationName });
            }
        }
    }
    if (actions.length === 0) warnings.push('No EVL fix blocks were recognized. Fixes are expected inside an EVL constraint or critique block.');
    return { actions, links, warnings, format: 'EVL' };
}

function importEolOperations(content: string, fileName: string, metamodel: MetaModel): ImportedQuickFixLayer {
    const warnings: string[] = [];
    const actions: ActionDefinition[] = [];
    const blocks = parseBalancedBlocks(content, /operation\s+[A-Za-z_][\w!:]*\s+[A-Za-z_][\w-]*\s*\([^)]*\)\s*\{/gi);
    for (const block of blocks) {
        const header = block.header.match(/operation\s+([A-Za-z_][\w!:]*)\s+([A-Za-z_][\w-]*)/i);
        if (header === null) continue;
        const metaClass = findMetaClass(metamodel, header[1]);
        const contextClassId = metaClass?.id ?? '';
        if (metaClass === undefined) warnings.push(`EOL operation ${header[2]}: context ${header[1]} was not found. The quick fix was preserved as an editable draft; unresolved operations must be mapped to the current metamodel.`);
        const exact = hydraOperations(block.body);
        const operations = exact.length > 0 ? exact : parseRepairStatements(block.body, metamodel, contextClassId, warnings, header[2]);
        const preceding = content.slice(Math.max(0, block.start - 500), block.start);
        actions.push({ ...createDefaultAction(), id: metadataId(preceding) ?? createValidationDesignerId('action'), name: header[2], description: `Imported from EOL file ${fileName}.`, operations, annotations: { 'hydra.import.format': 'EOL', 'hydra.import.contextClassId': contextClassId, 'hydra.import.originalContext': header[1] } });
    }
    if (actions.length === 0) warnings.push('No EOL context operations were recognized. Supported form: operation ClassName quickFixName() { ... }');
    return { actions, links: [], warnings, format: 'EOL' };
}

function freshAction(action: ActionDefinition): ActionDefinition {
    const copy = structuredClone(action);
    copy.id = copy.id === '' ? createValidationDesignerId('action') : copy.id;
    copy.operations = copy.operations.map(operation => ({ ...operation, operationId: operation.operationId === '' ? createValidationDesignerId('operation') : operation.operationId }));
    return copy;
}

function hydraOperations(body: string): ActionOperation[] {
    const operations: ActionOperation[] = [];
    for (const line of body.split(/\r?\n/)) {
        const match = line.match(/@hydra-operation\s+([^\s]+)/i);
        if (match === null) continue;
        try {
            const operation = JSON.parse(decodeURIComponent(match[1])) as ActionOperation;
            operations.push({ ...operation, operationId: operation.operationId === '' ? createValidationDesignerId('operation') : operation.operationId });
        } catch { /* human-readable statement parser below remains available */ }
    }
    return operations;
}

function metadataId(text: string): string | undefined { return text.match(/@hydra-action-id\s+([^\s]+)/i)?.[1]?.trim(); }

function parseRepairStatements(body: string, metamodel: MetaModel, contextClassId: string, warnings: string[], actionName: string): ActionOperation[] {
    const operations: ActionOperation[] = [];
    const statements = body.split(';').map(item => item.replace(/\/\/.*$/gm, '').trim()).filter(Boolean);
    for (const statement of statements) {
        let match = statement.match(/^self\.([A-Za-z_]\w*)\s*=\s*(.+)$/s);
        if (match !== null) {
            const target = findAttribute(metamodel, contextClassId, match[1]);
            if (target === undefined) { warnings.push(`${actionName}: attribute ${match[1]} was not found.`); continue; }
            const value = match[2].trim();
            if (/^(?:null|undefined)$/i.test(value)) operations.push({ kind: 'CLEAR_ATTRIBUTE', operationId: createValidationDesignerId('operation'), target: { kind: 'SELF' }, attributeId: target.id });
            else {
                const copy = value.match(/^self\.([A-Za-z_]\w*)$/);
                const source = copy === null ? undefined : findAttribute(metamodel, contextClassId, copy[1]);
                if (source !== undefined) operations.push({ kind: 'COPY_ATTRIBUTE', operationId: createValidationDesignerId('operation'), source: { kind: 'SELF' }, sourceAttributeId: source.id, target: { kind: 'SELF' }, targetAttributeId: target.id });
                else operations.push({ kind: 'SET_ATTRIBUTE', operationId: createValidationDesignerId('operation'), target: { kind: 'SELF' }, attributeId: target.id, value: literalSource(value) });
            }
            continue;
        }
        match = statement.match(/^self\.([A-Za-z_]\w*)\.(add|remove)\s*\(\s*selection\s*\)$/i);
        if (match !== null) {
            const relation = findRelation(metamodel, contextClassId, match[1]);
            if (relation === undefined) { warnings.push(`${actionName}: relation ${match[1]} was not found.`); continue; }
            operations.push({ kind: match[2].toLowerCase() === 'add' ? 'ADD_RELATION' : 'REMOVE_RELATION', operationId: createValidationDesignerId('operation'), relationId: relation.id, source: { kind: 'SELF' }, target: { kind: 'SELECTION' } });
            continue;
        }
        if (/^(?:delete\s+self|self\.delete\s*\(\s*\))$/i.test(statement)) { operations.push({ kind: 'DELETE_ELEMENT', operationId: createValidationDesignerId('operation'), target: { kind: 'SELF' } }); continue; }
        match = statement.match(/^(?:new\s+|var\s+\w+\s*=\s*new\s+)([A-Za-z_]\w*)/i);
        if (match !== null) {
            const metaClass = findMetaClass(metamodel, match[1]);
            if (metaClass === undefined) warnings.push(`${actionName}: class ${match[1]} was not found.`);
            else operations.push({ kind: 'CREATE_ELEMENT', operationId: createValidationDesignerId('operation'), classId: metaClass.id, resultName: 'createdElement' });
            continue;
        }
        warnings.push(`${actionName}: statement was retained only as source text because no HYDRA quick-fix operation matched: ${statement.slice(0, 100)}`);
    }
    return operations;
}

function literalSource(value: string): ActionValueSource {
    const trimmed = value.trim();
    if (/^'.*'$|^".*"$/s.test(trimmed)) return { literal: trimmed.slice(1, -1).replace(/''/g, "'") };
    if (/^(true|false)$/i.test(trimmed)) return { literal: trimmed.toLowerCase() === 'true' };
    if (/^-?\d+(?:\.\d+)?$/.test(trimmed)) return { literal: Number(trimmed) };
    return { literal: trimmed };
}

function extractField(body: string, field: string): string { return body.match(new RegExp(`\\b${field}\\s*:\\s*([^\\n]+)`, 'i'))?.[1]?.trim().replace(/;\s*$/, '') ?? ''; }
function unquote(value: string): string { const trimmed = value.trim(); return (/^'.*'$|^".*"$/s.test(trimmed)) ? trimmed.slice(1, -1).replace(/''/g, "'") : trimmed; }

export function exportQuickFixLayer(format: QuickFixInterchangeFormat, metamodel: MetaModel, model: ActionModel): LayerExportArtifact {
    if (format === 'HYDRA_JSON') return { fileName: `${metamodel.id}.quickfix.json`, mimeType: 'application/json', content: JSON.stringify(makeLayerInterchangeEnvelope('QUICK_FIX', metamodel.id, model, [model.id]), null, 2) };
    if (format === 'EVL') {
        const grouped = groupByContext(metamodel, model.actions);
        const content = [...grouped.entries()].map(([contextId, actions]) => {
            const hostGroups = new Map<string, ActionDefinition[]>();
            const fallbackHost = `HydraQuickFixHost_${sanitizeIdentifier(className(metamodel, contextId), 'Context')}`;
            for (const action of actions) {
                const host = action.annotations?.['hydra.import.validationRuleName'] || fallbackHost;
                hostGroups.set(host, [...(hostGroups.get(host) ?? []), action]);
            }
            const constraints = [...hostGroups.entries()].map(([host, fixes]) => `    constraint ${sanitizeIdentifier(host, fallbackHost)} {\n        check: true\n        message: 'HYDRA quick-fix host'\n${fixes.map(action => `        fix {\n            // @hydra-action-id ${action.id}\n            title: '${action.name.replace(/'/g, "''")}'\n            do {\n${action.operations.map(operation => `                // @hydra-operation ${encodeURIComponent(JSON.stringify(operation))}\n                ${operationAsEol(operation, metamodel)}`).join('\n')}\n            }\n        }`).join('\n')}\n    }`).join('\n\n');
            return `context ${className(metamodel, contextId)} {\n${constraints}\n}`;
        }).join('\n\n');
        return { fileName: `${metamodel.id}-quickfixes.evl`, mimeType: 'text/plain', content: `${content}\n` };
    }
    const content = model.actions.map(action => {
        const contextId = action.annotations?.['hydra.import.contextClassId'] ?? inferContextClass(metamodel, action);
        return `// @hydra-action-id ${action.id}\noperation ${className(metamodel, contextId)} ${sanitizeIdentifier(action.name, 'quickFix')}() {\n${action.operations.map(operation => `    // @hydra-operation ${encodeURIComponent(JSON.stringify(operation))}\n    ${operationAsEol(operation, metamodel)}`).join('\n')}\n}`;
    }).join('\n\n');
    return { fileName: `${metamodel.id}-quickfixes.eol`, mimeType: 'text/plain', content: `${content}\n` };
}

function groupByContext(metamodel: MetaModel, actions: ActionDefinition[]): Map<string, ActionDefinition[]> {
    const grouped = new Map<string, ActionDefinition[]>();
    for (const action of actions) {
        const context = action.annotations?.['hydra.import.contextClassId'] ?? inferContextClass(metamodel, action);
        grouped.set(context, [...(grouped.get(context) ?? []), action]);
    }
    return grouped;
}

function inferContextClass(metamodel: MetaModel, action: ActionDefinition): string {
    for (const operation of action.operations) {
        const attributeId = 'attributeId' in operation ? operation.attributeId : 'targetAttributeId' in operation ? operation.targetAttributeId : '';
        if (attributeId !== '') {
            const owner = metamodel.classes.find(metaClass => metaClass.attributes.some(attribute => attribute.id === attributeId));
            if (owner !== undefined) return owner.id;
        }
        if ('relationId' in operation) {
            const relation = metamodel.relations.find(item => item.id === operation.relationId);
            if (relation !== undefined) return relation.sourceTypeId;
        }
        if (operation.kind === 'CREATE_ELEMENT') return operation.classId;
    }
    return metamodel.classes[0]?.id ?? '';
}

function operationAsEol(operation: ActionOperation, metamodel: MetaModel): string {
    switch (operation.kind) {
        case 'SET_ATTRIBUTE': return `self.${attributeName(metamodel, operation.attributeId)} = ${valueAsEol(operation.value)};`;
        case 'CLEAR_ATTRIBUTE': return `self.${attributeName(metamodel, operation.attributeId)} = null;`;
        case 'COPY_ATTRIBUTE': return `self.${attributeName(metamodel, operation.targetAttributeId)} = self.${attributeName(metamodel, operation.sourceAttributeId)};`;
        case 'ADD_RELATION': return `self.${relationName(metamodel, operation.relationId)}.add(selection);`;
        case 'REMOVE_RELATION': return `self.${relationName(metamodel, operation.relationId)}.remove(selection);`;
        case 'CREATE_ELEMENT': return `var ${sanitizeIdentifier(operation.resultName, 'createdElement')} = new ${className(metamodel, operation.classId)};`;
        case 'DELETE_ELEMENT': return 'self.delete();';
        case 'TRANSFORM_TEXT': return `self.${attributeName(metamodel, operation.attributeId)} = self.${attributeName(metamodel, operation.attributeId)}.${operation.transformation.toLowerCase()}();`;
        case 'EXECUTE_ACTION': return `// HYDRA nested action: ${operation.actionId}`;
        case 'INVOKE_OPERATION': return `// HYDRA metamodel operation: ${operation.metamodelOperationId}`;
    }
}

function valueAsEol(value: ActionValueSource): string {
    if (value.expressionId !== undefined) return `/* expression:${value.expressionId} */ null`;
    if (value.parameterName !== undefined) return value.parameterName;
    if (typeof value.literal === 'string') return `'${value.literal.replace(/'/g, "''")}'`;
    if (value.literal === null || value.literal === undefined) return 'null';
    return String(value.literal);
}
