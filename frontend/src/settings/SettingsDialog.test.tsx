import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppearanceSettingsProvider } from './AppearanceSettingsContext';
import { HydraLanguageProvider } from '../i18n';
import { SettingsDialog } from './SettingsDialog';

function renderDialog(onClose = vi.fn()) {
    render(<HydraLanguageProvider><AppearanceSettingsProvider><SettingsDialog open onClose={onClose} /></AppearanceSettingsProvider></HydraLanguageProvider>);
    return onClose;
}

describe('SettingsDialog', () => {
    beforeEach(() => {
        window.localStorage.clear();
        Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1440 });
        Object.defineProperty(window, 'innerHeight', { configurable: true, value: 900 });
    });
    it('configures font family and separate application areas', () => {
        renderDialog();
        expect(screen.getByLabelText('Font family')).toBeTruthy();
        expect(screen.getByLabelText('General application')).toBeTruthy();
        expect(screen.getByLabelText('Windows and panels')).toBeTruthy();
        expect(screen.getByLabelText('Tabs and command bars')).toBeTruthy();
        expect(screen.getByLabelText('Forms and fields')).toBeTruthy();
        expect(screen.getByLabelText('Diagram cards and palettes')).toBeTruthy();
        expect(screen.getByLabelText('Tooltips and guides')).toBeTruthy();
        expect(screen.getByLabelText('Adapt sizes automatically to screen')).toBeTruthy();
    });

    it('applies settings to root CSS variables and persists them', async () => {
        const onClose = renderDialog();
        fireEvent.change(screen.getByLabelText('Font family'), { target: { value: 'VERDANA' } });
        fireEvent.change(screen.getByLabelText('Forms and fields'), { target: { value: '24' } });
        fireEvent.click(screen.getByRole('button', { name: 'Apply settings' }));
        await vi.waitFor(() => expect(document.documentElement.style.getPropertyValue('--app-form-font-size')).toBe('24px'));
        expect(document.documentElement.style.getPropertyValue('--app-font-family')).toContain('Verdana');
        expect(document.documentElement.style.getPropertyValue('--app-diagram-font-size')).toBe('18px');
        expect(document.documentElement.style.getPropertyValue('--app-tooltip-font-size')).toBe('18px');
        expect(window.localStorage.getItem('modeling-editor.appearance.v1')).toContain('"formFontSize":24');
        expect(document.documentElement.dataset.responsiveSizing).toBe('true');
        expect(onClose).toHaveBeenCalledOnce();
    });

    it('allows responsive sizing to be disabled explicitly', async () => {
        renderDialog();
        fireEvent.click(screen.getByLabelText('Adapt sizes automatically to screen'));
        fireEvent.click(screen.getByRole('button', { name: 'Apply settings' }));
        await vi.waitFor(() => expect(document.documentElement.dataset.responsiveSizing).toBe('false'));
        expect(document.documentElement.style.getPropertyValue('--app-responsive-scale')).toBe('1');
    });


    it('switches the complete interface language without changing appearance settings', async () => {
        renderDialog();
        fireEvent.change(screen.getByLabelText('Application language'), { target: { value: 'es' } });
        expect(await screen.findByRole('heading', { name: 'Configuración' })).toBeInTheDocument();
        expect(screen.getByLabelText('Idioma de la aplicación')).toHaveValue('es');
        expect(window.localStorage.getItem('hydra.language.v1')).toBe('es');
        expect(window.localStorage.getItem('modeling-editor.appearance.v1')).toBeNull();
    });

    it('resets the enlarged defaults', () => {
        renderDialog();
        fireEvent.change(screen.getByLabelText('Forms and fields'), { target: { value: '32' } });
        fireEvent.click(screen.getByRole('button', { name: 'Reset defaults' }));
        expect((screen.getByLabelText('Forms and fields') as HTMLSelectElement).value).toBe('18');
    });
    it('offers an explicit dark mode and applies it independently from the operating system preference', async () => {
        renderDialog();
        const theme = screen.getByRole('group', { name: 'Application theme' });
        fireEvent.click(within(theme).getByRole('button', { name: 'Dark' }));
        fireEvent.click(screen.getByRole('button', { name: 'Apply settings' }));
        await vi.waitFor(() => expect(document.documentElement.dataset.hydraTheme).toBe('dark'));
        expect(document.documentElement.dataset.hydraColorScheme).toBe('dark');
        expect(window.localStorage.getItem('modeling-editor.appearance.v1')).toContain('"colorScheme":"DARK"');
    });

});
