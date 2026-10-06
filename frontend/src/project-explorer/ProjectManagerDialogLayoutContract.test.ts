import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const css = () => readFileSync(resolve(process.cwd(), 'src/project-explorer/project-explorer.css'), 'utf8').replace(/\s+/g, ' ');

describe('Project manager responsive layout contract', () => {
    it('keeps compound source cards bounded and wraps their descriptions', () => {
        const styles = css();
        expect(styles).toContain('.project-manager-dialog .hydra-ui-button.project-source-option { display: grid; grid-template-columns: 38px minmax(0, 1fr); align-items: center; justify-content: stretch; justify-items: stretch; gap: 11px; min-width: 0; min-height: 86px; padding: 13px 14px; border: 1px solid var(--hydra-border-strong); border-radius: 8px; text-align: left; white-space: normal; overflow: hidden; }');
        expect(styles).toContain('.project-manager-dialog .project-source-option :is(strong, small) { display: block; min-width: 0; max-width: 100%; margin: 0; white-space: normal; overflow-wrap: anywhere; word-break: normal; }');
        expect(styles).toContain('@container (max-width: 680px)');
    });
});
