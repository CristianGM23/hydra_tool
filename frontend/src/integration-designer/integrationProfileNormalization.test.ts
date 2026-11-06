import { describe, expect, it } from 'vitest';
import { normalizeIntegrationProfile } from './integrationProfileNormalization';

describe('integration profile current contract', () => {
    it('rejects incomplete internal profiles instead of inventing missing fields', () => {
        expect(() => normalizeIntegrationProfile({ schemaVersion: '1.0', id: 'p', metamodelId: 'm', name: 'P' } as never)).toThrow(/collections|target/i);
    });
    it('treats collections omitted by the server (empty lists) as empty instead of failing', () => {
        const profile = normalizeIntegrationProfile({ schemaVersion: '1.0', id: 'p', metamodelId: 'm', name: 'P', target: { technology: 'EMF' }, features: [], artifacts: [], injectionPolicy: {}, annotations: {} } as never);
        expect(profile.mappings).toEqual([]);
        expect(profile.requiredCapabilities).toEqual([]);
    });
    it('still rejects a collection that is present but not a list', () => {
        expect(() => normalizeIntegrationProfile({ schemaVersion: '1.0', id: 'p', metamodelId: 'm', name: 'P', target: { technology: 'EMF' }, features: 'x', artifacts: [], injectionPolicy: {}, annotations: {} } as never)).toThrow(/collections/i);
    });
    it('restores texts the server omits (empty strings) so the designer can read them', () => {
        const profile = normalizeIntegrationProfile({ schemaVersion: '1.0', id: 'p', metamodelId: 'm', name: 'P', target: { technology: 'EMF' }, features: [], artifacts: [{ kind: 'METAMODEL' }], mappings: [{ kind: 'CLASS' }], injectionPolicy: {}, annotations: {} } as never);
        expect(profile.target.projectId).toBe('');
        expect(profile.target.sourceRoots).toEqual([]);
        expect(profile.injectionPolicy.generatedRoot).toBe('');
        expect(profile.mappings[0].canonicalElementId.trim()).toBe('');
        expect(profile.mappings[0].hostReference.path).toBe('');
    });
});
