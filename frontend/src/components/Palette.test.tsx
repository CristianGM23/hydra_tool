import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { Palette } from './Palette';
import type { PaletteEntry } from '../types/editor';

const entries: PaletteEntry[] = [
    { classId: 'sample#//Model', packageName: 'sample', className: 'Model' },
    { classId: 'sample#//Node', packageName: 'sample', className: 'Node' }
];

describe('Palette', () => {
    it('renders available classes', () => {
        render(<Palette entries={entries} onCreate={() => undefined} />);

        expect(screen.getByText('Model')).toBeInTheDocument();
        expect(screen.getByText('Node')).toBeInTheDocument();
    });

    it('invokes onCreate with the selected class', () => {
        const onCreate = vi.fn();
        render(<Palette entries={entries} onCreate={onCreate} />);

        fireEvent.click(screen.getByRole('button', { name: /Node/ }));
        expect(onCreate).toHaveBeenCalledWith(entries[1]);
    });

    it('shows an empty message without entries', () => {
        render(<Palette entries={[]} onCreate={() => undefined} />);

        expect(screen.getByText('No elements available')).toBeInTheDocument();
    });
});