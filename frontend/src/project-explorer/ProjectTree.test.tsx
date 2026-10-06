import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ProjectTree } from './ProjectTree';
import type { ProjectTreeNode } from './types';

const file: ProjectTreeNode = {
    id: 'file:metamodels/people.metamodel.json', name: 'people.metamodel.json', relativePath: 'metamodels/people.metamodel.json',
    nodeType: 'FILE', artifactKind: 'METAMODEL', ownership: 'USER_MANAGED', status: 'NORMAL', editable: true,
    size: 20, lastModified: '2026-07-27T10:00:00Z', etag: 'etag', children: []
};
const root: ProjectTreeNode = {
    id: 'project:people', name: 'People', relativePath: '', nodeType: 'PROJECT', artifactKind: 'PROJECT_DESCRIPTOR',
    ownership: 'OWNED', status: 'NORMAL', editable: false, size: 0, lastModified: null, etag: '', children: [
        { id: 'group:metamodels', name: 'Metamodels', relativePath: 'metamodels', nodeType: 'GROUP', artifactKind: 'GENERIC_FILE', ownership: 'USER_MANAGED', status: 'NORMAL', editable: false, size: 0, lastModified: null, etag: '', children: [file] }
    ]
};

describe('ProjectTree', () => {
    it('opens files on double click and reports selection', () => {
        const onOpen = vi.fn();
        const onSelect = vi.fn();
        render(<ProjectTree changedPaths={new Set()} root={root} selectedPath="" onOpen={onOpen} onSelect={onSelect} />);
        const button = screen.getByRole('button', { name: /people.metamodel.json/ });
        fireEvent.click(button);
        fireEvent.doubleClick(button);
        expect(onSelect).toHaveBeenCalledWith(file);
        expect(onOpen).toHaveBeenCalledWith(file);
    });

    it('marks externally changed paths', () => {
        render(<ProjectTree changedPaths={new Set([file.relativePath])} root={root} selectedPath={file.relativePath} onOpen={vi.fn()} onSelect={vi.fn()} />);
        expect(screen.getByLabelText('Changed externally')).toBeTruthy();
    });


    it('collapses and expands a logical group', () => {
        render(<ProjectTree changedPaths={new Set()} root={root} selectedPath="" onOpen={vi.fn()} onSelect={vi.fn()} />);
        const group = screen.getByRole('button', { name: /Metamodels/ });
        expect(screen.getByRole('button', { name: /people.metamodel.json/ })).toBeTruthy();
        fireEvent.click(group);
        expect(screen.queryByRole('button', { name: /people.metamodel.json/ })).toBeNull();
        fireEvent.click(group);
        expect(screen.getByRole('button', { name: /people.metamodel.json/ })).toBeTruthy();
    });

    it('marks generated files with a generated badge', () => {
        const generated = { ...file, id: 'file:generated', relativePath: 'generated/drawio/people.drawio', name: 'people.drawio', artifactKind: 'GENERATED_DRAWIO' as const, ownership: 'GENERATED' as const };
        render(<ProjectTree changedPaths={new Set()} root={{ ...root, children: [{ ...root.children[0], children: [generated] }] }} selectedPath="" onOpen={vi.fn()} onSelect={vi.fn()} />);
        expect(screen.getByText('gen')).toBeTruthy();
    });


    it('renders semantic icons for the project, folders and artifact files', () => {
        const { container } = render(<ProjectTree changedPaths={new Set()} root={root} selectedPath="" onOpen={vi.fn()} onSelect={vi.fn()} />);
        expect(container.querySelector('.project-tree-icon-project svg')).toBeTruthy();
        expect(container.querySelector('.project-tree-icon-group svg')).toBeTruthy();
        expect(screen.getByText('MM')).toBeTruthy();
    });

    it('does not open a directory on double click', () => {
        const onOpen = vi.fn();
        render(<ProjectTree changedPaths={new Set()} root={root} selectedPath="" onOpen={onOpen} onSelect={vi.fn()} />);
        fireEvent.doubleClick(screen.getByRole('button', { name: /Metamodels/ }));
        expect(onOpen).not.toHaveBeenCalled();
    });
});
