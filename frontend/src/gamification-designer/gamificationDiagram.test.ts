import { describe, expect, it } from 'vitest';
import { DEFAULT_HYDRA_SOURCE_PORT, DEFAULT_HYDRA_TARGET_PORT } from '../designer-ui/HydraConnectionVisual';
import { createEmptyGamificationModels, createPointType, createRule } from './gamificationFactory';
import {
    addGamificationEvidenceNode,
    autoLayoutGamificationDiagram,
    createEmptyGamificationDiagram,
    synchronizeGamificationDiagram,
    toGamificationFlowElements
} from './gamificationDiagram';

describe('gamification visual diagram', () => {
    it('derives verified evidence, rules and progression links', () => {
        const models = createEmptyGamificationModels('people');
        const point = createPointType();
        const definitions = { ...models.definitions, pointTypes: [point] };
        const rule = createRule('core.validation.violation-resolved', point.id);
        const bindings = { ...models.bindings, rules: [rule] };
        const diagram = synchronizeGamificationDiagram(definitions, bindings, null, createEmptyGamificationDiagram(definitions, bindings));
        const visual = toGamificationFlowElements(definitions, bindings, null, diagram, '', '');
        expect(visual.nodes.some(node => node.data.kind === 'EVIDENCE')).toBe(true);
        expect(visual.nodes.some(node => node.data.kind === 'RULE')).toBe(true);
        expect(visual.edges.some(edge => edge.source.includes('evidence:') && edge.target.includes('rule:'))).toBe(true);
        const evidence = visual.nodes.find(node => node.data.kind === 'EVIDENCE');
        expect(evidence?.data.label).toBe('Problem resolved');
        expect(evidence?.data.label).not.toContain('core.');
        expect(evidence?.data.subtitle).toMatch(/previously reported validation problem disappears/i);
        expect(visual.edges.every(edge => edge.markerEnd === undefined && edge.type === 'hydraDirectedEdge')).toBe(true);
        expect(visual.edges.every(edge => edge.sourceHandle === DEFAULT_HYDRA_SOURCE_PORT && edge.targetHandle === DEFAULT_HYDRA_TARGET_PORT && edge.zIndex === 8)).toBe(true);
        expect(visual.nodes.every(node => Array.isArray(node.handles) && node.handles.length > 0)).toBe(true);
    });


    it('places an event as gamification evidence before a rule is connected', () => {
        const models = createEmptyGamificationModels('people');
        const empty = createEmptyGamificationDiagram(models.definitions, models.bindings);
        const placed = addGamificationEvidenceNode(empty, 'core.model.element-created', { x: 125, y: 240 });
        const synchronized = synchronizeGamificationDiagram(models.definitions, models.bindings, null, placed, ['core.model.element-created']);
        const visual = toGamificationFlowElements(models.definitions, models.bindings, null, synchronized, '', '');
        const evidence = visual.nodes.find(node => node.id === 'evidence:core.model.element-created');
        expect(evidence).toBeDefined();
        expect(evidence?.position).toEqual({ x: 125, y: 240 });
        expect(evidence?.data.label).toBe('Model element created');
    });

    it('preserves node geometry and can replace it with deterministic auto layout', () => {
        const models = createEmptyGamificationModels('people');
        const point = createPointType();
        const definitions = { ...models.definitions, pointTypes: [point] };
        const initial = synchronizeGamificationDiagram(definitions, models.bindings, null, createEmptyGamificationDiagram(definitions, models.bindings));
        const moved = { ...initial, nodes: initial.nodes.map(node => ({ ...node, x: 999 })) };
        expect(synchronizeGamificationDiagram(definitions, models.bindings, null, moved).nodes[0]?.x).toBe(999);
        expect(autoLayoutGamificationDiagram(definitions, models.bindings, null, moved).nodes[0]?.x).not.toBe(999);
    });
});
