import type { MetaModel } from '../metamodel-designer/types';
import { createDefaultEventField, createDefaultEventType, createEventDesignerId } from '../event-designer/eventDesignerFactory';
import { normalizeEventBindingModel, normalizeEventDefinitionModel } from '../event-designer/eventModelNormalization';
import type { EventBinding, EventBindingModel, EventDefinitionModel, EventFieldDefinition, EventFieldType, EventPersistencePolicy, EventSubjectKind, EventTypeDefinition } from '../event-designer/types';
import type { LayerExportArtifact, LayerImportAnalysis, LayerImportSelection, LayerInterchangeFormat } from './layerInterchangeTypes';
import { makeLayerInterchangeEnvelope, unwrapLayerInterchangeEnvelope } from './layerInterchangeEnvelope';
import { sanitizeIdentifier } from './interchangeSupport';
import { makeBlocklyEnvelope, parseBlocklyEventDeclarations, parseBlocklyLayerEnvelope, serializeBlocklyLayerJavaScript, serializeBlocklyLayerJson } from '../blockly/blocklyInterchange';

export type EventInterchangeFormat = 'HYDRA_JSON' | 'ASYNCAPI_JSON' | 'BLOCKLY_JSON' | 'BLOCKLY_JAVASCRIPT';
export const EVENT_INTERCHANGE_FORMATS: readonly LayerInterchangeFormat<EventInterchangeFormat>[] = [
    { id: 'HYDRA_JSON', label: 'HYDRA event contracts JSON', description: 'Lossless event definitions and bindings, including every HYDRA-specific property.', extensions: ['.events.json', '.json'], mimeType: 'application/json' },
    { id: 'ASYNCAPI_JSON', label: 'AsyncAPI 3 JSON', description: 'Portable event/message contracts using AsyncAPI 3. HYDRA extensions preserve sources, subjects, persistence and transformation bindings.', extensions: ['.asyncapi.json', '.json'], mimeType: 'application/json' },
    { id: 'BLOCKLY_JSON', label: 'Blockly event layer (JSON)', description: 'Exports/imports event contracts as an independent HYDRA layer for injection into an existing Blockly editor.', extensions: ['.events.blockly.json', '.blockly.json'], mimeType: 'application/json' },
    { id: 'BLOCKLY_JAVASCRIPT', label: 'Blockly event layer (JavaScript · recommended for injection)', description: 'Recommended for direct Blockly injection: a passive JavaScript registration module that loads event contracts into an existing Blockly host without rebuilding its metamodel.', extensions: ['.events.blockly.js', '.blockly.js', '.js'], mimeType: 'text/javascript' }
];

export interface ImportedEventLayer { definitions: EventDefinitionModel; bindings: EventBindingModel; warnings: string[]; format: EventInterchangeFormat; }

