import { describe, expect, it } from 'vitest';
import { HYDRA_MODULES } from '../application-shell/moduleRegistry';
import { translateUiText } from './translationCatalog';

const representativeInterfaceCopy = [
    'Home', 'Settings', 'Design', 'Behavior', 'Delivery', 'Operations',
    'Create or import a metamodel', 'Open model editor', 'Active semantic context',
    'Validation rule', 'Quick fix', 'Event flow', 'Gamification designer',
    'Palette group', 'Palette tool', 'Visual connections', 'Containment',
    'Import destination', 'Safety and traceability', 'Recommended next step',
    'Application language', 'Font family', 'Forms and fields', 'Tooltips and guides',
    'Required field', 'Information about Name', 'Select a metamodel',
    'Could not load model', 'Could not parse document', 'No matching values',
    'Preview materialization', 'Rollback materialization', 'Search problems',
    'Automatic detection', 'Dependency cycles', 'Publication and updates'
];

describe('Spanish application coverage', () => {
    it('translates module names and descriptions without changing module IDs', () => {
        for (const module of HYDRA_MODULES) {
            expect(translateUiText(module.title, 'es')).not.toBe(module.title);
            expect(translateUiText(module.description, 'es')).not.toBe(module.description);
            expect(module.id).toMatch(/^[A-Z_]+$/);
        }
    });

    it('covers representative fields, tooltips, forms, buttons and diagnostics', () => {
        for (const text of representativeInterfaceCopy) {
            expect(translateUiText(text, 'es'), text).not.toBe(text);
        }
    });

    it('does not translate technical values or persisted identifiers', () => {
        for (const technical of ['MODEL_ID', 'people.ecore', 'sourceUri', 'CONTAINMENT', 'HYDRA', 'application/json']) {
            expect(translateUiText(technical, 'es')).toBe(technical);
        }
    });
});
