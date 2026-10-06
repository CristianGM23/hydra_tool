import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const projectExplorerCss = readFileSync(
    resolve(process.cwd(), 'src/project-explorer/project-explorer.css'),
    'utf8'
);

describe('metamodel workbench layout', () => {
    it('provides a flex host for the visual metamodel designer', () => {
        expect(projectExplorerCss.replace(/\s+/g, '')).toContain('.project-editor-content{display:flex;');
        expect(projectExplorerCss).toContain('.project-editor-content > .metamodel-visual-designer');
        expect(projectExplorerCss).not.toContain('.project-editor-content > .metamodel-designer { height: 100%; }');
    });
});
