import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createGrammar } from './grammarDesignerModel';
import type { GrammarDefinitionModel } from './types';

// Regresión del fallo hallado por la E2E real «grammar-metamodel-link»: el diseñador carga de forma asíncrona la última gramática guardada y,
// si esa carga terminaba DESPUÉS de que el usuario hubiera pulsado «New grammar» o empezado a editar, pisaba su borrador (se perdía lo escrito).

const resolvers: { load?: (value: { grammar: GrammarDefinitionModel; etag: string }) => void } = {};
const saved = { ...createGrammar(), id: 'docs-saved', name: 'Docs saved language' } as GrammarDefinitionModel;

vi.mock('./api', () => ({
    listGrammars: vi.fn(async () => [{ id: 'docs-saved', name: 'Docs saved language', technology: 'LANGIUM' }]),
    listToolchainProfiles: vi.fn(async () => []),
    listGrammarProviders: vi.fn(async () => []),
    // la carga de la gramática guardada queda pendiente hasta que la prueba la resuelve: simula una máquina lenta
    loadGrammar: vi.fn(() => new Promise(resolve => { resolvers.load = resolve as never; })),
    loadActiveGrammarCompilation: vi.fn(async () => null),
    loadGrammarCompilationProgress: vi.fn(async () => null),
    analyzeGrammar: vi.fn(() => new Promise(() => { /* el análisis nunca termina: no es lo que se prueba */ })),
    cancelGrammarAnalysis: vi.fn(async () => undefined),
    compileGrammar: vi.fn(),
    createGrammarApi: vi.fn(),
    replaceGrammarApi: vi.fn(),
    prepareGrammarPlatformConversion: vi.fn(),
    projectGrammarClassDiagram: vi.fn(async () => null),
}));
vi.mock('../metamodel-designer/api', () => ({ listCanonicalMetamodels: vi.fn(async () => []) }));
vi.mock('./GrammarMonacoEditor', () => ({ GrammarMonacoEditor: () => <div data-testid="monaco"/> }));
vi.mock('./GrammarClassDiagram', () => ({ GrammarClassDiagram: () => <div/> }));
vi.mock('./GrammarRuleSyntaxGraphs', () => ({ GrammarRuleSyntaxGraphs: () => <div/> }));
vi.mock('./GrammarInferredMetamodelView', () => ({ GrammarInferredMetamodelView: () => <div/> }));
vi.mock('./GrammarGeneratedCodeView', () => ({ GrammarGeneratedCodeView: () => <div/> }));
vi.mock('./GrammarTextualCustomization', () => ({ GrammarTextualCustomization: () => <div/> }));

import { GrammarDesigner } from './GrammarDesigner';

describe('GrammarDesigner: la carga inicial asíncrona no pisa el trabajo del usuario', () => {
    beforeEach(() => { try { localStorage.clear(); } catch { /* sin almacenamiento */ } resolvers.load = undefined; });

    it('una gramática nueva y lo que se escribe en ella sobreviven a una carga inicial que termina tarde', async () => {
        render(<GrammarDesigner/>);
        await waitFor(() => expect(resolvers.load).toBeDefined()); // la lista ya llegó y la carga de la última gramática está en curso
        fireEvent.click(screen.getByRole('button', { name: /^New grammar$/i }));
        const id = await screen.findByLabelText(/^ID/);
        fireEvent.change(id, { target: { value: 'mi-borrador' } });
        expect((id as HTMLInputElement).value).toBe('mi-borrador');
        await act(async () => { resolvers.load?.({ grammar: saved, etag: 'etag-1' }); });
        expect((screen.getByLabelText(/^ID/) as HTMLInputElement).value).toBe('mi-borrador'); // antes: 'docs-saved'
    });

    it('sin intervención del usuario, la última gramática guardada sí se carga', async () => {
        render(<GrammarDesigner/>);
        await waitFor(() => expect(resolvers.load).toBeDefined());
        await act(async () => { resolvers.load?.({ grammar: saved, etag: 'etag-1' }); });
        await waitFor(() => expect((screen.getByLabelText(/^ID/) as HTMLInputElement).value).toBe('docs-saved'));
    });
});
