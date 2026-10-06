import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DesignerDropdown } from '../designer-ui/DesignerDropdown';
import type { MetaModel } from '../metamodel-designer/types';
import { VisualDefinitionImportButton } from './VisualDefinitionImportDialog';

vi.mock('../metamodel-designer/api', () => ({ listCanonicalMetamodels: vi.fn().mockResolvedValue([]) }));

const metamodel = { id: 'people', name: 'People', schemaVersion: '1.0', classes: [], relations: [] } as unknown as MetaModel;

describe('visual definition import launcher', () => {
    it('keeps the launcher mounted long enough for the modal to open from a command dropdown', async () => {
        render(<DesignerDropdown label="Interchange"><VisualDefinitionImportButton metamodel={metamodel} onImported={vi.fn()} /></DesignerDropdown>);
        fireEvent.click(screen.getByRole('button', { name: 'Interchange' }));
        fireEvent.click(screen.getByRole('button', { name: 'Import visual definition' }));
        expect(await screen.findByRole('dialog', { name: 'Import visual definition' })).toBeTruthy();
        expect(screen.getByRole('heading', { name: 'Import source' })).toBeTruthy();
    });
});