export function importEventLayer(content: string, format: EventInterchangeFormat, metamodel: MetaModel, current: { definitions: EventDefinitionModel; bindings: EventBindingModel }, selection?: LayerImportSelection): ImportedEventLayer {
    if (format === 'BLOCKLY_JSON' || format === 'BLOCKLY_JAVASCRIPT') {
        const envelope = parseBlocklyLayerEnvelope<{ definitions: EventDefinitionModel; bindings: EventBindingModel }>(content);
        if (envelope !== null && envelope.layer === 'EVENTS') {
            const warnings = envelope.metamodelId !== '' && envelope.metamodelId !== metamodel.id ? [`Imported event layer targets metamodel ${envelope.metamodelId}; object/enum references will be adapted to ${metamodel.id}.`] : [];
            const definitions = adaptDefinitions(normalizeEventDefinitionModel(envelope.payload.definitions), metamodel, current.definitions, warnings);
            const bindings = normalizeEventBindingModel(envelope.payload.bindings);
            return filterImportedEventLayer({ definitions: { ...definitions, annotations: { ...definitions.annotations, 'hydra.interchange.format': format } }, bindings: { ...bindings, metamodelId: metamodel.id, eventDefinitionModelId: definitions.id, annotations: { ...bindings.annotations, 'hydra.interchange.format': format } }, warnings, format }, selection);
        }
        if (format === 'BLOCKLY_JAVASCRIPT') {
            const parsed = parseStaticBlocklyEvents(content, current.definitions);
            return filterImportedEventLayer({ definitions: { ...current.definitions, eventTypes: parsed.eventTypes, annotations: { ...current.definitions.annotations, 'hydra.interchange.format': format } }, bindings: { ...current.bindings, bindings: [], annotations: { ...current.bindings.annotations, 'hydra.interchange.format': format } }, warnings: parsed.warnings, format }, selection);
        }
        return filterImportedEventLayer(emptyImportedEventLayer(current, format, 'This Blockly JSON file does not contain a HYDRA EVENTS layer. No event contracts were imported.'), selection);
    }
    if (format === 'HYDRA_JSON') {
        type Payload = { definitions?: EventDefinitionModel; bindings?: EventBindingModel } | EventDefinitionModel;
        const envelope = unwrapLayerInterchangeEnvelope<Payload>(content, 'EVENTS', metamodel.id);
        const parsed = envelope.payload;
        const warnings: string[] = [...envelope.warnings];
        if ('definitions' in parsed && parsed.definitions !== undefined) {
            const normalized = normalizeEventDefinitionModel(parsed.definitions);
            const definitions = { ...adaptDefinitions(normalized, metamodel, current.definitions, warnings), annotations: { ...normalized.annotations, 'hydra.interchange.format': 'HYDRA_JSON' } };
            const bindings = parsed.bindings === undefined ? { ...current.bindings, eventDefinitionModelId: definitions.id } : normalizeEventBindingModel(parsed.bindings);
            return filterImportedEventLayer({ definitions, bindings: { ...bindings, metamodelId: metamodel.id, eventDefinitionModelId: definitions.id, annotations: { ...bindings.annotations, 'hydra.interchange.format': 'HYDRA_JSON' } }, warnings, format }, selection);
        }
        const normalized = normalizeEventDefinitionModel(parsed as EventDefinitionModel);
        const definitions = { ...adaptDefinitions(normalized, metamodel, current.definitions, warnings), annotations: { ...normalized.annotations, 'hydra.interchange.format': 'HYDRA_JSON' } };
        warnings.push('The JSON contained only event definitions; existing bindings were preserved.');
        return filterImportedEventLayer({ definitions, bindings: { ...current.bindings, eventDefinitionModelId: definitions.id, annotations: { ...current.bindings.annotations, 'hydra.interchange.format': 'HYDRA_JSON' } }, warnings, format }, selection);
    }
    return filterImportedEventLayer(importAsyncApi(content, metamodel, current), selection);
}


export function analyzeEventImport(content: string, format: EventInterchangeFormat, metamodel: MetaModel, current: { definitions: EventDefinitionModel; bindings: EventBindingModel }): LayerImportAnalysis {
    const imported = importEventLayer(content, format, metamodel, current);
    const invalid = imported.definitions.eventTypes.filter(type => type.id.trim() === '' || type.name.trim() === '');
    const unresolved = imported.warnings.filter(warning => /not found|unresolved|targets metamodel|review .* references/i.test(warning));
    const hardErrors = imported.definitions.eventTypes.length === 0 ? 1 : invalid.length;
    const categories = new Set(imported.definitions.eventTypes.map(type => type.category || 'CUSTOM'));
    return {
        canImport: imported.definitions.eventTypes.length > 0,
        summary: `${imported.definitions.eventTypes.length} event(s) identified in ${format}; ${categories.size} categor${categories.size === 1 ? 'y' : 'ies'} available for selective import.`,
        metrics: [
            { id: 'identified-events', label: 'Events identified', value: imported.definitions.eventTypes.length, tone: imported.definitions.eventTypes.length > 0 ? 'GOOD' : 'ERROR' },
            { id: 'valid-events', label: 'Valid event contracts', value: Math.max(0, imported.definitions.eventTypes.length - invalid.length), tone: 'GOOD' },
            { id: 'invalid-events', label: 'Invalid events', value: hardErrors, tone: hardErrors > 0 ? 'ERROR' : 'GOOD', detail: hardErrors > 0 && imported.definitions.eventTypes.length === 0 ? 'No importable event was recognized.' : undefined },
            { id: 'warnings', label: 'Warnings', value: imported.warnings.length, tone: imported.warnings.length > 0 ? 'WARNING' : 'GOOD' },
            { id: 'unresolved', label: 'Unresolved references', value: unresolved.length, tone: unresolved.length > 0 ? 'WARNING' : 'GOOD' },
            { id: 'bindings', label: 'Bindings identified', value: imported.bindings.bindings.length, tone: 'NEUTRAL' },
            { id: 'categories', label: 'Categories', value: categories.size, tone: 'NEUTRAL' }
        ],
        diagnostics: [
            ...invalid.map(type => ({ severity: 'ERROR' as const, category: 'Invalid event', itemId: type.id, message: `${type.name || type.id || 'Unnamed event'} does not have a stable ID and name.` })),
            ...imported.warnings.map(warning => ({ severity: /no .*recognized|does not contain/i.test(warning) ? 'ERROR' as const : 'WARNING' as const, category: /not found|unresolved|metamodel/i.test(warning) ? 'Metamodel adaptation' : 'Import parser', message: warning }))
        ],
        warnings: imported.warnings,
        items: imported.definitions.eventTypes.map(type => ({
            id: type.id,
            label: type.name || type.id,
            category: type.category || 'CUSTOM',
            description: type.description || `Blockly/JavaScript event contract ${type.id}`,
            source: type.annotations['hydra.blockly.source'] || type.annotations['hydra.import.format'] || format,
            warnings: imported.warnings.filter(warning => warning.includes(type.name) || warning.includes(type.id)),
            selectedByDefault: type.enabled !== false && type.deprecated !== true
        }))
    };
}

