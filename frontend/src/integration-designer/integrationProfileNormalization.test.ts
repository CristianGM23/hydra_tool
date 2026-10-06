import { describe, expect, it } from 'vitest';
import { normalizeIntegrationProfile } from './integrationProfileNormalization';

describe('integration profile current contract', () => {
    it('rejects incomplete internal profiles instead of inventing missing fields', () => {
        expect(() => normalizeIntegrationProfile({ schemaVersion: '1.0', id: 'p', metamodelId: 'm', name: 'P' } as never)).toThrow(/collections|target/i);
    });
});
