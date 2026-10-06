import { describe, expect, it } from 'vitest';
import { createDefaultEventBinding, createDefaultEventType, createEmptyEventModels } from './eventDesignerFactory';
import {
    addEventArchitectureNode,
    autoLayoutEventArchitectureDiagram,
    createEmptyEventArchitectureDiagram,
    removeEventArchitectureNode,
    synchronizeEventArchitectureDiagram,
    toEventArchitectureFlowElements
} from './eventArchitectureDiagram';

describe('event architecture diagram', () => {
    it('starts empty and keeps the complete event catalog outside the canvas', () => {
        const models = createEmptyEventModels('people');
        const eventType = createDefaultEventType(models.definitions.namespace);
        const definitions = { ...models.definitions, eventTypes: [eventType] };
        const synchronized = synchronizeEventArchitectureDiagram(definitions, models.bindings, ['core.model.saved'],
            createEmptyEventArchitectureDiagram(definitions, models.bindings));
        expect(synchronized.nodes).toEqual([]);
        expect(toEventArchitectureFlowElements(definitions, models.bindings, ['core.model.saved'], synchronized, '', '').nodes).toEqual([]);
    });

    it('renders only explicitly placed events and derives bindings as arrows', () => {
        const models = createEmptyEventModels('people');
        const eventType = createDefaultEventType(models.definitions.namespace);
        const definitions = { ...models.definitions, eventTypes: [eventType] };
        const binding = createDefaultEventBinding('core.model.saved', eventType.id);
        const bindings = { ...models.bindings, bindings: [binding] };
        let diagram = createEmptyEventArchitectureDiagram(definitions, bindings);
        diagram = addEventArchitectureNode(diagram, 'core.model.saved', { x: 70, y: 70 });
        diagram = addEventArchitectureNode(diagram, eventType.id, { x: 400, y: 70 });
        diagram = synchronizeEventArchitectureDiagram(definitions, bindings, ['core.model.saved'], diagram);
        const visual = toEventArchitectureFlowElements(definitions, bindings, ['core.model.saved'], diagram, '', '');
        expect(visual.nodes.map(node => node.id)).toEqual(['core.model.saved', eventType.id]);
        expect(visual.nodes[0]).toMatchObject({
            position: { x: 70, y: 70 },
            style: { width: 230, height: 92 }
        });
        expect(visual.nodes[0]?.width).toBeUndefined();
        expect(visual.nodes[0]?.height).toBeUndefined();
        expect(visual.edges).toHaveLength(1);
        expect(visual.edges[0]).toMatchObject({ source: 'core.model.saved', target: eventType.id });
        expect(visual.edges[0]?.markerEnd).toBeUndefined();
        expect(visual.edges[0]?.type).toBe('hydraDirectedEdge');
        const reserved = visual.nodes.find(node => node.id === 'core.model.saved');
        expect(reserved?.data.label).toBe('Model saved');
        expect(reserved?.data.description).toMatch(/stored successfully/i);
        expect(reserved?.data.label).not.toContain('core.');
    });

    it('renders the core palette fallback even while the reserved-event catalog is unavailable', () => {
        const models = createEmptyEventModels('people');
        const placed = addEventArchitectureNode(createEmptyEventArchitectureDiagram(models.definitions, models.bindings), 'core.model.element-created', { x: 120, y: 90 });
        const synchronized = synchronizeEventArchitectureDiagram(models.definitions, models.bindings, [], placed);
        const visual = toEventArchitectureFlowElements(models.definitions, models.bindings, [], synchronized, '', '');
        expect(synchronized.nodes.map(node => node.nodeId)).toEqual(['core.model.element-created']);
        expect(visual.nodes.map(node => node.id)).toEqual(['core.model.element-created']);
    });

    it('falls back to finite canvas coordinates when viewport projection is invalid', () => {
        const models = createEmptyEventModels('people');
        const placed = addEventArchitectureNode(createEmptyEventArchitectureDiagram(models.definitions, models.bindings), 'core.model.element-created', { x: Number.NaN, y: Number.POSITIVE_INFINITY });
        expect(placed.nodes[0]).toMatchObject({ x: 70, y: 70 });
        const visual = toEventArchitectureFlowElements(models.definitions, models.bindings, [], placed, '', '');
        expect(visual.nodes[0]?.position).toEqual({ x: 70, y: 70 });
    });

    it('preserves manual positions when canonical documents change', () => {
        const models = createEmptyEventModels('people');
        const eventType = createDefaultEventType(models.definitions.namespace);
        const definitions = { ...models.definitions, eventTypes: [eventType] };
        const empty = createEmptyEventArchitectureDiagram(definitions, models.bindings);
        const placed = addEventArchitectureNode(empty, eventType.id, { x: 999, y: 777 });
        const synchronized = synchronizeEventArchitectureDiagram(definitions, models.bindings, [], placed);
        expect(synchronized.nodes.find(node => node.nodeId === eventType.id)).toMatchObject({ x: 999, y: 777 });
    });

    it('removes a card without deleting its semantic event contract', () => {
        const models = createEmptyEventModels('people');
        const eventType = createDefaultEventType(models.definitions.namespace);
        const definitions = { ...models.definitions, eventTypes: [eventType] };
        const placed = addEventArchitectureNode(createEmptyEventArchitectureDiagram(definitions, models.bindings), eventType.id);
        const removed = removeEventArchitectureNode(placed, models.bindings, eventType.id);
        expect(removed.nodes).toEqual([]);
        expect(definitions.eventTypes).toContain(eventType);
    });

    it('auto layouts only the cards explicitly placed on the canvas', () => {
        const models = createEmptyEventModels('people');
        const eventType = createDefaultEventType(models.definitions.namespace);
        const definitions = { ...models.definitions, eventTypes: [eventType] };
        const placed = addEventArchitectureNode(createEmptyEventArchitectureDiagram(definitions, models.bindings), eventType.id);
        const diagram = autoLayoutEventArchitectureDiagram(definitions, models.bindings, ['core.model.saved'], placed);
        expect(diagram.nodes.map(node => node.nodeId)).toEqual([eventType.id]);
        expect(diagram.nodes[0]).toMatchObject({ x: 70, y: 70 });
    });

    it('materializes a visible edge when a canonical binding is created', () => {
        const models = createEmptyEventModels('people');
        const eventType = createDefaultEventType(models.definitions.namespace);
        const definitions = { ...models.definitions, eventTypes: [eventType] };
        const sourceId = 'core.validation.problem-resolved';
        const diagram = addEventArchitectureNode(addEventArchitectureNode(createEmptyEventArchitectureDiagram(definitions, models.bindings), sourceId), eventType.id);
        const nextBindings = { ...models.bindings, bindings: [{ id: 'binding:visual', name: 'Visual binding', sourceEventTypeId: sourceId, targetEventTypeId: eventType.id, conditionExpressionId: '', payloadMappings: [], enabled: true, errorPolicy: 'REPORT' as const, annotations: {} }] };
        const synchronized = synchronizeEventArchitectureDiagram(definitions, nextBindings, [sourceId], diagram);
        expect(synchronized.edges).toHaveLength(1);
        expect(synchronized.edges[0]).toMatchObject({ edgeId: 'binding:visual', points: [] });
    });

});