function emptyImportedEventLayer(current: { definitions: EventDefinitionModel; bindings: EventBindingModel }, format: EventInterchangeFormat, warning: string): ImportedEventLayer {
    return {
        definitions: { ...current.definitions, eventTypes: [], annotations: { ...current.definitions.annotations, 'hydra.interchange.format': format } },
        bindings: { ...current.bindings, bindings: [], annotations: { ...current.bindings.annotations, 'hydra.interchange.format': format } },
        warnings: [warning],
        format
    };
}

function filterImportedEventLayer(imported: ImportedEventLayer, selection?: LayerImportSelection): ImportedEventLayer {
    if (selection === undefined) return imported;
    const selectedIds = new Set(selection.itemIds);
    const allImportedIds = new Set(imported.definitions.eventTypes.map(type => type.id));
    const eventTypes = imported.definitions.eventTypes.filter(type => selectedIds.has(type.id));
    const bindings = imported.bindings.bindings.filter(binding => {
        const targetSelected = !allImportedIds.has(binding.targetEventTypeId) || selectedIds.has(binding.targetEventTypeId);
        const sourceSelected = binding.sourceEventTypeId === '' || !allImportedIds.has(binding.sourceEventTypeId) || selectedIds.has(binding.sourceEventTypeId);
        return targetSelected && sourceSelected;
    });
    const skipped = imported.definitions.eventTypes.length - eventTypes.length;
    const skippedBindings = imported.bindings.bindings.length - bindings.length;
    const selectionWarnings = [
        ...(skipped > 0 ? [`${skipped} identified event(s) were intentionally skipped by the user's import selection.`] : []),
        ...(skippedBindings > 0 ? [`${skippedBindings} binding(s) were skipped because one of their imported endpoint events was not selected.`] : [])
    ];
    return {
        ...imported,
        definitions: { ...imported.definitions, eventTypes },
        bindings: { ...imported.bindings, bindings },
        warnings: [...imported.warnings, ...selectionWarnings]
    };
}

function parseStaticBlocklyEvents(content: string, current: EventDefinitionModel): { eventTypes: EventTypeDefinition[]; warnings: string[] } {
    const parsed = parseBlocklyEventDeclarations(content);
    const eventTypes = parsed.events.map(candidate => {
        const base = createDefaultEventType(current.namespace);
        const stableToken = sanitizeIdentifier(candidate.eventType || candidate.token, 'event').toLowerCase();
        return {
            ...base,
            id: `blockly.event.${stableToken}`,
            name: humanizeBlocklyEvent(candidate.token || candidate.eventType),
            namespace: current.namespace || 'blockly',
            description: `Statically detected Blockly event '${candidate.eventType}' from ${candidate.source}.`,
            category: candidate.category,
            version: '1.0',
            fields: [],
            allowedSources: ['BLOCKLY'],
            allowedSubjects: blocklySubjects(candidate.category),
            persistencePolicy: 'TRANSIENT' as EventTypeDefinition['persistencePolicy'],
            annotations: { ...base.annotations, 'hydra.import.format': 'BLOCKLY_JAVASCRIPT', 'hydra.blockly.eventToken': candidate.eventType, 'hydra.blockly.source': candidate.source }
        };
    });
    return { eventTypes, warnings: parsed.warnings };
}

