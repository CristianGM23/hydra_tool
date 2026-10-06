import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

vi.mock('./importApi', () => ({ analyzeMetamodelImport: vi.fn(), importAndSaveMetamodel: vi.fn(), downloadMetamodelImportCombinedLog: vi.fn(), downloadMetamodelImportStageLog: vi.fn() }));

import { MetamodelImportDialog } from './MetamodelImportDialog';
import type { MetamodelImportFormatDescriptor } from './importTypes';

const formats: MetamodelImportFormatDescriptor[] = [
    { id: 'ecore', displayName: 'Ecore', category: 'SEMANTIC', extensions: ['.ecore'], supportLevel: 'EXACT' }
];

const props = {
    formats,
    onClose: vi.fn(),
    onLoad: vi.fn(),
    onSaved: vi.fn()
};

describe('MetamodelImportDialog window behavior', () => {
    it('renders the dialog through a body portal', () => {
        const host = document.createElement('div');
        document.body.appendChild(host);
        render(<MetamodelImportDialog {...props} />, { container: host });

        expect(document.body.querySelector('.metamodel-import-backdrop')).not.toBeNull();
        expect(host.querySelector('.metamodel-import-backdrop')).toBeNull();
    });

    it('maximizes and restores the dialog', () => {
        render(<MetamodelImportDialog {...props} />);
        const dialog = screen.getByRole('dialog');

        fireEvent.click(screen.getByRole('button', { name: 'Maximize import dialog' }));
        expect(dialog.className).toContain('maximized');

        fireEvent.click(screen.getByRole('button', { name: 'Restore import dialog' }));
        expect(dialog.className).not.toContain('maximized');
    });


    it('shows contextual help for all import fields and a file-system source button', () => {
        render(<MetamodelImportDialog {...props} />);
        expect(screen.getByRole('button', { name: /File system/ })).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: 'Information about Format' }));
        expect(screen.getByRole('tooltip')).toHaveTextContent('force a specific importer');
        expect(screen.getByRole('button', { name: 'Information about Strict mode' })).toBeTruthy();
    });

    it('routes update-site repositories to the independent p2 recovery center', () => {
        const onClose = vi.fn();
        window.location.hash = '#/metamodel';
        render(<MetamodelImportDialog {...props} onClose={onClose} />);

        fireEvent.click(screen.getByRole('button', { name: 'Open update-site recovery' }));

        expect(onClose).toHaveBeenCalledTimes(1);
        expect(window.location.hash).toBe('#/update-site-imports');
    });

    it('closes from the upper-right close button', () => {
        const onClose = vi.fn();
        render(<MetamodelImportDialog {...props} onClose={onClose} />);

        fireEvent.click(screen.getByRole('button', { name: 'Close import dialog' }));

        expect(onClose).toHaveBeenCalledTimes(1);
    });
});
