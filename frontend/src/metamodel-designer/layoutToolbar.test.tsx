import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { MetamodelLayoutToolbar } from './MetamodelLayoutToolbar';

describe('MetamodelLayoutToolbar', () => {
    it('changes direction, organizes and routes relations', () => {
        const onApply = vi.fn();
        const onRoute = vi.fn();
        const onDirectionChange = vi.fn();
        render(<MetamodelLayoutToolbar direction="LEFT_TO_RIGHT" disabled={false} focusSelected={false} hideDependencies={false} onApply={onApply} onElkApply={vi.fn()} onDirectionChange={onDirectionChange} onFocusSelectedChange={vi.fn()} onHideDependenciesChange={vi.fn()} onRoute={onRoute} />);

        fireEvent.change(screen.getByLabelText('Layout direction'), { target: { value: 'TOP_TO_BOTTOM' } });
        fireEvent.click(screen.getByRole('button', { name: 'Organize diagram' }));
        fireEvent.click(screen.getByRole('button', { name: 'Route relations' }));

        expect(onDirectionChange).toHaveBeenCalledWith('TOP_TO_BOTTOM');
        expect(onApply).toHaveBeenCalledTimes(1);
        expect(onRoute).toHaveBeenCalledTimes(1);
    });

    it('changes focus filters', () => {
        const onFocusSelectedChange = vi.fn();
        const onHideDependenciesChange = vi.fn();
        render(<MetamodelLayoutToolbar direction="LEFT_TO_RIGHT" disabled={false} focusSelected={false} hideDependencies={false} onApply={vi.fn()} onElkApply={vi.fn()} onDirectionChange={vi.fn()} onFocusSelectedChange={onFocusSelectedChange} onHideDependenciesChange={onHideDependenciesChange} onRoute={vi.fn()} />);

        fireEvent.click(screen.getByLabelText('Focus selected class'));
        fireEvent.click(screen.getByLabelText('Hide dependencies'));

        expect(onFocusSelectedChange).toHaveBeenCalledWith(true);
        expect(onHideDependenciesChange).toHaveBeenCalledWith(true);
    });

    it('disables actions while a request is active', () => {
        render(<MetamodelLayoutToolbar direction="LEFT_TO_RIGHT" disabled focusSelected={false} hideDependencies={false} onApply={vi.fn()} onElkApply={vi.fn()} onDirectionChange={vi.fn()} onFocusSelectedChange={vi.fn()} onHideDependenciesChange={vi.fn()} onRoute={vi.fn()} />);
        expect(screen.getByLabelText('Layout direction')).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Organize diagram' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Route relations' })).toBeDisabled();
    });
});
