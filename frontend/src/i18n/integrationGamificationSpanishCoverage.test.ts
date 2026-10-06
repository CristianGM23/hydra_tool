import { describe, expect, it } from 'vitest';
import { translateUiText } from './translationCatalog';

describe('Spanish integration and gamification coverage', () => {
    it('translates integration diagnostics and artifact resolution copy shown in compatibility reports', () => {
        expect(translateUiText('Event definition model was not found for this metamodel', 'es')).toBe('No se encontró el modelo de definiciones de eventos para este metamodelo.');
        expect(translateUiText('The profile marks this HYDRA artifact as required, but HYDRA cannot find a saved document with that ID for the selected metamodel.', 'es')).toMatch(/artefacto de HYDRA como obligatorio/i);
        expect(translateUiText('Runtime integration is enabled but no host element mappings are defined', 'es')).toMatch(/runtime.*mappings/i);
        expect(translateUiText('Canonical metamodel is the owning document', 'es')).toBe('El metamodelo es el documento propietario.');
        expect(translateUiText('Event Definitions', 'es')).toBe('Definiciones de eventos');
        expect(translateUiText('Resolved', 'es')).toBe('Resuelto');
        expect(translateUiText('Not found', 'es')).toBe('No encontrado');
    });

    it('translates gamification palette empty states and dependency guidance', () => {
        expect(translateUiText('No point types defined.', 'es')).toBe('No hay tipos de puntos definidos.');
        expect(translateUiText('No evidence rules defined.', 'es')).toBe('No hay reglas de evidencia definidas.');
        expect(translateUiText('Requirements to create it', 'es')).toBe('Requisitos para crearlo');
        expect(translateUiText('Blocked: create at least one point type first.', 'es')).toMatch(/Bloqueado/);
        expect(translateUiText('A rule must reference a known verified event. HYDRA includes reserved system events; custom events must first be created and saved in Event Designer.', 'es')).toMatch(/Diseñador de eventos/);
    });
});
