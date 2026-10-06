import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EventCategoryField } from './EventCategoryField';

describe('EventCategoryField', () => {
    it('offers HYDRA families with explanatory help and keeps a custom escape hatch', () => {
        const onChange = vi.fn();
        const view = render(<EventCategoryField value="DOMAIN" onChange={onChange} />);
        expect(screen.getByLabelText('Category')).toHaveValue('DOMAIN');
        fireEvent.click(screen.getByRole('button', { name: 'Information about Category' }));
        expect(screen.getByRole('tooltip')).toHaveTextContent(/Domain \/ business/i);
        expect(screen.getByRole('tooltip')).toHaveTextContent(/Validation/i);
        fireEvent.change(screen.getByLabelText('Category'), { target: { value: '__CUSTOM__' } });
        expect(onChange).toHaveBeenCalledWith('CUSTOM');
        view.rerender(<EventCategoryField value="CUSTOM" onChange={onChange} />);
        expect(screen.getByLabelText('Custom category')).toBeInTheDocument();
    });
});
