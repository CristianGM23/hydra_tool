import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProjectManagerDialog } from './ProjectManagerDialog';

const EMPTY_CREATION_CATALOG = { presets: [], capabilities: [] };

function jsonResponse(value: unknown): Response {
    return new Response(JSON.stringify(value), { status: 200, headers: { 'Content-Type': 'application/json' } });
}


beforeEach(() => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async input => {
        if (String(input) === '/api/projects/catalog') return jsonResponse(EMPTY_CREATION_CATALOG);
        throw new Error(`Unexpected request: ${String(input)}`);
    });
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe('ProjectManagerDialog', () => {
    it('creates a project with local path and stable id', async () => {
        const onCreate = vi.fn().mockResolvedValue(undefined);
        render(<ProjectManagerDialog open recentProjects={[]} onClose={vi.fn()} onCreate={onCreate} onImport={vi.fn()} onOpenProject={vi.fn()} />);
        fireEvent.change(screen.getByLabelText('Parent directory'), { target: { value: 'C:\\projects' } });
        fireEvent.change(screen.getByLabelText('Project name'), { target: { value: 'Railway' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create project' }));
        await vi.waitFor(() => expect(onCreate).toHaveBeenCalledWith(expect.objectContaining({ parentDirectory: 'C:\\projects', projectId: 'railway', name: 'Railway', initializeFromCurrentWorkspace: false })));
    });


    it('copies the current workspace only when the user explicitly requests it', async () => {
        const onCreate = vi.fn().mockResolvedValue(undefined);
        render(<ProjectManagerDialog open recentProjects={[]} onClose={vi.fn()} onCreate={onCreate} onImport={vi.fn()} onOpenProject={vi.fn()} />);
        fireEvent.change(screen.getByLabelText('Parent directory'), { target: { value: 'C:\\projects' } });
        fireEvent.change(screen.getByLabelText('Project name'), { target: { value: 'Railway' } });
        fireEvent.click(screen.getByRole('checkbox', { name: 'Copy the current HYDRA workspace into the new project' }));
        fireEvent.click(screen.getByRole('button', { name: 'Create project' }));
        await vi.waitFor(() => expect(onCreate).toHaveBeenCalledWith(expect.objectContaining({ initializeFromCurrentWorkspace: true })));
    });

    it('opens the directory selector from Browse and keeps the result visible beside the form', async () => {
        const fetchMock = vi.mocked(globalThis.fetch);
        fetchMock.mockImplementation(async input => {
            if (String(input) === '/api/projects/catalog') return jsonResponse(EMPTY_CREATION_CATALOG);
            if (String(input) === '/api/projects/path-picker') return jsonResponse({ available:true,cancelled:false,selectedPath:'C:\\Users\\Cristian\\Documents\\ModelingProjects',selectedDirectory:'C:\\Users\\Cristian\\Documents\\ModelingProjects',fileName:'ModelingProjects',mode:'DIRECTORY',message:'' });
            throw new Error(`Unexpected request: ${String(input)}`);
        });
        render(<ProjectManagerDialog open recentProjects={[]} onClose={vi.fn()} onCreate={vi.fn()} onImport={vi.fn()} onOpenProject={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Browse parent directory' }));
        await vi.waitFor(() => expect((screen.getByLabelText('Parent directory') as HTMLInputElement).value).toContain('ModelingProjects'));
        expect(screen.getByRole('status')).toHaveTextContent('Selected directory');
        expect(fetchMock).toHaveBeenCalledWith('/api/projects/path-picker',expect.objectContaining({method:'POST'}));
    });

    it('offers recent projects in open/import mode', () => {
        render(<ProjectManagerDialog open recentProjects={[{ id: 'railway', name: 'Railway', directory: 'C:\\Railway', lastOpenedAt: null, active: false, available: true }]} onClose={vi.fn()} onCreate={vi.fn()} onImport={vi.fn()} onOpenProject={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Open / import' }));
        expect(screen.getByLabelText('Recent projects')).toBeTruthy();
    });

    it('opens an explicit directory and loads it into the workspace', async () => {
        const onOpenProject = vi.fn().mockResolvedValue(undefined);
        render(<ProjectManagerDialog open recentProjects={[]} onClose={vi.fn()} onCreate={vi.fn()} onImport={vi.fn()} onOpenProject={onOpenProject} />);
        fireEvent.click(screen.getByRole('button', { name: 'Open / import' }));
        fireEvent.change(screen.getByLabelText('Project directory'), { target: { value: 'C:\\Projects\\Railway' } });
        fireEvent.click(screen.getByRole('button', { name: 'Open project' }));
        await vi.waitFor(() => expect(onOpenProject).toHaveBeenCalledWith('C:\\Projects\\Railway', true));
    });

    it('reports that a ZIP is required before importing', async () => {
        render(<ProjectManagerDialog open recentProjects={[]} onClose={vi.fn()} onCreate={vi.fn()} onImport={vi.fn()} onOpenProject={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Open / import' }));
        fireEvent.click(screen.getByRole('button', { name: 'ZIP file' }));
        fireEvent.click(screen.getByRole('button', { name: 'Import project' }));
        expect(await screen.findByRole('alert')).toHaveTextContent('Select a ZIP file');
    });

    it('offers local, zip, directory and repository sources', () => {
        render(<ProjectManagerDialog open recentProjects={[]} onClose={vi.fn()} onCreate={vi.fn()} onImport={vi.fn()} onImportDirectory={vi.fn()} onImportRemote={vi.fn()} onOpenProject={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Open / import' }));
        expect(screen.getByRole('button', { name: /Local project/ })).toBeTruthy();
        expect(screen.getByRole('button', { name: /ZIP file/ })).toBeTruthy();
        expect(screen.getAllByRole('button', { name: /Project folder/ }).length).toBeGreaterThanOrEqual(1);
        expect(screen.getByRole('button', { name: /Repository/ })).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: /ZIP file/ }));
        fireEvent.click(screen.getByRole('button', { name: 'Information about Destination directory' }));
        expect(screen.getByRole('tooltip')).toHaveTextContent('Parent folder where the imported project will be created');
    });

    it('opens the browser ZIP selector when the ZIP picker is pressed during import', async () => {
        render(<ProjectManagerDialog open recentProjects={[]} onClose={vi.fn()} onCreate={vi.fn()} onImport={vi.fn()} onOpenProject={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Open / import' }));
        fireEvent.click(screen.getByRole('button', { name: 'ZIP file' }));
        const input=document.getElementById('project-import-file') as HTMLInputElement;
        Object.defineProperty(input,'showPicker',{configurable:true,value:undefined});
        const clickSpy=vi.spyOn(input,'click');
        fireEvent.click(screen.getByRole('button', { name: /Select ZIP file/ }));
        expect(clickSpy).toHaveBeenCalledOnce();
    });

    it('uses the native file selector option when opening a project descriptor', async () => {
        const fetchMock = vi.mocked(globalThis.fetch);
        fetchMock.mockImplementation(async input => {
            if (String(input) === '/api/projects/catalog') return jsonResponse(EMPTY_CREATION_CATALOG);
            if (String(input) === '/api/projects/path-picker') return jsonResponse({ available:true,cancelled:false,selectedPath:'C:\\Projects\\Railway\\modeling-project.json',selectedDirectory:'C:\\Projects\\Railway',fileName:'modeling-project.json',mode:'PROJECT_DESCRIPTOR',message:'' });
            throw new Error(`Unexpected request: ${String(input)}`);
        });
        render(<ProjectManagerDialog open recentProjects={[]} onClose={vi.fn()} onCreate={vi.fn()} onImport={vi.fn()} onOpenProject={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Open / import' }));
        fireEvent.click(screen.getByRole('button', { name: /Project file/ }));
        await vi.waitFor(() => expect((screen.getByLabelText('Project directory') as HTMLInputElement).value).toBe('C:\\Projects\\Railway'));
        expect(fetchMock).toHaveBeenCalledWith('/api/projects/path-picker',expect.objectContaining({method:'POST'}));
    });

    it('shows a clear message when the backend is not running', async () => {
        const fetchMock = vi.mocked(globalThis.fetch);
        fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
        render(<ProjectManagerDialog open recentProjects={[]} onClose={vi.fn()} onCreate={vi.fn()} onImport={vi.fn()} onOpenProject={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Open / import' }));
        fireEvent.click(screen.getByRole('button', { name: /Project file/ }));
        expect(await screen.findByText(/Backend unavailable at the same-origin \/api endpoint/)).toBeTruthy();
    });

    it('imports a project from a repository profile', async () => {
        const fetchMock = vi.mocked(globalThis.fetch);
        fetchMock.mockImplementation(async input => {
            if (String(input) === '/api/projects/catalog') return jsonResponse(EMPTY_CREATION_CATALOG);
            if (String(input) === '/api/remote-storage/profiles') return jsonResponse([{ id:'repo-main', displayName:'Main repository', provider:'GITHUB', enabled:true, configured:true, repository:'hydra', branch:'main', rootPath:'/', maximumArtifactBytes:1000 }]);
            throw new Error(`Unexpected request: ${String(input)}`);
        });
        const onImportRemote = vi.fn().mockResolvedValue(undefined);
        render(<ProjectManagerDialog open recentProjects={[]} onClose={vi.fn()} onCreate={vi.fn()} onImport={vi.fn()} onImportRemote={onImportRemote} onOpenProject={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Open / import' }));
        fireEvent.click(screen.getByRole('button', { name: /Repository/ }));
        await screen.findByRole('option', { name: /Main repository/ });
        fireEvent.change(screen.getByLabelText('Destination directory'), { target: { value: 'C:\\projects' } });
        fireEvent.change(screen.getByLabelText('Local project folder name'), { target: { value: 'RailwayImported' } });
        fireEvent.change(screen.getByLabelText('Repository profile'), { target: { value: 'repo-main' } });
        fireEvent.change(screen.getByLabelText('Remote project path'), { target: { value: 'projects/railway.zip' } });
        fireEvent.click(screen.getByRole('button', { name: 'Import project' }));
        await vi.waitFor(() => expect(onImportRemote).toHaveBeenCalledWith({ profileId:'repo-main', remotePath:'projects/railway.zip', parentDirectory:'C:\\projects', projectName:'RailwayImported' }));
    });

    it('closes from the accessible close button', () => {
        const onClose = vi.fn();
        render(<ProjectManagerDialog open recentProjects={[]} onClose={onClose} onCreate={vi.fn()} onImport={vi.fn()} onOpenProject={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Close project dialog' }));
        expect(onClose).toHaveBeenCalledOnce();
    });
});
