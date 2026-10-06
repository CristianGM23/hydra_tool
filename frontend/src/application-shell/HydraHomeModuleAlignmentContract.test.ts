import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const css = () => readFileSync(resolve(process.cwd(), 'src/application-shell/application-shell.css'), 'utf8').replace(/\s+/g, ' ');

describe('HYDRA Home module alignment contract', () => {
    it('anchors every compound module button to the same three columns', () => {
        const styles = css();
        expect(styles).toContain('.hydra-ui-button.hydra-home-module-row { display: grid; grid-template-columns: minmax(0, 1fr) 168px 112px; align-items: center; justify-content: stretch; justify-items: stretch; gap: 16px; width: 100%; min-height: 72px; padding: 13px 15px; border: 1px solid var(--hydra-border-default); border-left: 4px solid var(--hydra-card-accent, var(--hydra-color-primary-600)); border-radius: 10px; text-align: left; white-space: normal; }');
        expect(styles).toContain('.hydra-home-module-row .hydra-module-row-copy { width: 100%; min-width: 0; padding: 0; margin: 0; justify-self: stretch; text-align: left; }');
        expect(styles).toContain('.hydra-home-module-row .hydra-module-row-copy > :is(strong, small) { display: block; width: 100%; min-width: 0; margin: 0; padding: 0; text-align: left; }');
        expect(styles).toContain('.hydra-home-module-row .hydra-module-row-meta { box-sizing: border-box; width: 100%; justify-self: stretch; text-align: center; }');
        expect(styles).toContain('.hydra-home-module-row .hydra-module-card-open { box-sizing: border-box; width: 100%; justify-self: stretch; text-align: right; }');
    });
});
