import { act, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useHydraDialogs } from './useHydraDialogs';

function Fixture() {
    const { requestDecision, dialogs } = useHydraDialogs();
    return <><button onClick={() => { void requestDecision({ title: 'Overwrite?', description: 'One explanation only', confirmLabel: 'Replace', cancelLabel: 'Cancel' }); }}>open</button>{dialogs}</>;
}

describe('useHydraDialogs', () => {
    it('renders a decision description exactly once', async () => {
        render(<Fixture />);
        await act(async () => { screen.getByRole('button', { name: 'open' }).click(); });
        expect(screen.getAllByText('One explanation only')).toHaveLength(1);
    });
});
