import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ValidatedNumberInput } from './ValidatedNumberInput';

describe('ValidatedNumberInput', () => {
    it('sends valid real values including comma decimals', () => {
        const onChange = vi.fn();
        render(<ValidatedNumberInput label="Width" min={0} value={10} onChange={onChange} />);

        fireEvent.change(screen.getByLabelText('Width'), { target: { value: '12,5' } });

        expect(onChange).toHaveBeenCalledWith(12.5);
        expect(screen.queryByRole('alert')).toBeNull();
    });

    it('keeps invalid text out of the application state', () => {
        const onChange = vi.fn();
        render(<ValidatedNumberInput label="Height" min={1} value={20} onChange={onChange} />);

        fireEvent.change(screen.getByLabelText('Height'), { target: { value: '20px' } });

        expect(onChange).not.toHaveBeenCalled();
        expect(screen.getByLabelText('Height')).toHaveAttribute('aria-invalid', 'true');
        expect(screen.getByRole('alert').textContent).toContain('natural or real');
    });

    it('restores the previous valid value when an invalid field loses focus', () => {
        render(<ValidatedNumberInput label="Position" max={100} min={0} value={25} onChange={vi.fn()} />);
        const input = screen.getByLabelText('Position');

        fireEvent.change(input, { target: { value: '101' } });
        fireEvent.blur(input);

        expect(input).toHaveValue('25');
        expect(screen.queryByRole('alert')).toBeNull();
    });
});
