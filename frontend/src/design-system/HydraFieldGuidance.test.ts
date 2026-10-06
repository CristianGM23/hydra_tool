import { describe, expect, it } from 'vitest';
import { fieldGuidance, shouldEnrichFieldHelp } from './HydraFieldGuidance';

describe('HYDRA contextual field guidance', () => {
    it('enriches concise field help without expanding already detailed copy', () => {
        expect(shouldEnrichFieldHelp('Stable mapping identifier.')).toBe(true);
        expect(shouldEnrichFieldHelp('A'.repeat(141))).toBe(false);
    });

    it('explains stable identifiers and semantic references in user terms', () => {
        expect(fieldGuidance('Rule ID', 'Stable identifier used by references.', 'en', false).choose).toContain('stable');
        expect(fieldGuidance('Mapping ID', 'Stable mapping identifier.', 'es', false).impact).toContain('referencias');
        expect(fieldGuidance('Target class', 'Metamodel class represented by this element.', 'es', true).impact).toContain('elemento semántico');
    });

    it('uses domain-specific advice for visual, event and gamification fields', () => {
        expect(fieldGuidance('Border color', 'Outline color.', 'es', false).choose).toContain('vista previa');
        expect(fieldGuidance('Event payload', 'Portable event data.', 'en', false).impact).toContain('EventFlows');
        expect(fieldGuidance('Reward', 'Visible reward name.', 'es', false).choose).toContain('evidencias verificables');
    });
});
