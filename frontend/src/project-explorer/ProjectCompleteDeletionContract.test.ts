import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(relativePath: string): string {
    return readFileSync(resolve(process.cwd(), relativePath), 'utf8');
}

describe('HYDRA V4.30.0d complete project deletion contract', () => {
    it('keeps complete-project deletion separate from entry deletion', () => {
        const api = source('src/project-explorer/projectApi.ts');
        expect(api).toContain("'/api/projects/active/deletion-impact'");
        expect(api).toContain('/api/projects/active?deleteDependencies=');
        expect(api).toContain('/api/projects/active/entries');
        expect(api).toContain('query.toString()');
    });

    it('offers complete deletion from both toolbar and context menu when the project root is selected', () => {
        const workspace = source('src/project-explorer/ProjectExplorerWorkspace.tsx');
        expect(workspace).toContain('const projectRootSelected = useMemo');
        expect(workspace).toContain("selectedNodes.some(node => node.relativePath === '')");
        expect(workspace).toContain('setProjectDeletionOpen(true)');
        expect(workspace).toContain('Delete project');
        expect(workspace).toContain('Delete complete project…');
        expect(workspace).toContain('<ProjectDeletionDialog');
    });

    it('requires dependency confirmation in the destructive dialog', () => {
        const dialog = source('src/project-explorer/ProjectDeletionDialog.tsx');
        expect(dialog).toContain('impact.dependencies.length > 0 && !deleteDependencies');
        expect(dialog).toContain('Also delete all dependent artifacts');
        expect(dialog).toContain('deleteActiveProject(deleteDependencies)');
    });
});
