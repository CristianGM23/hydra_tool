import { describe, expect, it } from 'vitest';
import {
    changedHydraPersistedIdentities,
    findHydraOverwriteConflicts,
    hydraContentSignature,
    sameHydraPersistedIdentity
} from './hydraOverwritePolicy';

const original = { layer: 'Metamodel', entityKind: 'METAMODEL', stableId: 'people-model', uniqueName: 'People' };

describe('hydraOverwritePolicy', () => {
    it('recognizes the same stable ID after trimming accidental whitespace', () => {
        expect(sameHydraPersistedIdentity({ ...original, stableId: ' people-model ' }, original)).toBe(true);
    });

    it('does not collapse identifier case that the backend can distinguish', () => {
        expect(sameHydraPersistedIdentity({ ...original, stableId: 'PEOPLE-MODEL', uniqueName: 'Different' }, original)).toBe(false);
    });

    it('can recognize the same unique name when the stable ID was edited', () => {
        expect(sameHydraPersistedIdentity({ ...original, stableId: 'people-model-v2', uniqueName: ' People ' }, original)).toBe(true);
    });

    it('does not report a conflict when no matching element exists in persistence', () => {
        expect(findHydraOverwriteConflicts([original], [])).toEqual([]);
    });

    it('does not treat unchanged siblings in a persisted container as overwrite candidates', () => {
        const persisted = {
            layer: 'Event flows', entityKind: 'EVENT_FLOW', stableId: 'flow-1', uniqueName: 'Existing',
            contentSignature: hydraContentSignature({ id: 'flow-1', name: 'Existing', enabled: true })
        };
        expect(changedHydraPersistedIdentities([{ ...persisted }], [persisted])).toEqual([]);
        const added = {
            layer: 'Event flows', entityKind: 'EVENT_FLOW', stableId: 'flow-2', uniqueName: 'New',
            contentSignature: hydraContentSignature({ id: 'flow-2', name: 'New', enabled: true })
        };
        expect(findHydraOverwriteConflicts([persisted, added], [persisted])).toEqual([]);
    });

    it('detects a real replacement by ID or name', () => {
        const changed = { ...original, contentSignature: hydraContentSignature({ name: 'Changed' }) };
        const persisted = { ...original, contentSignature: hydraContentSignature({ name: 'Before' }) };
        expect(findHydraOverwriteConflicts([changed], [persisted])[0]?.matchedBy).toEqual(expect.arrayContaining(['STABLE_ID', 'UNIQUE_NAME']));
    });

    it('can use semantic signatures only for layers that explicitly enable them', () => {
        const persisted = { layer: 'Validations and quick fixes', entityKind: 'VALIDATION_RULE', stableId: 'r1', uniqueName: 'First', semanticSignature: 'same-condition' };
        const next = { layer: 'Validations and quick fixes', entityKind: 'VALIDATION_RULE', stableId: 'r2', uniqueName: 'Second', semanticSignature: 'same-condition' };
        expect(findHydraOverwriteConflicts([next], [persisted], false)).toEqual([]);
        expect(findHydraOverwriteConflicts([next], [persisted], true)[0]?.matchedBy).toContain('SEMANTIC_SIGNATURE');
    });

    it('keeps different entity kinds independent inside a compound layer', () => {
        const validation = { layer: 'Validations and quick fixes', entityKind: 'VALIDATION_RULE', stableId: 'shared', uniqueName: 'Shared' };
        const quickFix = { layer: 'Validations and quick fixes', entityKind: 'QUICK_FIX', stableId: 'shared', uniqueName: 'Shared' };
        expect(findHydraOverwriteConflicts([quickFix], [validation], true)).toEqual([]);
    });
});
