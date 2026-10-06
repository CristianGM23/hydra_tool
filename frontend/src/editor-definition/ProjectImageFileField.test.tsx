import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjectImageFileField } from './ProjectImageFileField';
import { isSupportedImage } from './projectImageFileSupport';
import * as projectApi from '../project-explorer/projectApi';

vi.mock('../project-explorer/projectApi', () => ({
    loadProjectTree: vi.fn(),
    uploadProjectFile: vi.fn(),
    projectFileDownloadUrl: (path: string) => `/download/${path}`,
    projectFileVisualUrl: (path: string) => `/visual/${path}`
}));

describe('project image file field', () => {
    beforeEach(() => vi.mocked(projectApi.uploadProjectFile).mockReset());

    it('accepts every portable image family and rejects mismatched or unsupported files', () => {
        for (const file of [
            { name: 'node.svg', type: 'image/svg+xml' },
            { name: 'node.png', type: 'image/png' },
            { name: 'node.jpg', type: 'image/jpeg' },
            { name: 'node.jpeg', type: 'image/jpeg' },
            { name: 'node.gif', type: 'image/gif' },
            { name: 'node.webp', type: 'image/webp' },
        ]) expect(isSupportedImage(file)).toBe(true);
        expect(isSupportedImage({ name: 'node.bmp', type: 'image/bmp' })).toBe(false);
        expect(isSupportedImage({ name: 'node.jpg', type: 'text/plain' })).toBe(false);
    });

    it('stores the selected file in assets/notation and returns only the relative project path', async () => {
        vi.mocked(projectApi.uploadProjectFile).mockResolvedValue({ relativePath: 'assets/notation/node.svg' } as never);
        const change = vi.fn();
        render(<ProjectImageFileField id="image" label="Image" help="Choose image" value="" onChange={change} />);
        const input = document.querySelector('input[type="file"]') as HTMLInputElement;
        fireEvent.change(input, { target: { files: [new File(['<svg/>'], 'node.svg', { type: 'image/svg+xml' })] } });
        await waitFor(() => expect(projectApi.uploadProjectFile).toHaveBeenCalledWith('assets/notation', expect.any(File)));
        expect(change).toHaveBeenCalledWith('assets/notation/node.svg');
        expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    });
});
