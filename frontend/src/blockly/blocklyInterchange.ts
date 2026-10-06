export type HydraBlocklyLayerKind = 'METAMODEL' | 'VALIDATIONS' | 'EVENTS' | 'EVENT_FLOW' | 'GAMIFICATION';

export interface HydraBlocklyLayerEnvelope<TPayload = unknown> {
    schemaVersion: '1.0';
    kind: 'HYDRA_BLOCKLY_LAYER';
    layer: HydraBlocklyLayerKind;
    metamodelId: string;
    generatedAt: string;
    payload: TPayload;
    blockDefinitions?: unknown[];
    annotations: Record<string, string>;
}

export interface BlocklyBlockDefinition {
    type: string;
    message0?: string;
    args0?: Array<Record<string, unknown>>;
    output?: unknown;
    previousStatement?: unknown;
    nextStatement?: unknown;
    colour?: unknown;
    tooltip?: unknown;
    helpUrl?: unknown;
    [key: string]: unknown;
}

export function makeBlocklyEnvelope<TPayload>(layer: HydraBlocklyLayerKind, metamodelId: string, payload: TPayload, blockDefinitions?: unknown[]): HydraBlocklyLayerEnvelope<TPayload> {
    return { schemaVersion: '1.0', kind: 'HYDRA_BLOCKLY_LAYER', layer, metamodelId, generatedAt: new Date().toISOString(), payload, blockDefinitions, annotations: { 'hydra.blockly.integration': 'independent-layer', 'hydra.blockly.execution': 'declarative', 'hydra.blockly.preferredArtifact': 'javascript' } };
}

export function serializeBlocklyLayerJson<TPayload>(envelope: HydraBlocklyLayerEnvelope<TPayload>): string {
    return JSON.stringify(envelope, null, 2);
}

