import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

vi.mock('./exportApi', () => ({
    downloadMetamodelExport: vi.fn().mockResolvedValue(undefined),
    downloadMetamodelExportBundle: vi.fn().mockResolvedValue(undefined)
}));

import { MetamodelExportDialog } from './MetamodelExportDialog';

const formats = [
    { id: 'svg', displayName: 'SVG', extension: '.svg', category: 'DIAGRAM' as const }
];

describe('MetamodelExportDialog window behavior', () => {
    it('renders through a body portal and uses the top-aligned window backdrop', () => {
        const host = document.createElement('div');
        document.body.appendChild(host);
        render(<MetamodelExportDialog formats={formats} metamodelId="sample" onClose={vi.fn()} />, { container: host });

        expect(document.body.querySelector('.metamodel-export-backdrop')).not.toBeNull();
        expect(host.querySelector('.metamodel-export-backdrop')).toBeNull();
        expect(screen.getByRole('dialog')).toHaveClass('metamodel-export-dialog-window');
    });

    it('maximizes and restores the export dialog', () => {
        render(<MetamodelExportDialog formats={formats} metamodelId="sample" onClose={vi.fn()} />);
        const dialog = screen.getByRole('dialog');

        fireEvent.click(screen.getByRole('button', { name: 'Maximize export dialog' }));
        expect(dialog).toHaveClass('maximized');

        fireEvent.click(screen.getByRole('button', { name: 'Restore export dialog' }));
        expect(dialog).not.toHaveClass('maximized');
    });

    it('closes from the upper-right close button', () => {
        const onClose = vi.fn();
        render(<MetamodelExportDialog formats={formats} metamodelId="sample" onClose={onClose} />);

        fireEvent.click(screen.getByRole('button', { name: 'Close export dialog' }));

        expect(onClose).toHaveBeenCalledOnce();
    });
});
