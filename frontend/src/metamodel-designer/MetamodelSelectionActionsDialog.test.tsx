import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { MetamodelSelectionActionsDialog } from './MetamodelSelectionActionsDialog';

describe('MetamodelSelectionActionsDialog', () => {
    it('shows counts and runs horizontal and tree arrangements', () => {
        const onArrange = vi.fn();
        render(<MetamodelSelectionActionsDialog canDistribute elementCount={3} onArrange={onArrange} onClear={vi.fn()} onDeleteElements={vi.fn()} onDeleteRelations={vi.fn()} relationCount={2} />);

        expect(screen.getByText('3 element(s) selected')).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: 'Horizontal line' }));
        fireEvent.click(screen.getByRole('button', { name: 'Tree layout' }));

        expect(onArrange).toHaveBeenNthCalledWith(1, 'ALIGN_TOP');
        expect(onArrange).toHaveBeenNthCalledWith(2, 'TREE');
    });

    it('runs relation and element deletion once', () => {
        const onDeleteElements = vi.fn();
        const onDeleteRelations = vi.fn();
        render(<MetamodelSelectionActionsDialog canDistribute elementCount={4} onArrange={vi.fn()} onClear={vi.fn()} onDeleteElements={onDeleteElements} onDeleteRelations={onDeleteRelations} relationCount={3} />);

        fireEvent.click(screen.getByRole('button', { name: 'Delete selected relations' }));
        fireEvent.click(screen.getByRole('button', { name: 'Delete selected elements' }));

        expect(onDeleteRelations).toHaveBeenCalledTimes(1);
        expect(onDeleteElements).toHaveBeenCalledTimes(1);
    });
});
