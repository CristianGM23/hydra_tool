import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DiagramInteractionToolbar } from './DiagramInteractionToolbar';

describe('DiagramInteractionToolbar interaction and category menus', () => {
    it('keeps Select, Pan and Connect directly visible while secondary tools stay categorized', () => {
        const onModeChange = vi.fn();
        render(<DiagramInteractionToolbar mode="SELECT" onModeChange={onModeChange} allowConnect onUndo={vi.fn()} onRedo={vi.fn()} onDuplicate={vi.fn()} onDelete={vi.fn()} onAutoLayout={vi.fn()} onFit={vi.fn()} />);
        expect(screen.getByRole('button', { name: 'Select' })).toHaveAttribute('aria-pressed', 'true');
        fireEvent.click(screen.getByRole('button', { name: 'Pan' }));
        expect(onModeChange).toHaveBeenCalledWith('PAN');
        fireEvent.click(screen.getByRole('button', { name: 'Connect' }));
        expect(onModeChange).toHaveBeenCalledWith('CONNECT');
        expect(screen.getByRole('button', { name: /History/ })).toBeTruthy();
    });

    it('exposes the ELK-style layout command when a canvas supports it', () => {
        const onElkLayout = vi.fn();
        render(<DiagramInteractionToolbar mode="SELECT" onModeChange={vi.fn()} onElkLayout={onElkLayout} />);
        fireEvent.click(screen.getByRole('button', { name: 'Canvas' }));
        fireEvent.click(screen.getByRole('button', { name: 'ELK-style layout' }));
        expect(onElkLayout).toHaveBeenCalledTimes(1);
    });

    it('keeps multi-selection layout operations inside Align and Distribute categories', () => {
        render(<DiagramInteractionToolbar mode="SELECT" onModeChange={vi.fn()} selection={{ elementCount: 3, relationCount: 1, onArrange: vi.fn(), onClear: vi.fn(), onDeleteElements: vi.fn(), onDeleteRelations: vi.fn() }} />);
        expect(screen.queryByRole('button', { name: 'Vertical line' })).toBeNull();
        fireEvent.click(screen.getByRole('button', { name: 'Align' }));
        expect(screen.getByRole('button', { name: 'Vertical line' })).toBeTruthy();
        expect(screen.getByRole('button', { name: 'Horizontal line' })).toBeTruthy();
    });
});
