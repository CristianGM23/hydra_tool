import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { HydraMultiSelectChips } from './HydraMultiSelectChips';

const OPTIONS = [
    { value: 'validation-runtime', label: 'Validation runtime', description: 'Validation publisher.', category: 'Behavior' },
    { value: 'event-flow-runtime', label: 'EventFlow runtime', description: 'Event-flow publisher.', category: 'Behavior' }
];

function Harness({ allowCustom = false }: { allowCustom?: boolean }) {
    const [selected, setSelected] = useState<string[]>([]);
    return <HydraMultiSelectChips id="sources" ariaLabel="Allowed sources" options={OPTIONS} selected={selected} onChange={setSelected} allowCustom={allowCustom} />;
}

describe('HydraMultiSelectChips', () => {
    it('adds and removes selected values through checkboxes and pills', () => {
        render(<Harness />);
        fireEvent.click(screen.getByRole('checkbox', { name: /Validation runtime/ }));
        expect(screen.getByText('Validation runtime', { selector: '.hydra-multiselect-chip span' })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Remove Validation runtime' }));
        expect(screen.queryByRole('button', { name: 'Remove Validation runtime' })).not.toBeInTheDocument();
    });

    it('preserves an advanced custom-value escape hatch for external identifiers', () => {
        render(<Harness allowCustom />);
        fireEvent.click(screen.getByText('Advanced compatibility: add a custom value'));
        fireEvent.change(screen.getByRole('textbox', { name: 'Custom value' }), { target: { value: 'external-source' } });
        fireEvent.click(screen.getByRole('button', { name: 'Add' }));
        expect(screen.getByRole('button', { name: 'Remove external-source' })).toBeInTheDocument();
    });
});
