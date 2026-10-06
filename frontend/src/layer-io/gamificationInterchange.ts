import type { GamificationBindingModel, GamificationDefinitionModel } from '../gamification-designer/types';
import { makeBlocklyEnvelope, parseBlocklyLayerEnvelope, serializeBlocklyLayerJavaScript, serializeBlocklyLayerJson } from '../blockly/blocklyInterchange';
import type { LayerExportArtifact, LayerImportAnalysis, LayerInterchangeFormat } from './layerInterchangeTypes';
import { makeLayerInterchangeEnvelope, unwrapLayerInterchangeEnvelope } from './layerInterchangeEnvelope';

export type GamificationInterchangeFormat = 'HYDRA_JSON' | 'BLOCKLY_JSON' | 'BLOCKLY_JAVASCRIPT';

export const GAMIFICATION_INTERCHANGE_FORMATS: readonly LayerInterchangeFormat<GamificationInterchangeFormat>[] = [
    {
        id: 'HYDRA_JSON',
        label: 'HYDRA gamification JSON',
        description: 'Lossless HYDRA gamification catalog and evidence bindings. Use it for HYDRA-to-HYDRA exchange or archival.',
        extensions: ['.gamification.json', '.json'],
        mimeType: 'application/json'
    },
    {
        id: 'BLOCKLY_JSON',
        label: 'Blockly gamification layer (JSON)',
        description: 'Exports scoring, penalties, levels, achievements, challenges, rewards and evidence rules as an independent layer that a Blockly host can inject without replacing its block definitions.',
        extensions: ['.gamification.blockly.json', '.blockly.json'],
        mimeType: 'application/json'
    },
    {
        id: 'BLOCKLY_JAVASCRIPT',
        label: 'Blockly gamification layer (JavaScript · recommended for injection)',
        description: 'Recommended for direct Blockly injection: loading this independent JavaScript module registers the HYDRA gamification layer and notifies any installed Blockly adapter; imported JavaScript is parsed but never executed by HYDRA.',
        extensions: ['.gamification.blockly.js', '.blockly.js', '.js'],
        mimeType: 'text/javascript'
    }
];

export interface ImportedGamificationLayer {
    definitions: GamificationDefinitionModel;
    bindings: GamificationBindingModel;
    warnings: string[];
    format: GamificationInterchangeFormat;
}

interface GamificationPayload {
    definitions: GamificationDefinitionModel;
    bindings: GamificationBindingModel;
}

export function analyzeGamificationImport(content: string, format: GamificationInterchangeFormat, current: GamificationPayload): LayerImportAnalysis {
    const imported = importGamificationLayer(content, format, current);
    const invalidRules = imported.bindings.rules.filter(rule => rule.id.trim() === '' || rule.name.trim() === '' || rule.eventTypeId.trim() === '');
    const definitionCount = imported.definitions.pointTypes.length + imported.definitions.levels.length + imported.definitions.achievements.length + imported.definitions.challenges.length + imported.definitions.rewards.length;
    const rewardLike = imported.definitions.rewards.length + imported.definitions.achievements.length;
    return {
        summary: definitionCount === 0 && imported.bindings.rules.length === 0 ? 'No gamification definition can be imported from this source.' : `${definitionCount} gamification definition(s) and ${imported.bindings.rules.length} evidence rule(s) identified.`,
        canImport: definitionCount > 0 || imported.bindings.rules.length > 0,
        metrics: [
            { id: 'definitions', label: 'Gamification definitions', value: definitionCount, tone: definitionCount > 0 ? 'GOOD' : 'NEUTRAL' },
            { id: 'points', label: 'Point types', value: imported.definitions.pointTypes.length, tone: 'NEUTRAL' },
            { id: 'rewards', label: 'Rewards / achievements', value: rewardLike, tone: 'NEUTRAL' },
            { id: 'rules', label: 'Evidence rules', value: imported.bindings.rules.length, tone: imported.bindings.rules.length > 0 ? 'GOOD' : 'NEUTRAL' },
            { id: 'invalid-rules', label: 'Erroneous evidence rules', value: invalidRules.length, tone: invalidRules.length > 0 ? 'ERROR' : 'GOOD' },
            { id: 'warnings', label: 'Warnings', value: imported.warnings.length, tone: imported.warnings.length > 0 ? 'WARNING' : 'GOOD' }
        ],
        diagnostics: [
            ...invalidRules.map(rule => ({ severity: 'ERROR' as const, category: 'INVALID_EVIDENCE_RULE', itemId: rule.id || rule.name, message: `${rule.name || 'Unnamed evidence rule'}: id, name and source event are required.` })),
            ...imported.warnings.map(message => ({ severity: 'WARNING' as const, category: 'IMPORT_WARNING', message }))
        ],
        warnings: imported.warnings
    };
}

