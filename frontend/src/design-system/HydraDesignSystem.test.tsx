import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import {
    HydraButton, HydraCheckbox, HydraErrorBoundary, HydraModal, HydraPalette, HydraResizablePanel,
    HydraTooltip, HydraTree, type HydraTreeNode
} from './index';

describe('HYDRA design system', () => {
    it('keeps button variants and loading state consistent', () => {
        render(<HydraButton loading variant="primary">Save</HydraButton>);
        const button = screen.getByRole('button', { name: 'Save' });
        expect(button).toBeDisabled();
        expect(button).toHaveClass('hydra-ui-button', 'variant-primary');
        expect(button).toHaveAttribute('aria-busy', 'true');
    });

    it('keeps a checkbox name independent from its technical description', () => {
        render(<HydraCheckbox checked={false} label="Create book" description="NODE · target Book · DERIVED" onChange={() => undefined} />);
        const checkbox = screen.getByRole('checkbox', { name: 'Create book' });
        expect(checkbox).toHaveAccessibleDescription('NODE · target Book · DERIVED');
    });

    it('opens fragile help content in a portal and closes it with Escape', () => {
        render(<HydraTooltip label="Field information" title="About field"><p>Stable help</p></HydraTooltip>);
        fireEvent.click(screen.getByRole('button', { name: 'Field information' }));
        expect(screen.getByRole('tooltip')).toHaveTextContent('Stable help');
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
    });

    it('translates compound tooltip content without generating React key warnings', () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        try {
            render(<HydraTooltip label="Compound information" title="About compound field"><p>First detail</p><div><strong>Options</strong><dl><div><dt>One</dt><dd>First option</dd></div></dl></div></HydraTooltip>);
            fireEvent.click(screen.getByRole('button', { name: 'Compound information' }));
            expect(screen.getByRole('tooltip')).toHaveTextContent('First detail');
            expect(consoleError.mock.calls.some(call => call.some(argument => String(argument).includes('unique "key" prop')))).toBe(false);
        } finally {
            consoleError.mockRestore();
        }
    });

    it('uses one modal contract for escape and close controls', () => {
        const close = vi.fn();
        render(<HydraModal open title="Shared dialog" onClose={close}><p>Content</p></HydraModal>);
        expect(screen.getByRole('dialog')).toHaveTextContent('Shared dialog');
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(close).toHaveBeenCalledOnce();
    });

    it('expands tree branches that arrive asynchronously', () => {
        const initial: HydraTreeNode[] = [{ id: 'root', label: 'Root', children: [] }];
        const { rerender } = render(<HydraTree ariaLabel="Shared tree" nodes={initial} defaultExpandedIds={['root']} />);
        rerender(<HydraTree ariaLabel="Shared tree" nodes={[{ id: 'root', label: 'Root', children: [{ id: 'late', label: 'Late node' }] }]} defaultExpandedIds={['root']} />);
        expect(screen.getByText('Late node')).toBeInTheDocument();
    });

    it('resizes panels with keyboard commands and persists their width', () => {
        window.localStorage.clear();
        render(<div><HydraResizablePanel ariaLabel="Resize palette" storageKey="hydra-test-panel" initialWidth={300} minimumWidth={220} maximumWidth={420}>Panel</HydraResizablePanel></div>);
        const separator = screen.getByRole('separator', { name: 'Resize palette' });
        fireEvent.keyDown(separator, { key: 'ArrowRight' });
        expect(separator).toHaveAttribute('aria-valuenow', '316');
        expect(window.localStorage.getItem('hydra-test-panel')).toBe('316');
    });

    it('contains palette layout and prevents a failed view from blanking the workspace', () => {
        const caughtError = vi.fn();
        const originalConsoleError = console.error;
        const consoleError = vi.spyOn(console, 'error').mockImplementation((...arguments_: unknown[]) => {
            const expectedBoundaryError = arguments_.some(argument =>
                argument instanceof Error && argument.message === 'render failed'
            ) || arguments_.some(argument => String(argument).includes('render failed'));
            if (!expectedBoundaryError) originalConsoleError(...arguments_);
        });
        function Broken(): never { throw new Error('render failed'); }

        try {
            render(
                <><HydraPalette title="Tools"><span>Class</span></HydraPalette><HydraErrorBoundary scope="Events"><Broken /></HydraErrorBoundary></>,
                { onCaughtError: caughtError }
            );
            expect(screen.getByLabelText('Tools')).toHaveTextContent('Class');
            expect(screen.getByRole('alert')).toHaveTextContent('This area could not be rendered: Events');
            expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
            expect(caughtError).toHaveBeenCalledOnce();
            expect(caughtError.mock.calls[0]?.[0]).toMatchObject({ message: 'render failed' });
        } finally {
            consoleError.mockRestore();
        }
    });
    it('keeps focus inside an active control when a modal parent rerenders with a new close callback', async () => {
        const firstClose = vi.fn();
        const { rerender } = render(<HydraModal open title="Stable focus" onClose={firstClose}><select aria-label="Language"><option>Java</option><option>Python</option></select></HydraModal>);
        const select = screen.getByLabelText('Language');
        select.focus();
        expect(document.activeElement).toBe(select);
        rerender(<HydraModal open title="Stable focus" onClose={() => undefined}><select aria-label="Language"><option>Java</option><option>Python</option></select></HydraModal>);
        expect(document.activeElement).toBe(screen.getByLabelText('Language'));
    });

});
