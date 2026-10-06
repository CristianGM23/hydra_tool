import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HydraLanguageProvider } from '../i18n';
import { CapabilityReadinessView } from './CapabilityReadinessView';
import * as api from './api';
import * as grammarApi from '../grammar-designer/api';

vi.mock('./api');
vi.mock('../grammar-designer/api');
vi.mock('./CapabilityTransactionPanel', () => ({ CapabilityTransactionPanel: () => <div>transaction</div> }));

describe('CapabilityReadinessView localization', () => {
    beforeEach(() => {
        window.localStorage.setItem('hydra.language.v1', 'es');
        vi.mocked(api.loadCapabilityReadiness).mockResolvedValue({
            contextId: 'm1', contextName: 'Demo', contextType: 'METAMODEL', semanticContextId: 'm1', score: 50, configuredLayers: 1, totalLayers: 2, warnings: [], recommendedNextSteps: [],
            layers: [
                { id: 'STRUCTURE', displayName: 'Metamodel structure', configured: true, artifactCount: 1, metrics: { relations: 1, classes: 5 }, recommendedModule: 'METAMODEL' },
                { id: 'EVENTS', displayName: 'Events and bindings', configured: false, artifactCount: 0, metrics: { eventTypes: 2, bindings: 1 }, recommendedModule: 'EVENTS' }
            ]
        });
    });
    it('translates card state, layer names and metric labels instead of exposing backend keys', async () => {
        render(<HydraLanguageProvider><CapabilityReadinessView contextId="m1" contextType="METAMODEL" compact onNavigate={vi.fn()} /></HydraLanguageProvider>);
        expect(await screen.findByText('Estructura del metamodelo')).toBeInTheDocument();
        expect(screen.getByText('Configurado')).toBeInTheDocument();
        expect(screen.getByText('No configurado')).toBeInTheDocument();
        expect(screen.getByText('Relaciones')).toBeInTheDocument();
        expect(screen.getByText('Clases')).toBeInTheDocument();
        expect(screen.getByText('Tipos de evento')).toBeInTheDocument();
        expect(screen.getByText('1/2 áreas preparadas')).toBeInTheDocument();
        expect(screen.queryByText(/capacidades/i)).not.toBeInTheDocument();
    });

    it('shows a real grammar summary instead of a blocking Home error when aggregated readiness is unavailable', async () => {
        vi.mocked(api.loadCapabilityReadiness).mockRejectedValue(new Error('No capability inventory'));
        vi.mocked(grammarApi.loadGrammar).mockResolvedValue({ grammar: {
            schemaVersion:'1.0', id:'main-language', name:'Main language', technology:'LANGIUM', authoringMode:'GRAMMAR_FIRST', metamodelId:'', toolchainProfileId:'langium-4.3-browser', entryDocumentId:'main', entryRule:'Model',
            documents:[{ id:'main', relativePath:'main.langium', languageId:'langium', content:'grammar Main', sourceHash:'', entryPoint:true, generated:false }],
            semanticBindings:{ schemaVersion:'1.0', bindings:[{ grammarElementId:'Model', grammarElementKind:'RULE', semanticElementId:'Model', semanticElementKind:'CLASS', confidence:1, provenance:'TEST', metadata:{} }], annotations:{} },
            syntaxStyleDefinitionId:'', settings:{ 'hydra.textual.role.KEYWORD':'#1d4ed8|1|0', 'hydra.textual.hover.Model.description':'Modelo raíz' }, annotations:{}
        }, etag:'e1' });
        vi.mocked(grammarApi.loadActiveGrammarCompilation).mockResolvedValue(null);
        render(<HydraLanguageProvider><CapabilityReadinessView contextId="main-language" contextType="GRAMMAR" compact onNavigate={vi.fn()} /></HydraLanguageProvider>);
        expect(await screen.findByText('Definición de gramática')).toBeInTheDocument();
        expect(screen.getByText('Modelo semántico')).toBeInTheDocument();
        expect(screen.getByText('Editor textual')).toBeInTheDocument();
        expect(screen.getByText('Editor generado y ejecución')).toBeInTheDocument();
        expect(screen.queryByText('Compilación y runtime')).not.toBeInTheDocument();
        expect(screen.getByText('La gramática se ha seleccionado correctamente.')).toBeInTheDocument();
        expect(screen.queryByText('Preparación no disponible')).not.toBeInTheDocument();
    });

});
