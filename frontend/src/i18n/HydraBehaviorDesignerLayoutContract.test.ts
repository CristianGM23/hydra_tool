import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(path: string): string {
    return readFileSync(resolve(process.cwd(), path), 'utf8');
}

describe('HYDRA behavior designer width contract', () => {
    it('stretches Events across the project editor', () => {
        const styles = source('src/event-designer/event-designer.css');
        expect(styles).toContain('.event-designer {');
        expect(styles).toContain('width: 100%;');
        expect(styles).toContain('flex: 1 1 auto;');
        expect(styles).toContain('.event-designer-layout {');
        expect(styles).toContain('align-self: stretch;');
    });

    it('stretches EventFlow and Gamification across their available surface', () => {
        const flow = source('src/event-designer/flow/event-flow-designer.css');
        const gamification = source('src/gamification-designer/gamification-designer.css');
        expect(flow).toContain('.event-flow-designer {');
        expect(flow).toContain('.event-flow-layout {');
        expect(gamification).toContain('.gamification-designer {');
        expect(gamification).toContain('.gamification-layout {');
        for (const styles of [flow, gamification]) {
            expect(styles).toContain('width: 100%;');
            expect(styles).toContain('flex: 1 1 auto;');
        }
    });

    it('gives direct behavior designers the full project-editor width', () => {
        const styles = source('src/project-explorer/project-explorer.css');
        expect(styles).toContain('.project-editor-content > .event-designer,');
        expect(styles).toContain('.project-editor-content > .gamification-designer,');
        expect(styles).toContain('.project-editor-content > .event-flow-designer');
        expect(styles).toContain('width: 100%;');
        expect(styles).toContain('min-width: 0;');
    });

    it('stretches the shared master-detail form instead of using intrinsic width', () => {
        const styles = source('src/designer-ui/designer-ui.css');
        expect(styles).toContain('every canonical master-detail form inherits the full available');
        expect(styles).toContain('.hydra-designer-master-detail {');
        expect(styles).toContain('align-self: stretch;');
    });
});
