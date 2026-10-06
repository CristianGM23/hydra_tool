import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { downloadMetamodelExport, downloadMetamodelExportBundle } from './exportApi';
import { MetamodelExportDialog } from './MetamodelExportDialog';

vi.mock('./exportApi', () => ({
    downloadMetamodelExport: vi.fn().mockResolvedValue(undefined),
    downloadMetamodelExportBundle: vi.fn().mockResolvedValue(undefined)
}));

const formats = [
    { id: 'xml', displayName: 'Neutral XML', extension: '.xml', category: 'SEMANTIC' as const },
    { id: 'svg', displayName: 'SVG', extension: '.svg', category: 'DIAGRAM' as const }
];

describe('MetamodelExportDialog configurable exports', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('shows options and selected bundle action', () => {
        render(<MetamodelExportDialog formats={formats} metamodelId="sample" onClose={vi.fn()} />);

        expect(screen.getByText('Export options')).toBeTruthy();
        expect(screen.getByLabelText('Include saved layout')).toBeTruthy();
        expect(screen.getByRole('button', { name: 'Download selected (1)' })).toBeTruthy();
    });

    it('clears and restores the format selection', () => {
        render(<MetamodelExportDialog formats={formats} metamodelId="sample" onClose={vi.fn()} />);

        fireEvent.click(screen.getByRole('button', { name: 'Clear' }));
        expect((screen.getByRole('button', { name: 'Download selected (0)' }) as HTMLButtonElement).disabled).toBe(true);

        fireEvent.click(screen.getByRole('button', { name: 'Select all' }));
        expect((screen.getByRole('button', { name: 'Download selected (2)' }) as HTMLButtonElement).disabled).toBe(false);
    });

    it('downloads one format with the selected options', async () => {
        render(<MetamodelExportDialog formats={formats} metamodelId="sample" onClose={vi.fn()} />);
        fireEvent.change(screen.getByLabelText('Image scale'), { target: { value: '4' } });
        fireEvent.change(screen.getByLabelText('Code package / module'), { target: { value: 'com.example.generated' } });
        fireEvent.click(screen.getAllByRole('button', { name: 'Download' })[0]);

        await vi.waitFor(() => expect(vi.mocked(downloadMetamodelExport)).toHaveBeenCalledWith('sample', expect.objectContaining({ format: 'xml', options: expect.objectContaining({ imageScale: 4, codePackageName: 'com.example.generated' }) })));
    });

    it('downloads only selected formats in the bundle', async () => {
        render(<MetamodelExportDialog formats={formats} metamodelId="sample" onClose={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Clear' }));
        fireEvent.click(screen.getByRole('checkbox', { name: /SVG/ }));
        fireEvent.click(screen.getByRole('button', { name: 'Download selected (1)' }));

        await vi.waitFor(() => expect(vi.mocked(downloadMetamodelExportBundle)).toHaveBeenCalledWith('sample', expect.objectContaining({ formats: ['svg'] })));
    });

    it('closes the dialog when no export is running', () => {
        const onClose = vi.fn();
        render(<MetamodelExportDialog formats={formats} metamodelId="sample" onClose={onClose} />);
        fireEvent.click(screen.getByRole('button', { name: 'Close' }));

        expect(onClose).toHaveBeenCalledOnce();
    });
});
