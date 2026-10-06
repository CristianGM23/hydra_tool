import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MetamodelValidationDialog } from './MetamodelValidationDialog';

describe('MetamodelValidationDialog', () => {
    it('presents exhaustive problems at a high level', () => {
        render(<MetamodelValidationDialog analysis={{ metamodelId: 'people', metamodelName: 'People', fingerprint: 'abcdef123456', validatedAt: '2026-08-12T10:00:00Z', valid: false, generationReady: false, summary: { errors: 1, warnings: 0, total: 1 }, diagnostics: [{ severity: 'ERROR', code: 'RELATION_OPPOSITE_UNKNOWN', category: 'Relation', title: 'Opposite relation cannot be resolved', whatHappened: 'Unknown opposite relation id: relation:assignee', location: 'relation [relation:assignedTasks]', situation: 'The canonical structure is inconsistent.', involvedElements: ['relation:assignedTasks', 'relation:assignee'], howToFix: ['Select an existing reverse relation or remove the opposite link.'], exportImpact: 'Blocks complete project generation.', path: '$.relations[relation:assignedTasks]' }] }} persisted onClose={vi.fn()} onValidateStored={vi.fn()} busy={false} />);
        expect(screen.getByText('What happened')).toBeTruthy();
        expect(screen.getByText('Where')).toBeTruthy();
        expect(screen.getByText('Situation')).toBeTruthy();
        expect(screen.getByText('Elements involved')).toBeTruthy();
        expect(screen.getByText('How to fix it')).toBeTruthy();
        expect(screen.getByText('relation:assignee')).toBeTruthy();
    });
});
