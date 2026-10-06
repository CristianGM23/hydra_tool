import { describe, expect, it } from 'vitest';
import { translateUiText } from './translationCatalog';

describe('HYDRA translation catalog', () => {
    it('translates stable interface copy to Spanish', () => {
        expect(translateUiText('Create or import a metamodel', 'es')).toBe('Crear o importar un metamodelo');
        expect(translateUiText('Could not load model', 'es')).toBe('No se pudo cargar modelo');
        expect(translateUiText('Application settings', 'es')).toBe('Configuración de la aplicación');
    });

    it('preserves English and technical identifiers', () => {
        expect(translateUiText('Create or import a metamodel', 'en')).toBe('Create or import a metamodel');
        expect(translateUiText('HYDRA Ecore JSON sourceUri', 'es')).toBe('HYDRA Ecore JSON sourceUri');
        expect(translateUiText('people.ecore', 'es')).toBe('people.ecore');
    });

    it('translates dynamic package counts without changing the number', () => {
        expect(translateUiText('3 root package(s)', 'es')).toBe('3 paquete(s) raíz');
    });

    it('translates guided condition summaries and container export messages', () => {
        expect(translateUiText('Condition 2', 'es')).toBe('Condición 2');
        expect(translateUiText('Customer.age is greater than or equal to 18', 'es')).toBe('Customer.age es mayor o igual que 18');
        expect(translateUiText('number of elements in Customer.orders is greater than 0', 'es')).toBe('el número de elementos en Customer.orders es mayor que 0');
        expect(translateUiText('Docker editor package generated · 10 portable capabilities · 1 optional layer(s) were not available', 'es'))
            .toBe('Paquete de editor Docker generado · 10 capacidades portables · 1 capa(s) opcional(es) no estaban disponibles');
    });


    it('does not translate proper names or generate half-translated sentences', () => {
        expect(translateUiText('Windows', 'es')).toBe('Windows');
        expect(translateUiText('Windows executable', 'es')).toBe('Ejecutable para Windows');
        expect(translateUiText('Custom event type', 'es')).toBe('Tipo de evento personalizado');
        expect(translateUiText('An unknown long sentence remains complete.', 'es'))
            .toBe('An unknown long sentence remains complete.');
        expect(translateUiText('Microsoft Entra admin center → App registrations → New registration', 'es'))
            .toBe('Microsoft Entra admin center → App registrations → New registration');
    });
});
