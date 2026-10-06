import { describe, expect, it } from 'vitest';
import type { MetaModel } from '../metamodel-designer/types';
import { decorateChanges, diagnosticExplanation, parseExternalChanges, recommendedResolution } from './representableImportGuidance';

const metamodel: MetaModel = {
    schemaVersion: '2.1', id: 'shop', name: 'Shop', annotations: {}, enums: [], dataTypes: [], relations: [],
    classes: [
        { id: 'class:Customer', name: 'Customer', kind: 'CLASS', attributes: [], operations: [], annotations: {} },
        { id: 'class:Order', name: 'Order', kind: 'CLASS', attributes: [], operations: [], annotations: {} }
    ]
};

const candidate = { externalId: 'c1', changeKind: 'ADD' as const, nativeKind: 'EClass', qualifiedName: 'external.Customer', properties: {} };

describe('representable import guidance', () => {
    it('recommends an existing class when the external class already has an exact semantic name match', () => {
        expect(recommendedResolution(candidate, metamodel)).toBe('EXISTING:class:Customer');
        const decorated = decorateChanges([candidate], { c1: 'EXISTING:class:Customer' });
        expect(decorated[0].properties).toMatchObject({ 'hydra:resolution': 'MAP_EXISTING', 'hydra:targetElementId': 'class:Customer' });
    });

    it('validates the normalized source before showing a guided change card', () => {
        expect(parseExternalChanges('[{"externalId":"c1","changeKind":"ADD","nativeKind":"EClass","qualifiedName":"x.Customer","properties":{}}]').valid).toBe(true);
        expect(parseExternalChanges('[{"changeKind":"ADD"}]')).toMatchObject({ valid: false, error: expect.stringContaining('externalId') });
    });

    it('describes green FULL checks as compatibility success rather than an incident', () => {
        const explanation = diagnosticExplanation({ externalId: 'c1', representability: 'FULL', code: 'REPRESENTATION_FULL', message: '', suggestion: '' }, candidate);
        expect(explanation.title).toBe('Compatible change');
        expect(explanation.explanation).toContain('No semantic loss');
    });

    it('turns blocking diagnostics into a specific problem and corrective action before technical details', () => {
        const unsupported = diagnosticExplanation({ externalId: 'c1', representability: 'NONE', code: 'NATIVE_KIND_UNSUPPORTED', message: 'technical detail', suggestion: 'technical suggestion' }, candidate);
        expect(unsupported.title).toBe('Unsupported external element type');
        expect(unsupported.affected).toBe('external.Customer');
        expect(unsupported.explanation).toContain('does not know how to convert');
        expect(unsupported.action).toContain('HYDRA mapper');
        const missingTarget = diagnosticExplanation({ externalId: 'c1', representability: 'NONE', code: 'TARGET_ELEMENT_MISSING', message: '', suggestion: '' }, candidate);
        expect(missingTarget.title).toBe('Existing destination is missing');
        expect(missingTarget.action).toContain('choose the existing class');
    });
});
