import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { HydraAdaptiveSelect } from './HydraAdaptiveSelect';
import { HydraFormField } from '../design-system/HydraFormField';

const shortOptions = [
    { value: 'A', label: 'Alpha' },
    { value: 'B', label: 'Beta' }
];
const longOptions = [
    { value: 'A', label: 'Alpha', category: 'First group', description: 'First value.' },
    { value: 'B', label: 'Beta', category: 'First group', description: 'Second value.' },
    { value: 'C', label: 'Charlie', category: 'First group' },
    { value: 'D', label: 'Delta', category: 'Second group' },
    { value: 'E', label: 'Echo', category: 'Second group' },
    { value: 'F', label: 'Foxtrot', category: 'Second group' },
    { value: 'G', label: 'Golf', category: 'Second group' }
];

describe('HydraAdaptiveSelect', () => {
    it('keeps a compact native select for six or fewer values and explains its options by default', () => {
        render(<HydraAdaptiveSelect ariaLabel="Short" options={shortOptions} value="A" onChange={() => undefined} />);
        expect(screen.getByRole('combobox', { name: 'Short' })).toBeInTheDocument();
        expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Information about Short' })).toBeInTheDocument();
    });

    it('groups more than six values without rendering the removed auxiliary filter and keeps option help', () => {
        const change = vi.fn();
        render(<HydraAdaptiveSelect ariaLabel="Long" options={longOptions} value="A" onChange={change} />);
        expect(screen.getByRole('group', { name: 'First group' })).toBeInTheDocument();
        expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
        expect(screen.queryByText('Filter values')).not.toBeInTheDocument();
        fireEvent.change(screen.getByRole('combobox', { name: 'Long' }), { target: { value: 'E' } });
        expect(change).toHaveBeenCalledWith('E');
        expect(screen.getByRole('button', { name: 'Information about Long' })).toBeInTheDocument();
    });

    it('allows option help to be requested explicitly for a short standalone selector', () => {
        const described = shortOptions.map(option => ({ ...option, description: `${option.label} description.` }));
        render(<HydraAdaptiveSelect ariaLabel="Explicit" options={described} showOptionHelp value="A" onChange={() => undefined} />);
        expect(screen.getByRole('button', { name: 'Information about Explicit' })).toBeInTheDocument();
    });

    it('does not duplicate option help when the surrounding HYDRA field already owns it', () => {
        const described = shortOptions.map(option => ({ ...option, description: `${option.label} description.` }));
        render(<HydraFormField label="Wrapped" targetId="wrapped-select" help="Choose a value." options={described}>
            <HydraAdaptiveSelect id="wrapped-select" ariaLabel="Wrapped" options={described} value="A" onChange={() => undefined} />
        </HydraFormField>);
        expect(screen.getAllByRole('button', { name: 'Information about Wrapped' })).toHaveLength(1);
    });

});
