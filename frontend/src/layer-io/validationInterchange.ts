import type { MetaModel } from '../metamodel-designer/types';
import type { ExpressionDefinition, ExpressionModel, ImportedValidationSource, ValidationModel, ValidationRule, ValidationRuleSet, ValidationSeverity } from '../validation/types';
import { createDefaultRuleSet, createUniqueRuleCode, createValidationDesignerId } from '../validation-designer/validationDesignerFactory';
import { inferExpressionDependencies } from '../validation-designer/validationDesignerSelectors';
import { className, externalExpression, findMetaClass, parseBalancedBlocks, parseExternalExpression, sanitizeIdentifier, stableTextHash } from './interchangeSupport';
import type { LayerExportArtifact, LayerImportAnalysis, LayerInterchangeFormat } from './layerInterchangeTypes';
import { makeLayerInterchangeEnvelope, unwrapLayerInterchangeEnvelope } from './layerInterchangeEnvelope';
import { blockConnectionChecks, makeBlocklyEnvelope, parseBlocklyBlockDefinitions, parseBlocklyLayerEnvelope, serializeBlocklyLayerJavaScript, serializeBlocklyLayerJson } from '../blockly/blocklyInterchange';

export type ValidationInterchangeFormat = 'HYDRA_JSON' | 'OCL' | 'EVL' | 'ATL' | 'BLOCKLY_JSON' | 'BLOCKLY_JAVASCRIPT';
export const VALIDATION_INTERCHANGE_FORMATS: readonly LayerInterchangeFormat<ValidationInterchangeFormat>[] = [
    { id: 'HYDRA_JSON', label: 'HYDRA validation JSON', description: 'Lossless HYDRA validation + expression document for backups and HYDRA-to-HYDRA exchange.', extensions: ['.validation.json', '.json'], mimeType: 'application/json' },
    { id: 'OCL', label: 'OCL constraints', description: 'Imports/exports class invariants and maps common OCL expressions to editable HYDRA expression nodes.', extensions: ['.ocl'], mimeType: 'text/plain' },
    { id: 'EVL', label: 'Epsilon Validation Language (EVL)', description: 'Imports/exports EVL constraint/critique blocks, messages and supported check expressions.', extensions: ['.evl'], mimeType: 'text/plain' },
    { id: 'ATL', label: 'ATL Boolean helpers', description: 'Imports/exports context Boolean helpers as validation rules. HYDRA metadata comments preserve diagnostics not native to ATL helpers.', extensions: ['.atl'], mimeType: 'text/plain' },
    { id: 'BLOCKLY_JSON', label: 'Blockly validation layer (JSON)', description: 'Imports HYDRA validation layers or static Blockly block connection checks. Exports validations as an independent layer that can be injected into an existing Blockly editor.', extensions: ['.validation.blockly.json', '.blockly.json', '.blocks.json'], mimeType: 'application/json' },
    { id: 'BLOCKLY_JAVASCRIPT', label: 'Blockly validation layer (JavaScript · recommended for injection)', description: 'Recommended for direct Blockly injection: reads static Blockly declarations without executing JavaScript and exports an independent validation registration module for an existing Blockly editor.', extensions: ['.validation.blockly.js', '.blockly.js', '.js'], mimeType: 'text/javascript' }
];

export interface ImportedValidationLayer {
    expressions: ExpressionDefinition[];
    rules: ValidationRule[];
    ruleSets: ValidationRuleSet[];
    source: ImportedValidationSource;
    warnings: string[];
    format: ValidationInterchangeFormat;
}

export function detectValidationFormat(fileName: string): ValidationInterchangeFormat {
    const lower = fileName.toLowerCase();
    if (lower.endsWith('.ocl')) return 'OCL';
    if (lower.endsWith('.evl')) return 'EVL';
    if (lower.endsWith('.atl')) return 'ATL';
    if (lower.endsWith('.blockly.js') || lower.endsWith('.validation.blockly.js')) return 'BLOCKLY_JAVASCRIPT';
    if (lower.endsWith('.blockly.json') || lower.endsWith('.blocks.json') || lower.endsWith('.validation.blockly.json')) return 'BLOCKLY_JSON';
    return 'HYDRA_JSON';
}

