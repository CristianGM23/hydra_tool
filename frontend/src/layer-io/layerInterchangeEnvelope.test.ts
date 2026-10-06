import { describe, expect, it } from 'vitest';
import { makeLayerInterchangeEnvelope, unwrapLayerInterchangeEnvelope } from './layerInterchangeEnvelope';

describe('HYDRA layer interchange envelope', () => {
    it('creates a deterministic versioned envelope', () => {
        const envelope = makeLayerInterchangeEnvelope('EVENT_FLOW', 'people', { id: 'flow-one' }, ['z', 'a', 'a', '']);
        expect(envelope).toEqual({
            schemaVersion: '1.0',
            kind: 'EVENT_FLOW',
            metamodelId: 'people',
            dependencies: ['a', 'z'],
            payload: { id: 'flow-one' },
            annotations: { 'hydra.interchange': 'layer-envelope-1.0' }
        });
    });

    it('unwraps a matching package and reports metamodel drift without rejecting it', () => {
        const content = JSON.stringify(makeLayerInterchangeEnvelope('VALIDATION', 'library', { id: 'rules' }, ['expressions']));
        const result = unwrapLayerInterchangeEnvelope<{ id: string }>(content, 'VALIDATION', 'people');
        expect(result.enveloped).toBe(true);
        expect(result.payload.id).toBe('rules');
        expect(result.dependencies).toEqual(['expressions']);
        expect(result.warnings.join(' ')).toContain('library');
        expect(result.warnings.join(' ')).toContain('people');
    });

    it('accepts legacy raw HYDRA JSON without an envelope', () => {
        const result = unwrapLayerInterchangeEnvelope<{ id: string }>(JSON.stringify({ id: 'legacy' }), 'EVENTS', 'people');
        expect(result).toMatchObject({ payload: { id: 'legacy' }, enveloped: false, warnings: [] });
    });

    it('rejects a package for another layer kind', () => {
        const content = JSON.stringify(makeLayerInterchangeEnvelope('GAMIFICATION', 'people', { id: 'game' }));
        expect(() => unwrapLayerInterchangeEnvelope(content, 'EVENT_FLOW')).toThrow(/GAMIFICATION.*EVENT_FLOW/);
    });
});