/** Generates a passive registration script. It does not require HYDRA to own the Blockly workspace. */
export function serializeBlocklyLayerJavaScript<TPayload>(envelope: HydraBlocklyLayerEnvelope<TPayload>): string {
    const json = JSON.stringify(envelope, null, 2).replace(/<\//g, '<\\/');
    return `/* HYDRA_BLOCKLY_LAYER_BEGIN\n${encodeURIComponent(JSON.stringify(envelope))}\nHYDRA_BLOCKLY_LAYER_END */\n(function(root){\n  'use strict';\n  const layer = ${json};\n  const bridge = root.HydraBlocklyBridge = root.HydraBlocklyBridge || { layers: {}, adapters: {} };\n  bridge.layers = bridge.layers || {};\n  bridge.adapters = bridge.adapters || {};\n  if (bridge.__hydraAdapterAware !== true) {\n    const previousRegister = typeof bridge.registerLayer === 'function' ? bridge.registerLayer.bind(bridge) : null;\n    bridge.registerLayer = function(kind, value){\n      if (previousRegister !== null) previousRegister(kind, value);\n      this.layers[kind] = value;\n      const adapters = Array.isArray(this.adapters[kind]) ? this.adapters[kind] : [];\n      for (const adapter of adapters) adapter(value, root.Blockly || null);\n      return value;\n    };\n    bridge.__hydraAdapterAware = true;\n  }\n  if (typeof bridge.getLayer !== 'function') bridge.getLayer = function(kind){ return this.layers[kind] || null; };\n  if (typeof bridge.registerAdapter !== 'function') bridge.registerAdapter = function(kind, adapter){\n    if (typeof adapter !== 'function') throw new TypeError('HYDRA Blockly adapter must be a function.');\n    this.adapters[kind] = this.adapters[kind] || [];\n    this.adapters[kind].push(adapter);\n    if (this.layers[kind]) adapter(this.layers[kind], root.Blockly || null);\n    return function(){ bridge.adapters[kind] = (bridge.adapters[kind] || []).filter(function(item){ return item !== adapter; }); };\n  };\n  bridge.registerLayer(layer.layer, layer);\n  if (layer.layer === 'METAMODEL' && Array.isArray(layer.blockDefinitions) && root.Blockly) {\n    const defineBlocks = root.Blockly.common && typeof root.Blockly.common.defineBlocksWithJsonArray === 'function'\n      ? root.Blockly.common.defineBlocksWithJsonArray.bind(root.Blockly.common)\n      : typeof root.Blockly.defineBlocksWithJsonArray === 'function' ? root.Blockly.defineBlocksWithJsonArray.bind(root.Blockly) : null;\n    if (defineBlocks !== null) defineBlocks(layer.blockDefinitions);\n  }\n  if (typeof root.dispatchEvent === 'function' && typeof root.CustomEvent === 'function') {\n    root.dispatchEvent(new root.CustomEvent('hydra:blockly-layer', { detail: layer }));\n  }\n})(typeof globalThis !== 'undefined' ? globalThis : window);\n`;
}

export function parseBlocklyLayerEnvelope<TPayload = unknown>(content: string): HydraBlocklyLayerEnvelope<TPayload> | null {
    const trimmed = content.trim();
    if (trimmed.startsWith('{')) {
        try { const parsed = JSON.parse(trimmed) as HydraBlocklyLayerEnvelope<TPayload>; return parsed?.kind === 'HYDRA_BLOCKLY_LAYER' ? parsed : null; } catch { return null; }
    }
    const metadata = content.match(/HYDRA_BLOCKLY_LAYER_BEGIN\s*\n([^\n]+)\nHYDRA_BLOCKLY_LAYER_END/);
    if (metadata !== null) {
        try { const parsed = JSON.parse(decodeURIComponent(metadata[1])) as HydraBlocklyLayerEnvelope<TPayload>; return parsed?.kind === 'HYDRA_BLOCKLY_LAYER' ? parsed : null; } catch { return null; }
    }
    return null;
}

/** Reads static JSON Blockly block definitions without evaluating JavaScript. */
export function parseBlocklyBlockDefinitions(content: string): { definitions: BlocklyBlockDefinition[]; warnings: string[] } {
    const envelope = parseBlocklyLayerEnvelope(content);
    if (envelope !== null && Array.isArray(envelope.blockDefinitions)) return { definitions: envelope.blockDefinitions.filter(isBlocklyDefinition) as BlocklyBlockDefinition[], warnings: [] };
    const warnings: string[] = [];
    const trimmed = content.trim();
    if (trimmed.startsWith('[') || trimmed.startsWith('{')) {
        try {
            const value = JSON.parse(trimmed) as unknown;
            const candidates = Array.isArray(value) ? value : isObject(value) && Array.isArray(value.blocks) ? value.blocks : isObject(value) && Array.isArray(value.blockDefinitions) ? value.blockDefinitions : [];
            return { definitions: candidates.filter(isBlocklyDefinition) as BlocklyBlockDefinition[], warnings };
        } catch { warnings.push('The file is not valid JSON.'); }
    }
    const arrays = extractBalancedArguments(content, /(?:(?:Blockly\.)?common\.)?defineBlocksWithJsonArray\s*\(/g, '[', ']');
    const definitions: BlocklyBlockDefinition[] = [];
    for (const arrayText of arrays) {
        try { const parsed = JSON.parse(arrayText) as unknown; if (Array.isArray(parsed)) definitions.push(...parsed.filter(isBlocklyDefinition) as BlocklyBlockDefinition[]); }
        catch { warnings.push('A defineBlocksWithJsonArray call contains dynamic JavaScript and could not be converted without executing code.'); }
    }
    for (const definition of extractClassicJsonInitDefinitions(content)) definitions.push(definition);
    if (definitions.length === 0) warnings.push('No static Blockly JSON block definitions were recognized. HYDRA deliberately does not execute imported JavaScript.');
    return { definitions, warnings };
}


export type BlocklyEventCategory = 'BLOCK' | 'VARIABLE' | 'UI' | 'WORKSPACE' | 'CUSTOM';

export interface BlocklyEventDeclaration {
    token: string;
    eventType: string;
    category: BlocklyEventCategory;
    source: string;
}

/**
 * Recovers Blockly event declarations and references from JavaScript without evaluating it.
 * Only static constants and event registry calls are considered importable evidence.
 */
export function parseBlocklyEventDeclarations(content: string): { events: BlocklyEventDeclaration[]; warnings: string[] } {
    const candidates = new Map<string, { token: string; eventType: string; source: string }>();
    const assignmentPattern = /(?:Blockly\.)?Events\.([A-Z][A-Z0-9_]*)\s*=\s*['"`]([^'"`]+)['"`]/g;
    let match: RegExpExecArray | null;
    while ((match = assignmentPattern.exec(content)) !== null) candidates.set(match[2], { token: match[1], eventType: match[2], source: `Blockly.Events.${match[1]}` });

    const declaredConstantPattern = /(?:export\s+)?(?:const|let|var)\s+((?:BLOCK|VAR|VARIABLE|BUBBLE|VIEWPORT|THEME|TOOLBOX|SELECTED|FINISHED_LOADING|CLICK|MARKER|TRASHCAN)[A-Z0-9_]*)\s*=\s*['"`]([^'"`]+)['"`]/g;
    while ((match = declaredConstantPattern.exec(content)) !== null) if (!candidates.has(match[2])) candidates.set(match[2], { token: match[1], eventType: match[2], source: `JavaScript constant ${match[1]}` });

    const referencedConstantPattern = /Blockly\.Events\.([A-Z][A-Z0-9_]*)/g;
    while ((match = referencedConstantPattern.exec(content)) !== null) {
        const token = match[1];
        const fallbackType = token.toLowerCase();
        if (![...candidates.values()].some(candidate => candidate.token === token)) candidates.set(fallbackType, { token, eventType: fallbackType, source: `Blockly.Events.${token} reference` });
    }

    const registryPattern = /(?:Blockly\.)?registry\.register\s*\(\s*(?:Blockly\.)?registry\.Type\.EVENT\s*,\s*['"`]([^'"`]+)['"`]/g;
    while ((match = registryPattern.exec(content)) !== null) if (!candidates.has(match[1])) candidates.set(match[1], { token: match[1], eventType: match[1], source: 'Blockly.registry.Type.EVENT' });

    const eventRegisterPattern = /(?:Blockly\.)?Events\.register\s*\(\s*['"`]([^'"`]+)['"`]/g;
    while ((match = eventRegisterPattern.exec(content)) !== null) if (!candidates.has(match[1])) candidates.set(match[1], { token: match[1], eventType: match[1], source: 'Blockly.Events.register' });

    const listenerLiteralPattern = /\b(?:event|changeEvent|blocklyEvent)\.type\s*={2,3}\s*['"`]([^'"`]+)['"`]/gi;
    while ((match = listenerLiteralPattern.exec(content)) !== null) if (!candidates.has(match[1])) candidates.set(match[1], { token: match[1].toUpperCase().replace(/[^A-Z0-9]+/g, '_'), eventType: match[1], source: 'Blockly change-listener event.type literal' });

    const warnings: string[] = [];
    if (candidates.size === 0) warnings.push('No static Blockly event declarations were recognized. HYDRA looks for Blockly.Events constants/references and EVENT registry registrations and never executes imported JavaScript.');
    return { events: [...candidates.values()].map(candidate => ({ ...candidate, category: inferBlocklyEventCategory(candidate.token, candidate.eventType) })), warnings };
}

function inferBlocklyEventCategory(token: string, eventType: string): BlocklyEventCategory {
    const normalized = `${token}_${eventType}`.toUpperCase();
    const literal = eventType.toLowerCase();
    if (/\bBLOCK|BLOCK_/.test(normalized) || ['create', 'change', 'delete', 'move'].includes(literal)) return 'BLOCK';
    if (/\bVAR|VARIABLE/.test(normalized) || /^var_(?:create|delete|rename)$/.test(literal)) return 'VARIABLE';
    if (/TOOLBOX|SELECT|CLICK|BUBBLE|VIEWPORT|THEME|TRASHCAN|MARKER|DRAG/.test(normalized) || /^(?:bubble_open|toolbox_item_select|selected|click|viewport_change|theme_change|trashcan_open|marker_move)$/.test(literal)) return 'UI';
    if (/WORKSPACE|FINISHED_LOADING|LOAD|SERIALIZ/.test(normalized) || literal === 'finished_loading') return 'WORKSPACE';
    return 'CUSTOM';
}

export function blockConnectionChecks(definition: BlocklyBlockDefinition): string[] {
    const values: string[] = [];
    for (const arg of definition.args0 ?? []) {
        const type = String(arg.type ?? '');
        if (type !== 'input_value' && type !== 'input_statement') continue;
        for (const check of normalizeChecks(arg.check)) values.push(`${String(arg.name ?? 'input')}:${check}`);
    }
    for (const check of normalizeChecks(definition.output)) values.push(`output:${check}`);
    for (const check of normalizeChecks(definition.previousStatement)) values.push(`previous:${check}`);
    for (const check of normalizeChecks(definition.nextStatement)) values.push(`next:${check}`);
    return [...new Set(values)];
}

function normalizeChecks(value: unknown): string[] { if (Array.isArray(value)) return value.map(String).filter(Boolean); if (typeof value === 'string' && value !== '') return [value]; return []; }
function isObject(value: unknown): value is Record<string, unknown> { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function isBlocklyDefinition(value: unknown): value is BlocklyBlockDefinition { return isObject(value) && typeof value.type === 'string' && value.type.trim() !== ''; }

function extractClassicJsonInitDefinitions(source: string): BlocklyBlockDefinition[] {
    const result: BlocklyBlockDefinition[] = [];
    const assignment = /Blockly\.Blocks\s*\[\s*['"]([^'"]+)['"]\s*\]\s*=\s*\{/g;
    let match: RegExpExecArray | null;
    while ((match = assignment.exec(source)) !== null) {
        const nextAssignment = source.slice(assignment.lastIndex).search(/Blockly\.Blocks\s*\[/);
        const limit = nextAssignment < 0 ? source.length : assignment.lastIndex + nextAssignment;
        const segment = source.slice(match.index, limit);
        const objects = extractBalancedArguments(segment, /\.jsonInit\s*\(/g, '{', '}');
        const objectText = objects[0];
        if (objectText === undefined) continue;
        try {
            const parsed = JSON.parse(objectText) as unknown;
            if (!isObject(parsed)) continue;
            result.push({ ...parsed, type: typeof parsed.type === 'string' && parsed.type.trim() !== '' ? parsed.type : match[1] } as BlocklyBlockDefinition);
        } catch { /* Dynamic classic init is deliberately left unexecuted. */ }
    }
    return result;
}

function extractBalancedArguments(source: string, pattern: RegExp, open: '['|'{', close: ']'|'}'): string[] {
    const result: string[] = []; let match: RegExpExecArray | null;
    while ((match = pattern.exec(source)) !== null) {
        let index = match.index + match[0].length; while (index < source.length && /\s/.test(source[index])) index += 1;
        if (source[index] !== open) continue;
        let depth = 0; let quote = ''; let escaped = false;
        for (let cursor = index; cursor < source.length; cursor += 1) {
            const char = source[cursor];
            if (quote !== '') { if (escaped) escaped = false; else if (char === '\\') escaped = true; else if (char === quote) quote = ''; continue; }
            if (char === '"' || char === "'") { quote = char; continue; }
            if (char === open) depth += 1;
            if (char === close) { depth -= 1; if (depth === 0) { result.push(source.slice(index, cursor + 1)); pattern.lastIndex = cursor + 1; break; } }
        }
    }
    return result;
}