export function analyzeValidationImport(content: string, fileName: string, format: ValidationInterchangeFormat, metamodel: MetaModel, current: { expressions: ExpressionModel; validations: ValidationModel }): LayerImportAnalysis {
    const imported = importValidationLayer(content, fileName, format, metamodel, current);
    const partial = imported.rules.filter(rule => rule.conversionStatus !== 'FULL');
    const unsupported = imported.rules.filter(rule => rule.conversionStatus === 'UNSUPPORTED');
    const unresolved = imported.rules.filter(rule => rule.context.kind === 'CLASS' && (rule.context.classId ?? '').trim() === '');
    const diagnostics = [
        ...partial.map(rule => ({ severity: rule.conversionStatus === 'UNSUPPORTED' ? 'ERROR' as const : 'WARNING' as const, category: 'VALIDATION_CONVERSION', itemId: rule.id, message: `${rule.name}: ${rule.conversionStatus === 'UNSUPPORTED' ? 'the validation could not be converted to an editable HYDRA rule.' : 'the validation was only partially converted and should be reviewed.'}` })),
        ...unresolved.map(rule => ({ severity: 'WARNING' as const, category: 'UNRESOLVED_CONTEXT', itemId: rule.id, message: `${rule.name}: its context could not be resolved against the selected metamodel.` })),
        ...imported.warnings.map(message => ({ severity: 'WARNING' as const, category: 'IMPORT_WARNING', message }))
    ];
    return {
        summary: imported.rules.length === 0 ? 'No validation rule can be imported from this source.' : `${imported.rules.length} validation rule(s) identified; ${partial.length} require review.`,
        canImport: imported.rules.length > 0,
        metrics: [
            { id: 'identified', label: 'Validations identified', value: imported.rules.length, tone: imported.rules.length > 0 ? 'GOOD' : 'ERROR' },
            { id: 'full', label: 'Fully converted', value: imported.rules.filter(rule => rule.conversionStatus === 'FULL').length, tone: 'GOOD' },
            { id: 'partial', label: 'Erroneous / partial validations', value: partial.length, tone: partial.length > 0 ? 'WARNING' : 'GOOD' },
            { id: 'unresolved-context', label: 'Unresolved contexts', value: unresolved.length, tone: unresolved.length > 0 ? 'WARNING' : 'GOOD' },
            { id: 'unsupported', label: 'Unsupported rules', value: unsupported.length, tone: unsupported.length > 0 ? 'ERROR' : 'GOOD' },
            { id: 'expressions', label: 'Expressions recovered', value: imported.expressions.length, tone: 'NEUTRAL' },
            { id: 'warnings', label: 'Warnings', value: imported.warnings.length, tone: imported.warnings.length > 0 ? 'WARNING' : 'GOOD' }
        ],
        diagnostics,
        warnings: imported.warnings
    };
}

export function importValidationLayer(content: string, fileName: string, format: ValidationInterchangeFormat, metamodel: MetaModel, current: { expressions: ExpressionModel; validations: ValidationModel }): ImportedValidationLayer {
    if (format === 'HYDRA_JSON') return importHydraJson(content, fileName, metamodel);
    if (format === 'OCL') return importOcl(content, fileName, metamodel, current);
    if (format === 'EVL') return importEvl(content, fileName, metamodel, current);
    if (format === 'ATL') return importAtl(content, fileName, metamodel, current);
    return importBlocklyValidation(content, fileName, format, metamodel);
}

function importHydraJson(content: string, fileName: string, metamodel: MetaModel): ImportedValidationLayer {
    type Payload = { expressionModel?: ExpressionModel; validationModel?: ValidationModel; expressions?: ExpressionDefinition[]; rules?: ValidationRule[]; ruleSets?: ValidationRuleSet[] };
    const envelope = unwrapLayerInterchangeEnvelope<Payload>(content, 'VALIDATION', metamodel.id);
    const parsed = envelope.payload;
    const expressions = parsed.expressionModel?.expressions ?? parsed.expressions ?? [];
    const rules = parsed.validationModel?.rules ?? parsed.rules ?? [];
    const ruleSets = parsed.validationModel?.ruleSets ?? parsed.ruleSets ?? [];
    const warnings: string[] = [...envelope.warnings];
    const validClassIds = new Set(metamodel.classes.map(item => item.id));
    const adjustedRules = rules.map(rule => {
        if (rule.context.kind === 'CLASS' && rule.context.classId !== undefined && !validClassIds.has(rule.context.classId)) warnings.push(`Rule ${rule.name}: class ${rule.context.classId} does not exist in the current metamodel.`);
        return structuredClone(rule);
    });
    return { expressions: expressions.map(expression => structuredClone(expression)), rules: adjustedRules, ruleSets: normalizeCanonicalRuleSets(ruleSets, adjustedRules), source: sourceRecord(fileName, 'HYDRA_JSON', content, warnings.length === 0 ? 'FULL' : 'PARTIAL'), warnings, format: 'HYDRA_JSON' };
}

