import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { MetamodelExportDialog } from './MetamodelExportDialog';

/**
 * Comprueba los diálogos de ciclo de vida del metamodelo.
 */
describe('metamodel lifecycle dialogs', () => {

    /**
     * Comprueba que las gramáticas aparezcan antes de PlantUML y Markdown.
     */
    it('renders semantic and grammar exports', () => {
        render(<MetamodelExportDialog formats={[{ id: 'ecore', displayName: 'Ecore', extension: '.ecore', category: 'SEMANTIC' }, { id: 'langium', displayName: 'Langium grammar', extension: '.langium', category: 'GRAMMAR' }, { id: 'xtext', displayName: 'Xtext grammar', extension: '.xtext', category: 'GRAMMAR' }, { id: 'antlr', displayName: 'ANTLR grammar', extension: '.g4', category: 'GRAMMAR' }]} metamodelId="company" onClose={vi.fn()} />);

        expect(screen.getByText('Langium grammar')).toBeTruthy();
        expect(screen.getByText('Xtext grammar')).toBeTruthy();
        expect(screen.getByText('ANTLR grammar')).toBeTruthy();
        expect(document.querySelector('[data-export-url="/api/metamodels/company/exports/langium"]')).not.toBeNull();
    });
});
