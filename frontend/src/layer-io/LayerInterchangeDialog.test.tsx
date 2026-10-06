import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

vi.mock('../metamodel-designer/importApi', () => ({
    analyzeMetamodelImport: vi.fn(),
    importAndSaveMetamodel: vi.fn(),
    importPreparedMetamodel: vi.fn()
}));

import { analyzeMetamodelImport } from '../metamodel-designer/importApi';
import type { MetaModel } from '../metamodel-designer/types';
import { LayerInterchangeDialog } from './LayerInterchangeDialog';

const METAMODEL: MetaModel = {
    schemaVersion: '2.1',
    id: 'active',
    name: 'Active',
    classes: [],
    enums: [],
    dataTypes: [],
    relations: [],
    annotations: {}
};

const PREVIEW = {
    detectedFormat: 'emf',
    supportLevel: 'EXACT',
    metamodel: { ...METAMODEL, id: 'associated', name: 'Associated' },
    targets: { schemaVersion: '1.0', metamodelId: 'associated', targets: [] },
    diagram: { schemaVersion: '2.0', metamodelId: 'associated', nodes: [], edges: [], annotations: {} },
    warnings: [],
    statistics: { classes: 0, enums: 0, dataTypes: 0, attributes: 0, relations: 0, diagramNodes: 0, diagramEdges: 0 }
} as const;

describe('LayerInterchangeDialog associated metamodel', () => {
    it('analyzes an attached metamodel automatically and can detach it', async () => {
        vi.mocked(analyzeMetamodelImport).mockResolvedValue(PREVIEW as never);
        render(<LayerInterchangeDialog
            mode="IMPORT"
            layerName="Validation"
            description="Import validation rules"
            formats={[{ id: 'EVL', label: 'EVL', description: 'Epsilon Validation Language', extensions: ['.evl'] }]}
            defaultFormat="EVL"
            currentMetamodel={METAMODEL}
            onClose={vi.fn()}
            onImport={vi.fn()}
        />);

        const associatedInput = screen.getByLabelText('Associated metamodel file') as HTMLInputElement;
        fireEvent.change(associatedInput, { target: { files: [new File(['package project;'], 'project.emf')] } });

        await waitFor(() => expect(analyzeMetamodelImport).toHaveBeenCalledTimes(1));
        expect(await screen.findByText('Automatic analysis complete.')).toBeTruthy();
        expect(screen.getByText('project.emf')).toBeTruthy();

        fireEvent.click(screen.getByRole('button', { name: 'Detach metamodel' }));

        expect(screen.queryByText('project.emf')).toBeNull();
        expect(screen.queryByText('Automatic analysis complete.')).toBeNull();
        expect(associatedInput.value).toBe('');
    });
});
