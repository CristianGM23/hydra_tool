import { createRequire } from 'node:module';
import { render } from '@testing-library/react';
import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

interface AxeViolationNode { html: string; target?: string[]; }
interface AxeViolation { id: string; help: string; impact?: string | null; nodes?: AxeViolationNode[]; }
interface AxeLike {
    run(context: Element, options?: { runOnly: { type: 'tag'; values: string[] } }): Promise<{ violations: AxeViolation[] }>;
}

const runtimeRequire = createRequire(import.meta.url);
const WCAG_AA_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22a', 'wcag22aa'];

function formatViolation(item: AxeViolation): string {
    const nodes = (item.nodes ?? []).map(node => node.html).join(' | ');
    return `${item.id}: ${item.help}${item.impact === undefined || item.impact === null ? '' : ` [${item.impact}]`}${nodes === '' ? '' : ` -> ${nodes}`}`;
}

describe('axe accessibility smoke', () => {
    it('keeps representative design-system controls free of automated WCAG A/AA violations', async () => {
        const [{ HydraButton }, { HydraCheckbox }, { HydraModal }] = await Promise.all([
            import('../design-system/HydraButton'),
            import('../design-system/HydraFormField'),
            import('../design-system/HydraModal')
        ]);
        const axe = runtimeRequire('axe-core') as AxeLike;
        render(<><main><h1>Accessibility smoke host</h1><p>Representative host page for the portal-mounted dialog.</p></main><HydraModal open title="Accessibility test" onClose={() => undefined}>
            <label htmlFor="a11y-name">Name</label><input id="a11y-name" />
            <HydraCheckbox checked={false} label="Enabled" onChange={() => undefined} />
            <HydraButton onClick={() => undefined}>Save</HydraButton>
        </HydraModal></>);
        const dialog = screen.getByRole('dialog', { name: 'Accessibility test' });
        expect(dialog).toHaveAttribute('aria-modal', 'true');
        const result = await axe.run(document.body, { runOnly: { type: 'tag', values: WCAG_AA_TAGS } });
        expect(result.violations, result.violations.map(formatViolation).join('\n')).toEqual([]);
    });
});
