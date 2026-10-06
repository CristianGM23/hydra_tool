import { describe, expect, it } from 'vitest';
import { translateUiText } from './translationCatalog';

describe('HYDRA Spanish copy quality for behavior designers', () => {
    it('uses complete natural translations instead of translating isolated words', () => {
        expect(translateUiText('Custom event type', 'es')).toBe('Tipo de evento personalizado');
        expect(translateUiText('Shared catalog and evidence-rule configuration.', 'es'))
            .toBe('Configuración compartida del catálogo y de las reglas de evidencia.');
        expect(translateUiText('Create or select a flow before using the diagram.', 'es'))
            .toBe('Crea o selecciona un flujo antes de usar el diagrama.');
        expect(translateUiText('Rules and progression', 'es')).toBe('Reglas y progresión');
    });

    it('translates actionable multiline errors while preserving file-system paths', () => {
        const path = String.raw`C:\Users\cristian.gomez\Documents\editor\data\gamification\broken.json`;
        const english = [
            `What went wrong: Could not parse document: ${path}`,
            'Why it is invalid: The submitted form could not be converted to the HYDRA document because one value has the wrong type, format or structure.',
            'How to correct it: Review the indicated field, correct it in the structured form and submit again. Avoid editing the raw JSON unless the Source tab explicitly requires it.',
            'Possible checks: Use the field tooltip to check the expected format. Restore the last valid value if the error appeared after a manual JSON edit.'
        ].join('\n');
        const spanish = translateUiText(english, 'es');
        expect(spanish).toContain(`Qué ha ocurrido: No se pudo analizar el documento: ${path}`);
        expect(spanish).toContain('Por qué no es válido: El formulario no pudo convertirse al documento HYDRA');
        expect(spanish).toContain('Cómo corregirlo: Revisa el campo indicado');
        expect(spanish).toContain('Comprobaciones posibles: Usa la ayuda del campo');
        expect(spanish).not.toContain('What went wrong');
        expect(spanish).not.toContain('submitted form');
    });

    it('preserves proper names and technical identifiers', () => {
        expect(translateUiText('Windows', 'es')).toBe('Windows');
        expect(translateUiText('Windows executable', 'es')).toBe('Ejecutable para Windows');
        expect(translateUiText('EventFlow', 'es')).toBe('EventFlow');
        expect(translateUiText('MongoDB', 'es')).toBe('MongoDB');
        expect(translateUiText('core.quickfix.applied', 'es')).toBe('core.quickfix.applied');
    });

    it('leaves unknown sentences untouched instead of producing mixed-language output', () => {
        const unknown = 'An unknown long sentence with Windows should remain untouched entirely.';
        expect(translateUiText(unknown, 'es')).toBe(unknown);
    });

    it('translates the internal Events, EventFlow and Gamification tabs', () => {
        expect(translateUiText('FORM', 'es')).toBe('FORMULARIO');
        expect(translateUiText('STRUCTURE', 'es')).toBe('ESTRUCTURA');
        expect(translateUiText('ARCHITECTURE', 'es')).toBe('ARQUITECTURA');
        expect(translateUiText('FLOWS', 'es')).toBe('FLUJOS');
        expect(translateUiText('SOURCE', 'es')).toBe('ORIGEN');
        expect(translateUiText('RUNTIME', 'es')).toBe('EJECUCIÓN');
        expect(translateUiText('Saved', 'es')).toBe('Guardado');
        expect(translateUiText('Bindings', 'es')).toBe('Enlaces');
    });
});
