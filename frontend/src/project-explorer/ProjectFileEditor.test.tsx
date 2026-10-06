import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ProjectFileEditor } from './ProjectFileEditor';
import type { ProjectTab } from './types';

const tab: ProjectTab = {
    path: 'docs/readme.md', name: 'readme.md', draft: '# Draft', dirty: true, loading: false, error: '',
    content: { relativePath: 'docs/readme.md', name: 'readme.md', artifactKind: 'DOCUMENTATION', ownership: 'USER_MANAGED', mediaType: 'text/markdown', charset: 'UTF-8', binary: false, content: '# Original', etag: 'etag', size: 10, lastModified: '2026-07-27T10:00:00Z', editable: true }
};

describe('ProjectFileEditor', () => {
    it('edits and saves textual project files', () => {
        const onChange = vi.fn();
        const onSave = vi.fn();
        render(<ProjectFileEditor tab={tab} onChange={onChange} onReload={vi.fn()} onSave={onSave} />);
        fireEvent.change(screen.getByLabelText('Content of readme.md'), { target: { value: '# Changed' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));
        expect(onChange).toHaveBeenCalledWith('# Changed');
        expect(onSave).toHaveBeenCalledOnce();
    });

    it('does not expose a text area for binary files', () => {
        render(<ProjectFileEditor tab={{ ...tab, content: { ...tab.content!, binary: true, editable: false } }} onChange={vi.fn()} onReload={vi.fn()} onSave={vi.fn()} />);
        expect(screen.queryByRole('textbox')).toBeNull();
        expect(screen.getByText(/cannot be edited as text/i)).toBeTruthy();
    });


    it('shows loading and backend error states without an editor', () => {
        const { rerender } = render(<ProjectFileEditor tab={{ ...tab, loading: true }} onChange={vi.fn()} onReload={vi.fn()} onSave={vi.fn()} />);
        expect(screen.getByText(/Loading readme.md/)).toBeTruthy();
        rerender(<ProjectFileEditor tab={{ ...tab, loading: false, error: 'Could not read' }} onChange={vi.fn()} onReload={vi.fn()} onSave={vi.fn()} />);
        expect(screen.getByText('Could not read')).toBeTruthy();
    });

    it('reloads an opened textual file', () => {
        const onReload = vi.fn();
        render(<ProjectFileEditor tab={tab} onChange={vi.fn()} onReload={onReload} onSave={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Reload' }));
        expect(onReload).toHaveBeenCalledOnce();
    });

    it('disables save when the draft is clean', () => {
        render(<ProjectFileEditor tab={{ ...tab, dirty: false }} onChange={vi.fn()} onReload={vi.fn()} onSave={vi.fn()} />);
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });
});