function blocklySubjects(category: string): EventSubjectKind[] {
    if (category === 'BLOCK') return ['MODEL_ELEMENT'];
    if (category === 'VARIABLE') return ['MODEL_FEATURE'];
    if (category === 'UI') return ['EDITOR'];
    if (category === 'WORKSPACE') return ['MODEL'];
    return [];
}

function humanizeBlocklyEvent(value: string): string {
    const words = value.replace(/^BLOCKLY[_.-]?/i, '').split(/[_\-.\s]+/).filter(Boolean).map(word => word.toLowerCase());
    if (words.length === 0) return 'Blockly event';
    return words.map((word, index) => index === 0 ? word.charAt(0).toUpperCase() + word.slice(1) : word).join(' ');
}

function importAsyncApi(content: string, metamodel: MetaModel, current: { definitions: EventDefinitionModel; bindings: EventBindingModel }): ImportedEventLayer {
    const document = JSON.parse(content) as Record<string, unknown>;
    const warnings: string[] = [];
    const components = asObject(document.components);
    const messages = asObject(components.messages);
    const eventTypes: EventTypeDefinition[] = [];
    for (const [key, raw] of Object.entries(messages)) {
        const message = asObject(raw);
        const payload = asObject(message.payload);
        const properties = asObject(payload.properties);
        const required = new Set(Array.isArray(payload.required) ? payload.required.map(String) : []);
        const fields: EventFieldDefinition[] = Object.entries(properties).map(([name, schemaValue]) => {
            const schema = asObject(schemaValue);
            const field = createDefaultEventField();
            const original = asObject(schema['x-hydra-field']);
            const mapped = mapJsonSchemaType(schema);
            return { ...field, id: stringValue(original.id) || createEventDesignerId('field'), name, type: mapped.type, required: required.has(name), many: mapped.many, enumTypeId: adaptEnumType(stringValue(original.enumTypeId), metamodel, warnings, key, name), objectTypeId: adaptObjectType(stringValue(original.objectTypeId), metamodel, warnings, key, name), description: stringValue(schema.description) };
        });
        const hydra = asObject(message['x-hydra-event']);
        const base = createDefaultEventType(current.definitions.namespace);
        eventTypes.push({
            ...base,
            id: stringValue(hydra.id) || stringValue(message.name) || createEventDesignerId('event'),
            name: stringValue(message.title) || stringValue(message.name) || key,
            namespace: stringValue(hydra.namespace) || current.definitions.namespace,
            description: stringValue(message.description),
            category: stringValue(hydra.category) || 'ASYNCAPI',
            version: stringValue(hydra.version) || '1.0',
            fields,
            allowedSources: stringArray(hydra.allowedSources),
            allowedSubjects: stringArray(hydra.allowedSubjects).filter(isSubjectKind),
            persistencePolicy: persistence(stringValue(hydra.persistencePolicy)),
            abstractType: booleanValue(hydra.abstractType),
            deprecated: booleanValue(message.deprecated) || booleanValue(hydra.deprecated),
            enabled: hydra.enabled === undefined ? true : booleanValue(hydra.enabled),
            annotations: { 'hydra.import.format': 'ASYNCAPI_JSON' }
        });
    }
    const definitions: EventDefinitionModel = { ...current.definitions, eventTypes, annotations: { ...current.definitions.annotations, 'hydra.interchange.format': 'ASYNCAPI_JSON' } };
    const rawBindings = Array.isArray(document['x-hydra-bindings']) ? document['x-hydra-bindings'] : [];
    const ids = new Set(eventTypes.map(item => item.id));
    const bindings = rawBindings.map(value => value as EventBinding).filter(binding => {
        const valid = (ids.has(binding.targetEventTypeId)) && (ids.has(binding.sourceEventTypeId) || binding.sourceEventTypeId !== '');
        if (!valid) warnings.push(`Binding ${binding.id ?? '(unnamed)'} references an event that was not imported and was skipped.`);
        return valid;
    });
    if (Object.keys(messages).length === 0) warnings.push('AsyncAPI document contains no components.messages entries.');
    return { definitions, bindings: { ...current.bindings, eventDefinitionModelId: definitions.id, bindings, annotations: { ...current.bindings.annotations, 'hydra.interchange.format': 'ASYNCAPI_JSON' } }, warnings, format: 'ASYNCAPI_JSON' };
}