export function importGamificationLayer(content: string, format: GamificationInterchangeFormat, current: GamificationPayload): ImportedGamificationLayer {
    if (format === 'HYDRA_JSON') {
        const envelope = unwrapLayerInterchangeEnvelope<Partial<GamificationPayload>>(content, 'GAMIFICATION', current.definitions.metamodelId);
        const parsed = envelope.payload;
        if (parsed.definitions === undefined || parsed.bindings === undefined) throw new Error('HYDRA gamification JSON must contain definitions and bindings.');
        return {
            definitions: withFormat(structuredClone(parsed.definitions), format),
            bindings: withBindingFormat(structuredClone(parsed.bindings), format),
            warnings: envelope.warnings,
            format
        };
    }

    const envelope = parseBlocklyLayerEnvelope<GamificationPayload>(content);
    if (envelope === null || envelope.layer !== 'GAMIFICATION') {
        return {
            definitions: { ...current.definitions, pointTypes: [], levels: [], achievements: [], challenges: [], rewards: [], annotations: { ...current.definitions.annotations, 'hydra.interchange.format': format } },
            bindings: { ...current.bindings, rules: [], annotations: { ...current.bindings.annotations, 'hydra.interchange.format': format } },
            warnings: ['This Blockly/JavaScript file does not contain a HYDRA GAMIFICATION layer. HYDRA never executes imported JavaScript; arbitrary block code is therefore not interpreted as scoring logic.'],
            format
        };
    }
    const payload = envelope.payload;
    if (payload?.definitions === undefined || payload?.bindings === undefined) throw new Error('The Blockly gamification layer is missing its HYDRA definitions or bindings payload.');
    const warnings = envelope.metamodelId !== current.definitions.metamodelId
        ? [`The gamification layer was authored for metamodel ${envelope.metamodelId}; review event evidence and semantic references before saving.`]
        : [];
    return {
        definitions: withFormat(structuredClone(payload.definitions), format),
        bindings: withBindingFormat(structuredClone(payload.bindings), format),
        warnings,
        format
    };
}

export function exportGamificationLayer(format: GamificationInterchangeFormat, definitions: GamificationDefinitionModel, bindings: GamificationBindingModel): LayerExportArtifact {
    const payload: GamificationPayload = { definitions, bindings };
    if (format === 'HYDRA_JSON') {
        return {
            fileName: `${definitions.metamodelId}.gamification.json`,
            mimeType: 'application/json',
            content: JSON.stringify(makeLayerInterchangeEnvelope('GAMIFICATION', definitions.metamodelId, payload, [definitions.id, bindings.id]), null, 2)
        };
    }
    const envelope = makeBlocklyEnvelope('GAMIFICATION', definitions.metamodelId, payload);
    if (format === 'BLOCKLY_JSON') {
        return {
            fileName: `${definitions.metamodelId}.gamification.blockly.json`,
            mimeType: 'application/json',
            content: serializeBlocklyLayerJson(envelope)
        };
    }
    return {
        fileName: `${definitions.metamodelId}.gamification.blockly.js`,
        mimeType: 'text/javascript',
        content: serializeBlocklyLayerJavaScript(envelope)
    };
}

function withFormat(model: GamificationDefinitionModel, format: GamificationInterchangeFormat): GamificationDefinitionModel {
    return { ...model, annotations: { ...(model.annotations ?? {}), 'hydra.interchange.format': format } };
}

function withBindingFormat(model: GamificationBindingModel, format: GamificationInterchangeFormat): GamificationBindingModel {
    return { ...model, annotations: { ...(model.annotations ?? {}), 'hydra.interchange.format': format } };
}
