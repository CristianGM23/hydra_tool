import { describe, expect, it } from 'vitest';

import type { MetamodelExportFormatDescriptor } from './types';

/**
 * Comprueba que el frontend admite todas las categorías.
 */
describe('metamodel export format categories', () => {

    /**
     * Comprueba las categorías ampliadas.
     */
    it('accepts all export categories', () => {
        const formats: MetamodelExportFormatDescriptor[] = [
            { id: 'xml', displayName: 'XML', extension: '.xml', category: 'SEMANTIC' },
            { id: 'antlr', displayName: 'ANTLR', extension: '.g4', category: 'GRAMMAR' },
            { id: 'markdown-agent', displayName: 'Agent Markdown', extension: '.agent.md', category: 'DOCUMENTATION' },
            { id: 'svg', displayName: 'SVG', extension: '.svg', category: 'DIAGRAM' },
            { id: 'java', displayName: 'Java', extension: '-java.zip', category: 'CODE' },
            { id: 'bundle', displayName: 'Bundle', extension: '-exports.zip', category: 'BUNDLE' }
        ];

        expect(formats.map(format => format.category)).toEqual(['SEMANTIC', 'GRAMMAR', 'DOCUMENTATION', 'DIAGRAM', 'CODE', 'BUNDLE']);
    });
});
