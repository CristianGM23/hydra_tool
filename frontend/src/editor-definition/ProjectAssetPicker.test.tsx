import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjectAssetPicker } from './ProjectAssetPicker';
import { isImageAsset } from './projectAssetSupport';
import * as projectApi from '../project-explorer/projectApi';

vi.mock('../project-explorer/projectApi', () => ({
    loadProjectTree: vi.fn(),
    uploadProjectFile: vi.fn(),
    projectFileDownloadUrl: (path: string) => `/download/${path}`,
    projectFileVisualUrl: (path: string) => `/visual/${path}`
}));

const tree = {
    id: 'root', name: 'project', relativePath: '', nodeType: 'DIRECTORY', etag: '', size: 0, modifiedAt: '', children: [
        { id: 'svg', name: 'department.svg', relativePath: 'assets/notation/department.svg', nodeType: 'FILE', etag: '', size: 12, modifiedAt: '', children: [] },
        { id: 'png', name: 'employee.png', relativePath: 'assets/notation/employee.png', nodeType: 'FILE', etag: '', size: 12, modifiedAt: '', children: [] },
        { id: 'txt', name: 'notes.txt', relativePath: 'notes.txt', nodeType: 'FILE', etag: '', size: 12, modifiedAt: '', children: [] }
    ]
};

describe('project image asset picker', () => {
    beforeEach(() => {
        vi.mocked(projectApi.loadProjectTree).mockReset().mockResolvedValue(tree as never);
        vi.mocked(projectApi.uploadProjectFile).mockReset();
    });

    it('recognizes every portable project image family', () => {
        for (const path of ['icons/node.svg', 'icons/node.PNG', 'icons/node.jpg', 'icons/node.JPEG', 'icons/node.gif', 'icons/node.webp']) expect(isImageAsset(path)).toBe(true);
        expect(isImageAsset('icons/node.bmp')).toBe(false);
        expect(isImageAsset('notes.txt')).toBe(false);
    });

    it('lists image files from the physical project tree', async () => {
        render(<ProjectAssetPicker open value="" onSelect={vi.fn()} onClose={vi.fn()} />);
        expect(await screen.findByText('department.svg')).toBeInTheDocument();
        expect(screen.getByText('employee.png')).toBeInTheDocument();
        expect(screen.queryByText('notes.txt')).not.toBeInTheDocument();
        expect(projectApi.loadProjectTree).toHaveBeenCalledWith('PHYSICAL');
    });

    it('selects a project-relative asset path', async () => {
        const select = vi.fn();
        render(<ProjectAssetPicker open value="" onSelect={select} onClose={vi.fn()} />);
        fireEvent.click(await screen.findByRole('button', { name: /department.svg/i }));
        expect(select).toHaveBeenCalledWith('assets/notation/department.svg');
    });

    it('uploads new assets into the notation directory', async () => {
        vi.mocked(projectApi.uploadProjectFile).mockResolvedValue({ relativePath: 'assets/notation/new.svg' } as never);
        const select = vi.fn();
        render(<ProjectAssetPicker open value="" onSelect={select} onClose={vi.fn()} />);
        const input = document.querySelector('input[type="file"]') as HTMLInputElement;
        fireEvent.change(input, { target: { files: [new File(['<svg/>'], 'new.svg', { type: 'image/svg+xml' })] } });
        await waitFor(() => expect(projectApi.uploadProjectFile).toHaveBeenCalledWith('assets/notation', expect.any(File)));
        expect(select).toHaveBeenCalledWith('assets/notation/new.svg');
    });
});
