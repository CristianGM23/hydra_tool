import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { EdgeStyleEditor } from './EdgeStyleEditor';
import { NodeStyleEditor } from './NodeStyleEditor';

const nodeStyle = { fillColor: '#FFFFFF', headerColor: '#EEEEEE', strokeColor: '#333333', textColor: '#111111', fontFamily: 'Arial', fontSize: 12, fontWeight: 600, strokeWidth: 2, borderRadius: 4 };
const edgeStyle = { strokeColor: '#333333', textColor: '#111111', fontFamily: 'Arial', fontSize: 12, fontWeight: 600, strokeWidth: 2, lineStyle: 'SOLID' as const };

describe('style customization accordions', () => {
    it('keeps every node section closed by default and opens only the selected one', () => {
        render(<NodeStyleEditor value={nodeStyle} onChange={vi.fn()} />);
        const fill = screen.getByRole('button', { name: /Fill colors/ });
        const border = screen.getByRole('button', { name: /Border/ });
        const font = screen.getByRole('button', { name: /Text and font/ });

        expect(fill).toHaveAttribute('aria-expanded', 'false');
        expect(border).toHaveAttribute('aria-expanded', 'false');
        expect(font).toHaveAttribute('aria-expanded', 'false');
        fireEvent.click(border);
        expect(border).toHaveAttribute('aria-expanded', 'true');
        fireEvent.click(font);
        expect(border).toHaveAttribute('aria-expanded', 'false');
        expect(font).toHaveAttribute('aria-expanded', 'true');
    });

    it('groups edge properties into connection and label sections', () => {
        render(<EdgeStyleEditor value={edgeStyle} onChange={vi.fn()} />);
        const connection = screen.getByRole('button', { name: /Connection/ });
        const label = screen.getByRole('button', { name: /Label and font/ });

        expect(connection).toHaveAttribute('aria-expanded', 'false');
        fireEvent.click(connection);
        expect(screen.getByLabelText('Connection width')).toBeTruthy();
        fireEvent.click(label);
        expect(connection).toHaveAttribute('aria-expanded', 'false');
        expect(screen.getByLabelText('Font family')).toBeTruthy();
    });
});
