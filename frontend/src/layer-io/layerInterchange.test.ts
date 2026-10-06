import { describe, expect, it } from 'vitest';
import type { MetaModel } from '../metamodel-designer/types';
import { createEmptyActionModel, createEmptyExpressionModel, createEmptyValidationModel } from '../validation-designer/validationDesignerFactory';
import { createEmptyEventModels } from '../event-designer/eventDesignerFactory';
import { createEmptyEventFlowModel } from '../event-designer/flow/eventFlowFactory';
import { exportValidationLayer, importValidationLayer } from './validationInterchange';
import { exportQuickFixLayer, importQuickFixLayer } from './quickFixInterchange';
import { exportEventLayer, importEventLayer } from './eventInterchange';
import { exportEventFlowLayer, importEventFlowLayer } from './eventFlowInterchange';

const METAMODEL: MetaModel = {
    schemaVersion: '2.1',
    id: 'people',
    name: 'People',
    classes: [{
        id: 'class-person',
        name: 'Person',
        kind: 'CLASS',
        attributes: [
            { id: 'attr-name', name: 'name', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 0, upperBound: 1 }, ordered: false, unique: true, defaultValue: '', annotations: {} },
            { id: 'attr-age', name: 'age', type: { kind: 'PRIMITIVE', targetId: 'INTEGER', externalName: '' }, multiplicity: { lowerBound: 0, upperBound: 1 }, ordered: false, unique: true, defaultValue: '', annotations: {} }
        ],
        operations: [],
        annotations: {}
    }],
    enums: [{ id: 'enum-status', name: 'Status', literals: [{ id: 'status-active', name: 'ACTIVE', value: 0, annotations: {} }], annotations: {} }],
    dataTypes: [],
    relations: [],
    annotations: {}
};

function validationDocuments() {
    const expressions = createEmptyExpressionModel(METAMODEL.id);
    const actions = createEmptyActionModel(METAMODEL.id, expressions.id);
    const validations = createEmptyValidationModel(METAMODEL.id, expressions.id, actions.id);
    return { expressions, actions, validations };
}

describe('independent validation interchange', () => {
    it('maps OCL class/attribute names to the current metamodel and round-trips stable IDs', () => {
        const current = validationDocuments();
        const imported = importValidationLayer('context Person inv Adult: self.age >= 18', 'people.ocl', 'OCL', METAMODEL, current);
        expect(imported.warnings).toEqual([]);
        expect(imported.rules).toHaveLength(1);
        expect(imported.rules[0].context.classId).toBe('class-person');
        expect(imported.expressions[0].root.kind).toBe('BINARY');
        if (imported.expressions[0].root.kind !== 'BINARY') throw new Error('expected binary expression');
        expect(imported.expressions[0].root.left.kind).toBe('ATTRIBUTE_ACCESS');
        if (imported.expressions[0].root.left.kind !== 'ATTRIBUTE_ACCESS') throw new Error('expected attribute access');
        expect(imported.expressions[0].root.left.attributeId).toBe('attr-age');

        const expressionModel = { ...current.expressions, expressions: imported.expressions };
        const validationModel = { ...current.validations, rules: imported.rules, ruleSets: imported.ruleSets };
        const exported = exportValidationLayer('OCL', METAMODEL, expressionModel, validationModel);
        expect(exported.content).toContain(`-- @hydra-id ${imported.rules[0].id}`);
        expect(exported.content).toContain(`-- @hydra-expression-id ${imported.expressions[0].id}`);
        const roundTrip = importValidationLayer(exported.content, exported.fileName, 'OCL', METAMODEL, current);
        expect(roundTrip.rules[0].id).toBe(imported.rules[0].id);
        expect(roundTrip.expressions[0].id).toBe(imported.expressions[0].id);

        const editedRoot = structuredClone(imported.expressions[0].root);
        if (editedRoot.kind !== 'BINARY' || editedRoot.right.kind !== 'LITERAL') throw new Error('expected editable numeric literal');
        editedRoot.right.value = 21;
        const editedExport = exportValidationLayer('OCL', METAMODEL, { ...expressionModel, expressions: [{ ...imported.expressions[0], root: editedRoot }] }, validationModel);
        expect(editedExport.content).toContain('>= 21');
    });

    it('preserves an unresolved external validation as an editable partial draft instead of discarding it', () => {
        const current = validationDocuments();
        const imported = importValidationLayer('context MissingPerson inv Adult: self.age >= 18', 'legacy.ocl', 'OCL', METAMODEL, current);
        expect(imported.rules).toHaveLength(1);
        expect(imported.rules[0].context.classId).toBe('');
        expect(imported.rules[0].conversionStatus).toBe('PARTIAL');
        expect(imported.warnings.some(item => item.includes('unresolved editable draft'))).toBe(true);
    });

    it('imports EVL and ATL validation constructs as editable canonical rules', () => {
        const current = validationDocuments();
        const evl = importValidationLayer("context Person { constraint NameRequired { check: self.name.isNotEmpty()\n message: 'Name required' } }", 'rules.evl', 'EVL', METAMODEL, current);
        expect(evl.rules[0].name).toBe('NameRequired');
        expect(evl.rules[0].messageTemplate).toBe('Name required');
        expect(evl.rules[0].authoritativeRepresentation).toBe('EVL');

        const atl = importValidationLayer('helper context MM!Person def: IsAdult : Boolean = self.age >= 18;', 'rules.atl', 'ATL', METAMODEL, current);
        expect(atl.rules[0].name).toBe('IsAdult');
        expect(atl.rules[0].context.classId).toBe('class-person');
        expect(atl.rules[0].authoritativeRepresentation).toBe('ATL');
    });

    it('keeps an EVL constraint visible as a partial editable rule when its check syntax cannot be recognized', () => {
        const current = validationDocuments();
        const evl = importValidationLayer("context Person { constraint NeedsReview { guard: self.age > 0\n message: 'Review me' } }", 'partial.evl', 'EVL', METAMODEL, current);
        expect(evl.rules).toHaveLength(1);
        expect(evl.rules[0].name).toBe('NeedsReview');
        expect(evl.rules[0].conversionStatus).toBe('PARTIAL');
        expect(evl.expressions).toHaveLength(1);
        expect(evl.warnings.some(item => item.includes('preserved as an editable partial draft'))).toBe(true);
    });

});

