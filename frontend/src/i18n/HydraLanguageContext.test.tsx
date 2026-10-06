import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { HydraDomTranslator } from './HydraDomTranslator';
import { HydraLanguageProvider } from './HydraLanguageContext';
import { HydraLanguageToggle } from './HydraLanguageToggle';

function Fixture() {
    return <HydraLanguageProvider>
        <HydraDomTranslator />
        <HydraLanguageToggle />
        <section>
            <button aria-label="Create or import a metamodel">Create or import a metamodel</button>
            <label>Application settings<input placeholder="Select a metamodel" /></label>
            <code>MODEL_ID</code>
            <span data-hydra-i18n-skip="true">Model editor</span>
        </section>
    </HydraLanguageProvider>;
}

describe('HYDRA language mode', () => {
    beforeEach(() => window.localStorage.clear());

    it('switches visible content and accessibility text without changing technical content', async () => {
        render(<Fixture />);
        fireEvent.click(screen.getByRole('button', { name: 'ES' }));
        expect(await screen.findByRole('button', { name: 'Crear o importar un metamodelo' })).toHaveTextContent('Crear o importar un metamodelo');
        expect(screen.getByText('Configuración de la aplicación')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('Selecciona un metamodelo')).toBeInTheDocument();
        expect(screen.getByText('MODEL_ID')).toBeInTheDocument();
        expect(screen.getByText('Model editor')).toBeInTheDocument();
        expect(window.localStorage.getItem('hydra.language.v1')).toBe('es');
        expect(document.documentElement.lang).toBe('es');
    });

    it('restores the original English copy', async () => {
        render(<Fixture />);
        fireEvent.click(screen.getByRole('button', { name: 'ES' }));
        await screen.findByText('Configuración de la aplicación');
        fireEvent.click(screen.getByRole('button', { name: 'EN' }));
        expect(await screen.findByText('Application settings')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('Select a metamodel')).toBeInTheDocument();
    });
});