function adaptDefinitions(model: EventDefinitionModel, metamodel: MetaModel, current: EventDefinitionModel, warnings: string[]): EventDefinitionModel {
    return {
        ...model,
        id: current.id,
        metamodelId: metamodel.id,
        eventTypes: model.eventTypes.map(type => ({ ...type, fields: type.fields.map(field => ({ ...field, enumTypeId: adaptEnumType(field.enumTypeId, metamodel, warnings, type.name, field.name), objectTypeId: adaptObjectType(field.objectTypeId, metamodel, warnings, type.name, field.name) })) }))
    };
}

function adaptEnumType(value: string, metamodel: MetaModel, warnings: string[], eventName: string, fieldName: string): string {
    if (value === '') return '';
    const item = metamodel.enums.find(candidate => candidate.id === value || candidate.name === value || candidate.name.toLowerCase() === value.toLowerCase());
    if (item === undefined) { warnings.push(`${eventName}.${fieldName}: enumeration ${value} was not found in the selected metamodel.`); return ''; }
    return item.id;
}
function adaptObjectType(value: string, metamodel: MetaModel, warnings: string[], eventName: string, fieldName: string): string {
    if (value === '') return '';
    const item = metamodel.classes.find(candidate => candidate.id === value || candidate.name === value || candidate.name.toLowerCase() === value.toLowerCase());
    if (item === undefined) { warnings.push(`${eventName}.${fieldName}: object contract ${value} was not found in the selected metamodel.`); return ''; }
    return item.id;
}

export function exportEventLayer(format: EventInterchangeFormat, metamodel: MetaModel, definitions: EventDefinitionModel, bindings: EventBindingModel): LayerExportArtifact {
    if (format === 'BLOCKLY_JSON' || format === 'BLOCKLY_JAVASCRIPT') {
        const envelope = makeBlocklyEnvelope('EVENTS', metamodel.id, { definitions, bindings });
        return format === 'BLOCKLY_JSON' ? { fileName: `${metamodel.id}.events.blockly.json`, mimeType: 'application/json', content: serializeBlocklyLayerJson(envelope) } : { fileName: `${metamodel.id}.events.blockly.js`, mimeType: 'text/javascript', content: serializeBlocklyLayerJavaScript(envelope) };
    }
    if (format === 'HYDRA_JSON') return { fileName: `${metamodel.id}.events.json`, mimeType: 'application/json', content: JSON.stringify(makeLayerInterchangeEnvelope('EVENTS', metamodel.id, { definitions, bindings }, [definitions.id, bindings.id]), null, 2) };
    const messages: Record<string, unknown> = {};
    const channels: Record<string, unknown> = {};
    const used = new Set<string>();
    for (const eventType of definitions.eventTypes) {
        let key = sanitizeIdentifier(eventType.name, 'Event');
        let suffix = 2; while (used.has(key)) { key = `${sanitizeIdentifier(eventType.name, 'Event')}_${suffix}`; suffix += 1; } used.add(key);
        const properties: Record<string, unknown> = {};
        for (const field of eventType.fields) properties[field.name] = eventFieldSchema(field, metamodel);
        messages[key] = {
            name: eventType.id,
            title: eventType.name,
            description: eventType.description,
            deprecated: eventType.deprecated,
            contentType: 'application/json',
            payload: { type: 'object', properties, required: eventType.fields.filter(item => item.required).map(item => item.name) },
            'x-hydra-event': { id: eventType.id, namespace: eventType.namespace, category: eventType.category, version: eventType.version, allowedSources: eventType.allowedSources, allowedSubjects: eventType.allowedSubjects, persistencePolicy: eventType.persistencePolicy, abstractType: eventType.abstractType, deprecated: eventType.deprecated, enabled: eventType.enabled }
        };
        channels[key] = { address: `${eventType.namespace}.${eventType.id}`, messages: { [key]: { $ref: `#/components/messages/${key}` } } };
    }
    const document = { asyncapi: '3.0.0', info: { title: `${metamodel.name || metamodel.id} HYDRA events`, version: '1.0.0', description: 'Generated from editable HYDRA event contracts.' }, defaultContentType: 'application/json', channels, components: { messages }, 'x-hydra-metamodel-id': metamodel.id, 'x-hydra-bindings': bindings.bindings };
    return { fileName: `${metamodel.id}.asyncapi.json`, mimeType: 'application/json', content: JSON.stringify(document, null, 2) };
}

