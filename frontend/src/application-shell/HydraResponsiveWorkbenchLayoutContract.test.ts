import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(path: string): string {
    return readFileSync(resolve(process.cwd(), path), 'utf8');
}

function compactRule(styles: string, selector: string): string {
    const compact = styles.replace(/\s+/g, '');
    const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const match = new RegExp(`(?:^|})(${escaped}\\{[^}]*})`).exec(compact);
    if (!match?.[1]) throw new Error(`CSS rule not found: ${selector}`);
    return match[1];
}

describe('HYDRA responsive workbench layout contract', () => {
    it('wraps the project command bar instead of forcing horizontal overflow', () => {
        const component = source('src/project-explorer/ProjectExplorerWorkspace.tsx');
        const styles = source('src/project-explorer/project-explorer.css');
        expect(component).toContain('project-command-primary');
        expect(component).toContain('project-command-context');
        expect(styles).toContain('flex-wrap: wrap;');
        expect(styles.replace(/\s+/g, '')).toContain('.project-command-primary{flex:11720px;');
        expect(styles).not.toContain('.project-command-bar { overflow-x: auto; }');
    });

    it('stretches representable imports across the complete project editor width', () => {
        const styles = source('src/representable-import-center/representable-import-center.css');
        const projectStyles = source('src/project-explorer/project-explorer.css');
        expect(styles).toContain('.representable-import-center {');
        expect(styles).toContain('width: 100%;');
        expect(styles).toContain('flex: 1 1 auto;');
        expect(projectStyles).toContain('.project-editor-content > .representable-import-center');
    });

    it('keeps shell and navigation copy inside narrow containers', () => {
        const shell = source('src/application-shell/application-shell.css');
        expect(compactRule(shell, '.hydra-module-surface')).toContain('min-width:0');
        expect(compactRule(shell, '.hydra-module-description')).toContain('overflow-wrap:anywhere');
        expect(compactRule(shell, '.hydra-module-link-description')).toContain('overflow-wrap:anywhere');
    });

    it('uses all available space in native verification and wraps shared headers', () => {
        const verification = source('src/compatibility-center/compatibility-center.css');
        const shell = source('src/application-shell/application-shell.css');
        expect(verification).toContain('width: 100%;');
        expect(verification).toContain('flex: 1 1 auto;');
        expect(shell).toContain('.hydra-center-introduction {');
        expect(shell).toContain('flex-wrap: wrap;');
    });
});