describe('independent quick-fix interchange', () => {
    it('maps EVL fixes to declarative actions and preserves their validation association', () => {
        const current = validationDocuments();
        const source = `context Person {\n constraint NameRequired {\n  check: self.name.isNotEmpty()\n  message: 'Name required'\n  fix {\n   title: 'Set fallback name'\n   do { self.name = 'Unknown'; }\n  }\n }\n}`;
        const imported = importQuickFixLayer(source, 'rules.evl', 'EVL', METAMODEL);
        expect(imported.actions).toHaveLength(1);
        expect(imported.actions[0].operations[0].kind).toBe('SET_ATTRIBUTE');
        expect(imported.links).toEqual([{ actionId: imported.actions[0].id, validationRuleName: 'NameRequired' }]);

        const actionModel = { ...current.actions, actions: imported.actions };
        const exported = exportQuickFixLayer('EVL', METAMODEL, actionModel);
        expect(exported.content).toContain(`@hydra-action-id ${imported.actions[0].id}`);
        expect(exported.content).toContain('constraint NameRequired');
        const roundTrip = importQuickFixLayer(exported.content, exported.fileName, 'EVL', METAMODEL);
        expect(roundTrip.actions[0].id).toBe(imported.actions[0].id);
    });

    it('keeps quick fixes from an unknown context as drafts for manual remapping', () => {
        const imported = importQuickFixLayer("operation LegacyPerson clearName() { self.name = null; }", 'legacy.eol', 'EOL', METAMODEL);
        expect(imported.actions).toHaveLength(1);
        expect(imported.actions[0].annotations?.['hydra.import.contextClassId']).toBe('');
        expect(imported.warnings.some(item => item.includes('editable draft'))).toBe(true);
    });

    it('imports EOL operations against current metamodel attributes', () => {
        const imported = importQuickFixLayer("operation Person clearName() { self.name = null; }", 'repairs.eol', 'EOL', METAMODEL);
        expect(imported.actions[0].name).toBe('clearName');
        expect(imported.actions[0].operations[0]).toMatchObject({ kind: 'CLEAR_ATTRIBUTE', attributeId: 'attr-name' });
    });
});