function eventFieldSchema(field: EventFieldDefinition, metamodel: MetaModel): Record<string, unknown> {
    const base: Record<string, unknown> = { description: field.description, 'x-hydra-field': { id: field.id, enumTypeId: field.enumTypeId, objectTypeId: field.objectTypeId } };
    const item = primitiveSchema(field.type, field.enumTypeId, metamodel);
    return field.many ? { ...base, type: 'array', items: item } : { ...base, ...item };
}
function primitiveSchema(type: EventFieldType, enumTypeId: string, metamodel: MetaModel): Record<string, unknown> {
    if (type === 'INTEGER') return { type: 'integer' };
    if (type === 'DECIMAL') return { type: 'number' };
    if (type === 'BOOLEAN') return { type: 'boolean' };
    if (type === 'DATE') return { type: 'string', format: 'date' };
    if (type === 'DATETIME') return { type: 'string', format: 'date-time' };
    if (type === 'DURATION') return { type: 'string', format: 'duration' };
    if (type === 'OBJECT') return { type: 'object' };
    if (type === 'ARRAY') return { type: 'array', items: {} };
    if (type === 'ANY') return {};
    if (type === 'ENUM') { const values = metamodel.enums.find(item => item.id === enumTypeId)?.literals.map(item => item.name); return { type: 'string', ...(values === undefined ? {} : { enum: values }) }; }
    return { type: 'string' };
}
function mapJsonSchemaType(schema: Record<string, unknown>): { type: EventFieldType; many: boolean } {
    if (schema.type === 'array') { const item = asObject(schema.items); return { ...mapJsonSchemaType(item), many: true }; }
    if (schema.enum !== undefined) return { type: 'ENUM', many: false };
    if (schema.type === 'integer') return { type: 'INTEGER', many: false };
    if (schema.type === 'number') return { type: 'DECIMAL', many: false };
    if (schema.type === 'boolean') return { type: 'BOOLEAN', many: false };
    if (schema.type === 'object') return { type: 'OBJECT', many: false };
    if (schema.format === 'date') return { type: 'DATE', many: false };
    if (schema.format === 'date-time') return { type: 'DATETIME', many: false };
    if (schema.format === 'duration') return { type: 'DURATION', many: false };
    return { type: 'STRING', many: false };
}
function asObject(value: unknown): Record<string, unknown> { return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}; }
function stringValue(value: unknown): string { return typeof value === 'string' ? value : ''; }
function stringArray(value: unknown): string[] { return Array.isArray(value) ? value.filter(item => typeof item === 'string') : []; }
function booleanValue(value: unknown): boolean { return value === true; }
const SUBJECTS: EventSubjectKind[] = ['METAMODEL','META_CLASS','META_ATTRIBUTE','META_REFERENCE','META_OPERATION','MODEL','MODEL_ELEMENT','MODEL_FEATURE','VALIDATION_RULE','VALIDATION_DIAGNOSTIC','QUICK_FIX','ACTION','EVENT_DEFINITION','EVENT_FLOW','EDITOR','GAMIFICATION_RULE','GAMIFICATION_REWARD'];
function isSubjectKind(value: string): value is EventSubjectKind { return SUBJECTS.includes(value as EventSubjectKind); }
function persistence(value: string): EventPersistencePolicy { return ['TRANSIENT','SESSION','AUDIT','GAMIFICATION','EXTERNAL'].includes(value) ? value as EventPersistencePolicy : 'TRANSIENT'; }