function importOcl(content: string, fileName: string, metamodel: MetaModel, current: { expressions: ExpressionModel; validations: ValidationModel }): ImportedValidationLayer {
    const warnings: string[] = [];
    const expressions: ExpressionDefinition[] = [];
    const rules: ValidationRule[] = [];
    const existingCodes = current.validations.rules.map(item => item.code);
    const pattern = /context\s+([A-Za-z_][\w!:]*)\s+(?:inv(?:\s+([A-Za-z_][\w-]*))?\s*:)([\s\S]*?)(?=\n\s*context\b|\n\s*--\s*@hydra-|$)/gi;
    let match: RegExpExecArray | null;
    while ((match = pattern.exec(content)) !== null) {
        const metaClass = findMetaClass(metamodel, match[1]);
        const ruleName = match[2] ?? `Invariant_${rules.length + 1}`;
        const contextClassId = metaClass?.id ?? '';
        if (metaClass === undefined) warnings.push(`OCL invariant ${ruleName}: context ${match[1]} was not found in the current metamodel. The rule was imported as an unresolved editable draft instead of being discarded.`);
        const rawExpression = match[3].trim().replace(/;\s*$/, '');
        const parsed = parseExternalExpression(rawExpression, metamodel, contextClassId);
        warnings.push(...parsed.warnings.map(item => `${ruleName}: ${item}`));
        const metadata = precedingMetadata(content, match.index);
        const expressionId = metadata.expressionId ?? createValidationDesignerId('expression');
        const expression: ExpressionDefinition = { id: expressionId, name: `${ruleName} condition`, description: `Imported from ${fileName}`, contextClassId, declaredReturnType: { kind: 'BOOLEAN' }, root: parsed.node, annotations: { 'hydra.import.format': 'OCL', 'hydra.import.originalExpression': rawExpression } };
        expressions.push(expression);
        rules.push(makeRule({ id: metadata.id, name: ruleName, contextClassId, expression, message: metadata.message ?? `OCL invariant ${ruleName} is not satisfied.`, severity: metadata.severity ?? 'ERROR', format: 'OCL', fileName, originalFragment: match[0], fullyConverted: metaClass !== undefined && parsed.complete, existingCodes: [...existingCodes, ...rules.map(item => item.code)] }));
    }
    if (rules.length === 0) warnings.push('No OCL class invariants were recognized. Supported form: context Class inv RuleName: expression.');
    return importedLayer(expressions, rules, fileName, 'OCL', content, warnings);
}