describe('independent event interchange', () => {
    it('adapts AsyncAPI payload references to current enum/class IDs and round-trips event IDs', () => {
        const current = createEmptyEventModels(METAMODEL.id);
        const asyncApi = JSON.stringify({
            asyncapi: '3.0.0',
            info: { title: 'People events', version: '1.0.0' },
            components: { messages: {
                PersonChanged: {
                    name: 'person.changed', title: 'Person changed',
                    payload: { type: 'object', required: ['person'], properties: {
                        person: { type: 'object', 'x-hydra-field': { id: 'f-person', objectTypeId: 'Person' } },
                        status: { type: 'string', enum: ['ACTIVE'], 'x-hydra-field': { id: 'f-status', enumTypeId: 'Status' } }
                    } },
                    'x-hydra-event': { id: 'person.changed', namespace: 'people', allowedSources: ['MODEL_RUNTIME'], allowedSubjects: ['MODEL_ELEMENT'], persistencePolicy: 'AUDIT', enabled: true }
                }
            } }
        });
        const imported = importEventLayer(asyncApi, 'ASYNCAPI_JSON', METAMODEL, current);
        expect(imported.warnings).toEqual([]);
        expect(imported.definitions.eventTypes[0].fields[0].objectTypeId).toBe('class-person');
        expect(imported.definitions.eventTypes[0].fields[1].enumTypeId).toBe('enum-status');
        const exported = exportEventLayer('ASYNCAPI_JSON', METAMODEL, imported.definitions, imported.bindings);
        const roundTrip = importEventLayer(exported.content, 'ASYNCAPI_JSON', METAMODEL, current);
        expect(roundTrip.definitions.eventTypes[0].id).toBe('person.changed');
    });
});

describe('independent EventFlow interchange', () => {
    it('maps Node-RED nodes/wires to editable EventFlow and preserves the flow ID on HYDRA round trips', () => {
        const event = { ...createEmptyEventModels(METAMODEL.id).definitions, eventTypes: [{ id: 'person.changed', name: 'PersonChanged', namespace: 'people', description: '', category: 'CUSTOM', version: '1.0', fields: [], allowedSources: [], allowedSubjects: [], persistencePolicy: 'TRANSIENT' as const, abstractType: false, deprecated: false, enabled: true, annotations: {} }] };
        const current = createEmptyEventFlowModel(METAMODEL.id, event.id);
        const nodeRed = JSON.stringify([
            { id: 'tab-1', type: 'tab', name: 'People flow' },
            { id: 'in-1', type: 'mqtt in', z: 'tab-1', name: 'Person input', topic: 'PersonChanged', wires: [['out-1']] },
            { id: 'out-1', type: 'debug', z: 'tab-1', name: 'Done', wires: [] }
        ]);
        const imported = importEventFlowLayer(nodeRed, 'NODE_RED_JSON', current, event.eventTypes);
        expect(imported.model.flows).toHaveLength(1);
        expect(imported.model.flows[0].nodes.some(node => node.kind === 'SOURCE' && node.eventTypeId === 'person.changed')).toBe(true);
        const flowId = imported.model.flows[0].id;
        const exported = exportEventFlowLayer('NODE_RED_JSON', imported.model, event.eventTypes);
        const roundTrip = importEventFlowLayer(exported.content, 'NODE_RED_JSON', current, event.eventTypes);
        expect(roundTrip.model.flows[0].id).toBe(flowId);
    });

    it('maps common Siddhi queries and retains full flow metadata when exported again', () => {
        const definitions = createEmptyEventModels(METAMODEL.id).definitions;
        const eventTypes = [{ id: 'input', name: 'InputStream', namespace: 'people', description: '', category: 'CUSTOM', version: '1.0', fields: [], allowedSources: [], allowedSubjects: [], persistencePolicy: 'TRANSIENT' as const, abstractType: false, deprecated: false, enabled: true, annotations: {} }, { id: 'output', name: 'OutputStream', namespace: 'people', description: '', category: 'CUSTOM', version: '1.0', fields: [], allowedSources: [], allowedSubjects: [], persistencePolicy: 'TRANSIENT' as const, abstractType: false, deprecated: false, enabled: true, annotations: {} }];
        const current = createEmptyEventFlowModel(METAMODEL.id, definitions.id);
        const imported = importEventFlowLayer('from InputStream[age >= 18] select * insert into OutputStream;', 'SIDDHI', current, eventTypes);
        expect(imported.model.flows[0].nodes.some(node => node.kind === 'FILTER')).toBe(true);
        const flowId = imported.model.flows[0].id;
        const exported = exportEventFlowLayer('SIDDHI', imported.model, eventTypes);
        expect(exported.content).toContain('@hydra-flow-json');
        const roundTrip = importEventFlowLayer(exported.content, 'SIDDHI', current, eventTypes);
        expect(roundTrip.model.flows[0].id).toBe(flowId);
    });
});
