import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ProjectTabs } from './ProjectTabs';
import type { ProjectTab } from './types';

const tabs: ProjectTab[] = [
    { path: 'docs/a.md', name: 'a.md', content: null, draft: '', dirty: false, loading: false, error: '' },
    { path: 'docs/b.md', name: 'b.md', content: null, draft: '', dirty: true, loading: false, error: '' },
];

describe('ProjectTabs', () => {
    it('does not duplicate the active HYDRA layer as a project tab', () => {
        const { container } = render(<ProjectTabs activePath="" tabs={[]} onActivate={vi.fn()} onClose={vi.fn()} />);
        expect(container.querySelector('.project-tabs')).toBeNull();
    });

    it('activates an opened file tab', () => {
        const onActivate = vi.fn();
        render(<ProjectTabs activePath="" tabs={tabs} onActivate={onActivate} onClose={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'a.md' }));
        expect(onActivate).toHaveBeenCalledWith('docs/a.md');
    });

    it('marks dirty tabs and closes the requested path', () => {
        const onClose = vi.fn();
        render(<ProjectTabs activePath="docs/b.md" tabs={tabs} onActivate={vi.fn()} onClose={onClose} />);
        expect(screen.getByRole('button', { name: 'b.md *' })).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: 'Close b.md' }));
        expect(onClose).toHaveBeenCalledWith('docs/b.md');
    });
});
