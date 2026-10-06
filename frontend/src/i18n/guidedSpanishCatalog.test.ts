import { describe, expect, it } from 'vitest';
import { translateUiText } from './translationCatalog';

describe('V4.4 guided Spanish catalogue', () => {
    it('translates the common guided progression language without touching technical product names', () => {
        expect(translateUiText('Guided', 'es')).toBe('Guiado');
        expect(translateUiText('Step incomplete', 'es')).toBe('Paso incompleto');
        expect(translateUiText('Step 2 of 5', 'es')).toBe('Paso 2 de 5');
        expect(translateUiText('3/5 complete', 'es')).toBe('3/5 completados');
        expect(translateUiText('Docker', 'es')).toBe('Docker');
        expect(translateUiText('Kubernetes', 'es')).toBe('Kubernetes');
        expect(translateUiText('EventFlow', 'es')).toBe('EventFlow');
    });

    it('covers enriched event multi-selection and payload help', () => {
        expect(translateUiText('Allowed sources', 'es')).toBe('Orígenes permitidos');
        expect(translateUiText('Allowed subjects', 'es')).toBe('Sujetos permitidos');
        expect(translateUiText('Payload fields', 'es')).toBe('Campos del payload');
        expect(translateUiText('Validation runtime', 'es')).toBe('Runtime de validación');
        expect(translateUiText('Standalone editor runtime', 'es')).toBe('Runtime del editor independiente');
    });

    it('covers the new transversal delivery and operations assistants', () => {
        expect(translateUiText('Guided distribution', 'es')).toBe('Distribución guiada');
        expect(translateUiText('Guided publication', 'es')).toBe('Publicación guiada');
        expect(translateUiText('Guided representable import', 'es')).toBe('Importación representable guiada');
        expect(translateUiText('Guided round-trip', 'es')).toBe('Ida y vuelta guiada');
        expect(translateUiText('Guided native verification', 'es')).toBe('Verificación nativa guiada');
    });

    it('covers guided metamodel authoring without translating technical deployment names', () => {
        expect(translateUiText('Guided metamodel creation', 'es')).toBe('Creación guiada del metamodelo');
        expect(translateUiText('Properties and relationships', 'es')).toBe('Propiedades y relaciones');
        expect(translateUiText('Visual / manual', 'es')).toBe('Visual / edición manual');
        expect(translateUiText('2 class(es), 1 enum(s), 3 datatype(s).', 'es')).toBe('2 clase(s), 1 enumeración(es), 3 tipo(s) de datos.');
        expect(translateUiText('4 relation(s) currently defined. Relations are optional.', 'es')).toBe('4 relación(es) definida(s) actualmente. Las relaciones son opcionales.');
    });
});
