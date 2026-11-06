import { fireEvent, render, screen } from '@testing-library/react';
import { createPortal } from 'react-dom';
import { describe, expect, it, vi } from 'vitest';
import { DesignerCommandBar, DesignerCommandGroup } from './DesignerCommandBar';
import { DesignerDropdown } from './DesignerDropdown';

function Toolbar({ onSave = vi.fn() }: { onSave?: () => void }) {
    return <DesignerCommandBar
        title="Designer"
        description="Compact toolbar"
        actions={<><DesignerCommandGroup label="Changes"><button type="button" onClick={onSave}>Save</button><button type="button">Delete</button></DesignerCommandGroup><DesignerCommandGroup label="Quality"><button type="button">Check</button></DesignerCommandGroup></>}
        viewsLabel="Workspace"
        views={<nav><button type="button">Form</button><button type="button">Diagram</button><button type="button">Source</button></nav>}
    />;
}

describe('Designer command dropdowns', () => {
    it('keeps commands collapsed until their functional category is opened', () => {
        render(<Toolbar />);
        expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
        fireEvent.click(screen.getByRole('button', { name: 'Changes' }));
        expect(screen.getByRole('button', { name: 'Save' })).toBeTruthy();
        expect(screen.queryByRole('button', { name: 'Check' })).toBeNull();
    });

    it('runs an action and closes its category afterwards', () => {
        const onSave = vi.fn();
        render(<Toolbar onSave={onSave} />);
        fireEvent.click(screen.getByRole('button', { name: 'Changes' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));
        expect(onSave).toHaveBeenCalledTimes(1);
        expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
    });

    it('keeps primary workspace views visible while commands stay in dropdowns', () => {
        render(<Toolbar />);
        expect(screen.getByText('Workspace')).toBeTruthy();
        expect(screen.getByRole('button', { name: 'Form' })).toBeTruthy();
        expect(screen.getByRole('button', { name: 'Diagram' })).toBeTruthy();
        expect(screen.getByRole('button', { name: 'Source' })).toBeTruthy();
        expect(screen.queryByRole('button', { name: 'Workspace' })).toBeNull();
    });


    it('renders command menus in the global dropdown overlay layer', () => {
        render(<Toolbar />);
        fireEvent.click(screen.getByRole('button', { name: 'Changes' }));
        const popup = screen.getByRole('group', { name: 'Changes' });
        expect(popup.getAttribute('data-hydra-overlay-layer')).toBe('DROPDOWN');
        expect(document.body.contains(popup)).toBe(true);
    });

    it('stays open while the user works inside a dialog that the dropdown renders in its own portal', () => {
        render(<DesignerDropdown label="Interchange">
            <button type="button">Import visual definition</button>
            {createPortal(<div role="dialog" aria-label="Import dialog"><button type="button">Analyze</button></div>, document.body)}
        </DesignerDropdown>);
        fireEvent.click(screen.getByRole('button', { name: 'Interchange' }));
        const analyze = screen.getByRole('button', { name: 'Analyze' });
        fireEvent.pointerDown(analyze);
        fireEvent.click(analyze);
        expect(screen.getByRole('button', { name: 'Import visual definition' })).toBeTruthy();
        expect(screen.getByRole('dialog', { name: 'Import dialog' })).toBeTruthy();
        // Una pulsación realmente fuera del desplegable sí lo cierra.
        fireEvent.pointerDown(document.body);
        expect(screen.queryByRole('button', { name: 'Import visual definition' })).toBeNull();
    });

    it('closes an open category with Escape', () => {
        render(<Toolbar />);
        const trigger = screen.getByRole('button', { name: 'Quality' });
        fireEvent.click(trigger);
        expect(screen.getByRole('button', { name: 'Check' })).toBeTruthy();
        fireEvent.keyDown(document, { key: 'Escape' });
        expect(screen.queryByRole('button', { name: 'Check' })).toBeNull();
        expect(document.activeElement).toBe(trigger);
    });
});