function importEvl(content: string, fileName: string, metamodel: MetaModel, current: { expressions: ExpressionModel; validations: ValidationModel }): ImportedValidationLayer {
    const warnings: string[] = [];
    const expressions: ExpressionDefinition[] = [];
    const rules: ValidationRule[] = [];
    const contexts = parseBalancedBlocks(content, /context\s+[A-Za-z_][\w!:]*\s*\{/gi);
    for (const context of contexts) {
        const contextName = context.header.match(/context\s+([A-Za-z_][\w!:]*)/i)?.[1] ?? '';
        const metaClass = findMetaClass(metamodel, contextName);
        const contextClassId = metaClass?.id ?? '';
        if (metaClass === undefined) warnings.push(`EVL context ${contextName} was not found in the current metamodel. Its rules were imported as unresolved editable drafts.`);
        const blocks = parseBalancedBlocks(context.body, /(?:constraint|critique)\s+[A-Za-z_][\w-]*\s*\{/gi);
        for (const block of blocks) {
            const header = block.header.match(/(constraint|critique)\s+([A-Za-z_][\w-]*)/i);
            if (header === null) continue;
            const kind = header[1].toLowerCase();
            const name = header[2];
            const check = fieldExpression(block.body, 'check');
            const parsed = check === ''
                ? { node: { kind: 'LITERAL' as const, nodeId: createValidationDesignerId('node'), valueType: { kind: 'BOOLEAN' as const }, value: true }, complete: false, warnings: ['No check expression was recognized; the constraint was preserved as an editable partial draft.'] }
                : parseExternalExpression(check, metamodel, contextClassId);
            warnings.push(...parsed.warnings.map(item => `${name}: ${item}`));
            const metadata = metadataFromText(block.body);
            const expression: ExpressionDefinition = { id: metadata.expressionId ?? createValidationDesignerId('expression'), name: `${name} condition`, description: `Imported from ${fileName}`, contextClassId, declaredReturnType: { kind: 'BOOLEAN' }, root: parsed.node, annotations: { 'hydra.import.format': 'EVL', 'hydra.import.originalExpression': check, ...(check === '' ? { 'hydra.import.requiresReview': 'true' } : {}) } };
            expressions.push(expression);
            const message = unquoteString(fieldExpression(block.body, 'message')) || metadata.message || `EVL ${kind} ${name} is not satisfied.`;
            rules.push(makeRule({ id: metadata.id, name, contextClassId, expression, message, severity: metadata.severity ?? (kind === 'critique' ? 'WARNING' : 'ERROR'), format: 'EVL', fileName, originalFragment: block.header + ' {' + block.body + '}', fullyConverted: metaClass !== undefined && parsed.complete, existingCodes: [...current.validations.rules.map(item => item.code), ...rules.map(item => item.code)] }));
        }
    }
    if (rules.length === 0) warnings.push('No EVL constraint or critique blocks were recognized.');
    return importedLayer(expressions, rules, fileName, 'EVL', content, warnings);
}

function importAtl(content: string, fileName: string, metamodel: MetaModel, current: { expressions: ExpressionModel; validations: ValidationModel }): ImportedValidationLayer {
    const warnings: string[] = [];
    const expressions: ExpressionDefinition[] = [];
    const rules: ValidationRule[] = [];
    const pattern = /helper\s+context\s+([A-Za-z_][\w!:]*)\s+def\s*:\s*([A-Za-z_][\w-]*)\s*:\s*Boolean\s*=\s*([\s\S]*?);/gi;
    let match: RegExpExecArray | null;
    while ((match = pattern.exec(content)) !== null) {
        const metaClass = findMetaClass(metamodel, match[1]);
        const contextClassId = metaClass?.id ?? '';
        if (metaClass === undefined) warnings.push(`ATL helper ${match[2]}: context ${match[1]} was not found. The helper was imported as an unresolved editable draft.`);
        const parsed = parseExternalExpression(match[3].trim(), metamodel, contextClassId);
        warnings.push(...parsed.warnings.map(item => `${match?.[2] ?? 'ATL helper'}: ${item}`));
        const metadata = precedingMetadata(content, match.index);
        const expression: ExpressionDefinition = { id: metadata.expressionId ?? createValidationDesignerId('expression'), name: `${match[2]} condition`, description: `Imported ATL Boolean helper from ${fileName}`, contextClassId, declaredReturnType: { kind: 'BOOLEAN' }, root: parsed.node, annotations: { 'hydra.import.format': 'ATL', 'hydra.import.originalExpression': match[3].trim() } };
        expressions.push(expression);
        rules.push(makeRule({ id: metadata.id, name: match[2], contextClassId, expression, message: metadata.message ?? `ATL helper ${match[2]} returned false.`, severity: metadata.severity ?? 'ERROR', format: 'ATL', fileName, originalFragment: match[0], fullyConverted: metaClass !== undefined && parsed.complete, existingCodes: [...current.validations.rules.map(item => item.code), ...rules.map(item => item.code)] }));
    }
    if (rules.length === 0) warnings.push('No ATL Boolean context helpers were recognized. Supported form: helper context MM!Class def: name : Boolean = expression;');
    return importedLayer(expressions, rules, fileName, 'ATL', content, warnings);
}

function importBlocklyValidation(content: string, fileName: string, format: 'BLOCKLY_JSON' | 'BLOCKLY_JAVASCRIPT', metamodel: MetaModel): ImportedValidationLayer {
    const envelope = parseBlocklyLayerEnvelope<{ expressionModel?: ExpressionModel; validationModel?: ValidationModel }>(content);
    if (envelope !== null && envelope.layer === 'VALIDATIONS' && envelope.payload.validationModel !== undefined) {
        const validationModel = envelope.payload.validationModel;
        const expressionModel = envelope.payload.expressionModel;
        const warnings = envelope.metamodelId !== '' && envelope.metamodelId !== metamodel.id ? [`The Blockly validation layer targets metamodel ${envelope.metamodelId}; current metamodel is ${metamodel.id}. Definitions remain editable so references can be remapped.`] : [];
        return { expressions: expressionModel?.expressions.map(item => structuredClone(item)) ?? [], rules: validationModel.rules.map(item => structuredClone(item)), ruleSets: validationModel.ruleSets.map(item => structuredClone(item)), source: sourceRecord(fileName, format, content, warnings.length === 0 ? 'FULL' : 'PARTIAL'), warnings, format };
    }
    const parsed = parseBlocklyBlockDefinitions(content);
    const expressions: ExpressionDefinition[] = [];
    const rules: ValidationRule[] = [];
    const warnings = [...parsed.warnings];
    for (const block of parsed.definitions) {
        const checks = blockConnectionChecks(block);
        if (checks.length === 0) continue;
        const metaClass = findMetaClass(metamodel, block.type);
        if (metaClass === undefined) warnings.push(`Blockly block ${block.type}: no matching metamodel class was found. Connection checks were imported as an unresolved editable draft.`);
        const expression: ExpressionDefinition = { id: createValidationDesignerId('expression'), name: `${block.type} Blockly connection compatibility`, description: `Connection checks imported from ${fileName}.`, contextClassId: metaClass?.id ?? '', declaredReturnType: { kind: 'BOOLEAN' }, root: { kind: 'LITERAL', nodeId: createValidationDesignerId('node'), valueType: { kind: 'BOOLEAN' }, value: true }, annotations: { 'hydra.import.format': format, 'hydra.blockly.blockType': block.type, 'hydra.blockly.connectionChecks': JSON.stringify(checks) } };
        expressions.push(expression);
        rules.push({ id: createValidationDesignerId('rule'), name: `${block.type} connection compatibility`, code: createUniqueRuleCode(`BLOCKLY_${sanitizeIdentifier(block.type, 'BLOCK').toUpperCase()}_CONNECTION`, rules.map(item => item.code)), description: `Imported Blockly connection checks: ${checks.join(', ')}`, enabled: true, context: { kind: 'CLASS', classId: metaClass?.id ?? '' }, severity: 'ERROR', conditionExpressionId: expression.id, messageTemplate: `Blockly connection for ${block.type} must satisfy: ${checks.join(', ')}`, triggers: ['MANUAL', 'ON_SAVE'], dependencies: [], quickFixIds: [], executionMode: 'EXTERNAL', authoritativeRepresentation: format === 'BLOCKLY_JAVASCRIPT' ? 'JAVASCRIPT' : 'BLOCKLY', conversionStatus: 'PARTIAL', provenance: { technology: 'Blockly', sourceFile: fileName, sourceRule: block.type, sourceHash: stableTextHash(JSON.stringify(block)), importedAt: new Date().toISOString(), originalFragment: JSON.stringify(block) }, annotations: { 'hydra.import.format': format, 'hydra.blockly.blockType': block.type, 'hydra.blockly.connectionChecks': JSON.stringify(checks), 'hydra.blockly.externalRuntime': 'workspace-connection-check' } });
    }
    if (rules.length === 0) warnings.push('No Blockly connection checks were found. HYDRA recognizes input_value/input_statement checks plus output, previousStatement and nextStatement compatibility declarations.');
    return importedLayer(expressions, rules, fileName, format, content, warnings);
}

function importedLayer(expressions: ExpressionDefinition[], rules: ValidationRule[], fileName: string, format: ValidationInterchangeFormat, content: string, warnings: string[]): ImportedValidationLayer {
    const set = createDefaultRuleSet(0);
    const ruleSets = rules.length === 0 ? [] : [{ ...set, id: createValidationDesignerId('rule-set'), name: `${format} import · ${fileName}`, ruleIds: rules.map(item => item.id) }];
    return { expressions, rules, ruleSets, source: sourceRecord(fileName, format, content, warnings.length === 0 ? 'FULL' : 'PARTIAL'), warnings, format };
}

function makeRule(input: { id?: string; name: string; contextClassId: string; expression: ExpressionDefinition; message: string; severity: ValidationSeverity; format: 'OCL' | 'EVL' | 'ATL'; fileName: string; originalFragment: string; fullyConverted: boolean; existingCodes: string[] }): ValidationRule {
    return {
        id: input.id ?? createValidationDesignerId('rule'),
        name: input.name,
        code: createUniqueRuleCode(sanitizeIdentifier(input.name, 'IMPORTED_VALIDATION').toUpperCase(), input.existingCodes),
        description: `Imported from ${input.format} file ${input.fileName}.`,
        enabled: true,
        context: { kind: 'CLASS', classId: input.contextClassId },
        severity: input.severity,
        conditionExpressionId: input.expression.id,
        messageTemplate: input.message,
        triggers: ['ON_SAVE', 'MANUAL'],
        dependencies: inferExpressionDependencies(input.expression),
        quickFixIds: [],
        executionMode: 'INCREMENTAL',
        authoritativeRepresentation: input.format,
        conversionStatus: input.fullyConverted ? 'FULL' : 'PARTIAL',
        provenance: { technology: input.format, sourceFile: input.fileName, sourceRule: input.name, sourceHash: stableTextHash(input.originalFragment), importedAt: new Date().toISOString(), originalFragment: input.originalFragment },
        annotations: { 'hydra.import.format': input.format }
    };
}

function sourceRecord(fileName: string, format: string, content: string, status: 'FULL' | 'PARTIAL'): ImportedValidationSource {
    return { id: createValidationDesignerId('validation-source'), technology: format, fileName, contentHash: stableTextHash(content), conversionStatus: status, importedAt: new Date().toISOString(), annotations: { 'hydra.interchange.format': format } };
}

function fieldExpression(body: string, field: string): string {
    const match = body.match(new RegExp(`\\b${field}\\s*:\\s*([\\s\\S]*?)(?=\\n\\s*(?:message|check|guard|fix|do|title)\\s*:|\\n\\s*(?:fix\\b)|$)`, 'i'));
    return match?.[1]?.trim().replace(/;\s*$/, '') ?? '';
}

function unquoteString(value: string): string {
    const trimmed = value.trim().replace(/;\s*$/, '');
    if ((trimmed.startsWith("'") && trimmed.endsWith("'")) || (trimmed.startsWith('"') && trimmed.endsWith('"'))) return trimmed.slice(1, -1).replace(/''/g, "'");
    return trimmed;
}

interface ValidationMetadata { id?: string; expressionId?: string; message?: string; severity?: ValidationSeverity; }
function metadataFromText(text: string): ValidationMetadata {
    const metadata: ValidationMetadata = {};
    for (const line of text.split(/\r?\n/)) {
        const idMatch = line.match(/@hydra-id\s+([^\s]+)/i); if (idMatch !== null) metadata.id = idMatch[1].trim();
        const expressionMatch = line.match(/@hydra-expression-id\s+([^\s]+)/i); if (expressionMatch !== null) metadata.expressionId = expressionMatch[1].trim();
        const messageMatch = line.match(/@hydra-message\s+(.+)$/i); if (messageMatch !== null) metadata.message = messageMatch[1].trim();
        const severityMatch = line.match(/@hydra-severity\s+(ERROR|WARNING|INFO)/i); if (severityMatch !== null) metadata.severity = severityMatch[1].toUpperCase() as ValidationSeverity;
    }
    return metadata;
}
function precedingMetadata(content: string, index: number): ValidationMetadata {
    const start = Math.max(0, content.lastIndexOf('\n', Math.max(0, index - 800)));
    return metadataFromText(content.slice(start, index).split(/\r?\n/).slice(-8).join('\n'));
}

function normalizeCanonicalRuleSets(ruleSets: ValidationRuleSet[], rules: ValidationRule[]): ValidationRuleSet[] {
    if (ruleSets.length === 0 && rules.length > 0) return [{ ...createDefaultRuleSet(0), id: createValidationDesignerId('rule-set'), name: 'Imported validations', ruleIds: rules.map(item => item.id) }];
    const ids = new Set(rules.map(item => item.id));
    return ruleSets.map(item => ({ ...structuredClone(item), ruleIds: item.ruleIds.filter(id => ids.has(id)) }));
}

export function exportValidationLayer(format: ValidationInterchangeFormat, metamodel: MetaModel, expressions: ExpressionModel, validations: ValidationModel): LayerExportArtifact {
    if (format === 'BLOCKLY_JSON' || format === 'BLOCKLY_JAVASCRIPT') {
        const envelope = makeBlocklyEnvelope('VALIDATIONS', metamodel.id, { expressionModel: expressions, validationModel: validations });
        return format === 'BLOCKLY_JSON'
            ? { fileName: `${metamodel.id}.validation.blockly.json`, mimeType: 'application/json', content: serializeBlocklyLayerJson(envelope) }
            : { fileName: `${metamodel.id}.validation.blockly.js`, mimeType: 'text/javascript', content: serializeBlocklyLayerJavaScript(envelope) };
    }
    if (format === 'HYDRA_JSON') return { fileName: `${metamodel.id}.validation.json`, mimeType: 'application/json', content: JSON.stringify(makeLayerInterchangeEnvelope('VALIDATION', metamodel.id, { expressionModel: expressions, validationModel: validations }, [expressions.id, validations.id]), null, 2) };
    const expressionById = new Map(expressions.expressions.map(item => [item.id, item]));
    if (format === 'OCL') {
        const body = validations.rules.map(rule => {
            const expression = expressionById.get(rule.conditionExpressionId);
            if (expression === undefined) return `-- HYDRA skipped ${rule.code}: missing expression ${rule.conditionExpressionId}`;
            return [`-- @hydra-id ${rule.id}`, `-- @hydra-expression-id ${expression.id}`, `-- @hydra-message ${rule.messageTemplate}`, `-- @hydra-severity ${rule.severity}`, `context ${className(metamodel, rule.context.classId)} inv ${sanitizeIdentifier(rule.name, rule.code)}:`, `    ${externalExpression(expression.root, metamodel, 'OCL')}`].join('\n');
        }).join('\n\n');
        return { fileName: `${metamodel.id}.ocl`, mimeType: 'text/plain', content: `${body}\n` };
    }
    if (format === 'EVL') {
        const grouped = new Map<string, ValidationRule[]>();
        for (const rule of validations.rules) { const key = rule.context.classId ?? ''; grouped.set(key, [...(grouped.get(key) ?? []), rule]); }
        const contexts = [...grouped.entries()].map(([classId, rules]) => `context ${className(metamodel, classId)} {\n${rules.map(rule => {
            const expression = expressionById.get(rule.conditionExpressionId);
            const kind = rule.severity === 'WARNING' || rule.severity === 'INFO' ? 'critique' : 'constraint';
            return `    ${kind} ${sanitizeIdentifier(rule.name, rule.code)} {\n        check: ${expression === undefined ? 'true' : externalExpression(expression.root, metamodel, 'EVL')}\n        message: '${rule.messageTemplate.replace(/'/g, "''")}'\n        // @hydra-id ${rule.id}\n        // @hydra-expression-id ${expression?.id ?? rule.conditionExpressionId}\n        // @hydra-severity ${rule.severity}\n    }`;
        }).join('\n\n')}\n}`).join('\n\n');
        return { fileName: `${metamodel.id}.evl`, mimeType: 'text/plain', content: `${contexts}\n` };
    }
    const helpers = validations.rules.map(rule => {
        const expression = expressionById.get(rule.conditionExpressionId);
        return [`-- @hydra-id ${rule.id}`, `-- @hydra-expression-id ${expression?.id ?? rule.conditionExpressionId}`, `-- @hydra-message ${rule.messageTemplate}`, `-- @hydra-severity ${rule.severity}`, `helper context MM!${className(metamodel, rule.context.classId)} def: ${sanitizeIdentifier(rule.name, rule.code)} : Boolean =`, `    ${expression === undefined ? 'true' : externalExpression(expression.root, metamodel, 'ATL')};`].join('\n');
    }).join('\n\n');
    return { fileName: `${metamodel.id}.atl`, mimeType: 'text/plain', content: `-- Generated by HYDRA. Add the corresponding ATL module header/model declarations when embedding these helpers in a transformation.\n\n${helpers}\n` };
}
