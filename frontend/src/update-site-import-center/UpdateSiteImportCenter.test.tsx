import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UpdateSiteImportCenter } from './UpdateSiteImportCenter';
import type { UpdateSiteImportAnalysis, UpdateSiteImportResult } from './types';

vi.mock('./api', () => ({ analyzeUpdateSite: vi.fn(), importUpdateSite: vi.fn(), reconcileUpdateSite: vi.fn(),
    materializeUpdateSiteLayers: vi.fn(), rollbackUpdateSiteMaterialization: vi.fn(), loadUpdateSiteExtensionAdapters: vi.fn(),
    mergeUpdateSiteLayers: vi.fn(), rollbackUpdateSiteMerge: vi.fn(), analyzeRemoteUpdateSite: vi.fn(),
    previewDeclarativeAdapter: vi.fn(), importDeclarativeAdapter: vi.fn(), loadUpdateSiteCacheMetrics: vi.fn(),
    clearUpdateSiteCache: vi.fn(), createRemoteUpdateSiteSnapshot: vi.fn(), importRemoteUpdateSiteSnapshot: vi.fn(),
    analyzeRemoteUpdateSiteSnapshot: vi.fn(), checkRemoteUpdateSiteSnapshotDrift: vi.fn(), downloadRemoteUpdateSiteSnapshot: vi.fn(),
    reviewRemoteUpdateSiteSnapshotLicenses: vi.fn(),
    importRemoteSnapshotRecovery: vi.fn(), reconcileRemoteSnapshot: vi.fn(), materializeRemoteSnapshot: vi.fn(),
    mergeRemoteSnapshotLayers: vi.fn(), loadDeclarativeAdapterDocuments: vi.fn(), loadDeclarativeAdapterVersions: vi.fn(),
    testDeclarativeAdapter: vi.fn(), setDeclarativeAdapterEnabled: vi.fn(), deleteDeclarativeAdapter: vi.fn(),
    downloadDeclarativeAdapter: vi.fn(), downloadDeclarativeAdapterVersion: vi.fn(), loadCapabilityPerformanceBudgets: vi.fn(),
    evaluateCapabilityPerformance: vi.fn(), loadCapabilityPerformanceReports: vi.fn(), downloadCapabilityPerformanceHtml: vi.fn() }));
import { analyzeRemoteUpdateSite, analyzeUpdateSite, createRemoteUpdateSiteSnapshot, importRemoteSnapshotRecovery,
    importUpdateSite, loadUpdateSiteExtensionAdapters, materializeRemoteSnapshot, materializeUpdateSiteLayers,
    reconcileUpdateSite } from './api';

const analysis: UpdateSiteImportAnalysis = {
    analysisId: 'p2-analysis', generatedAt: '2026-07-31T12:00:00Z', selectedCandidateId: 'exact', importable: true,
    repository: {
        name: 'Library Update Site', sourceFingerprint: '1234567890abcdef1234567890abcdef',
        contentMetadataFound: true, artifactMetadataFound: true, repositoryFileCount: 24, repositorySizeBytes: 4096,
        features: [{ id: 'library.feature', version: '1.2.0', name: 'Library', sourcePath: 'features/library.jar!/feature.xml', pluginIds: ['library.model'], dependencies: [] }],
        bundles: [{ id: 'library.model', version: '1.2.0', name: 'Library model', kind: 'MODEL_PLUGIN', sourcePath: 'plugins/library.model.jar',
            classificationSignals: ['Ecore found'], dependencies: [], artifacts: [{ path: 'plugins/library.model.jar!/model/library.ecore', kind: 'ECORE', bundleId: 'library.model', sizeBytes: 100, sha256: 'abc' }], manifestHeaders: {} }],
        unownedArtifacts: [], missingDependencies: [], dependencyCycles: [], diagnostics: []
    },
    candidates: [
        { id: 'exact', level: 1, method: 'DIRECT_METAMODEL', confidence: 'EXACT', sourcePath: 'plugins/library.model.jar!/model/library.ecore', sourceBundleId: 'library.model', format: 'ECORE', score: 100, importable: true, rationale: 'Original Ecore found', warnings: [] },
        { id: 'inferred', level: 5, method: 'CODE_INFERENCE', confidence: 'INFERRED', sourcePath: 'plugins/library.model.jar!/Book.class', sourceBundleId: 'library.model', format: 'JAVA_CLASS', score: 30, importable: true, rationale: 'Inferred from code', warnings: ['Review inferred types'] }
    ],
    metamodelPreview: {
        detectedFormat: 'ecore', supportLevel: 'EXACT',
        metamodel: { schemaVersion: '2.1', id: 'library', name: 'Library', classes: [], enums: [], dataTypes: [], relations: [], annotations: {} },
        targets: null, diagram: null, warnings: [], statistics: { classes: 2, attributes: 1, enums: 0, dataTypes: 0, relations: 1, diagramNodes: 2, diagramEdges: 1 }
    } as unknown as UpdateSiteImportAnalysis['metamodelPreview'],
    provenance: [{ elementId: 'relation:Book:authors', elementPath: '//Book/authors', origin: 'plugins/library.model_1.2.0.jar', sourcePath: 'plugins/library.model.jar!/model/library.ecore', format: 'ECORE', originalElement: '//Book/authors', method: 'DIRECT_METAMODEL', confidence: 'EXACT', warnings: [] }],
    recoveredLayers: {
        palettes: [{ id: 'palette', label: 'Library', order: 0, sourcePath: 'library.gmftool', confidence: 'DERIVED', tools: [{ id: 'book-tool', label: 'Book', kind: 'NODE', targetElement: 'Book', sourcePath: 'library.gmftool', confidence: 'DERIVED' }] }],
        representations: [{ id: 'diagram', name: 'Library diagram', technology: 'GMF', kind: 'DIAGRAM', rootElements: ['Library'], sourcePath: 'library.gmfmap', confidence: 'DERIVED', warnings: [] }],
        mappings: [{ id: 'mapping', semanticElement: 'Book', visualElement: 'BookFigure', mappingKind: 'NODE', sourcePath: 'library.gmfmap', confidence: 'DERIVED', diagnostic: '' }], diagnostics: []
    }, diagnostics: []
};

describe('Eclipse update site recovery center', () => {
    function openAdvancedTools(): void {
        const summary = screen.getByText('Specialized capabilities').closest('summary')!;
        if (!summary.closest('details')?.hasAttribute('open')) fireEvent.click(summary);
    }

    function renderAdvanced(props: Parameters<typeof UpdateSiteImportCenter>[0] = {}): void {
        render(<UpdateSiteImportCenter {...props} />);
        fireEvent.click(screen.getByRole('button', { name: 'Advanced workspace' }));
    }

    beforeEach(() => {
        vi.mocked(analyzeUpdateSite).mockReset();
        vi.mocked(importUpdateSite).mockReset();
        vi.mocked(reconcileUpdateSite).mockReset();
        vi.mocked(materializeUpdateSiteLayers).mockReset();
        vi.mocked(analyzeRemoteUpdateSite).mockReset();
        vi.mocked(createRemoteUpdateSiteSnapshot).mockReset();
        vi.mocked(importRemoteSnapshotRecovery).mockReset();
        vi.mocked(materializeRemoteSnapshot).mockReset();
        vi.mocked(loadUpdateSiteExtensionAdapters).mockReset();
        vi.mocked(loadUpdateSiteExtensionAdapters).mockResolvedValue([]);
    });

    it('opens the guided local picker from the Archive or folder source card and keeps a separate directory action', () => {
        render(<UpdateSiteImportCenter />);
        const archive = screen.getByLabelText('Guided update site archive') as HTMLInputElement;
        const directory = screen.getByLabelText('Guided update site directory') as HTMLInputElement;
        Object.defineProperty(archive, 'showPicker', { configurable: true, value: undefined });
        Object.defineProperty(directory, 'showPicker', { configurable: true, value: undefined });
        const archiveClick = vi.spyOn(archive, 'click').mockImplementation(() => undefined);
        const directoryClick = vi.spyOn(directory, 'click').mockImplementation(() => undefined);

        fireEvent.click(screen.getByRole('button', { name: /Archive or folder/i }));
        expect(archiveClick).toHaveBeenCalledOnce();
        fireEvent.click(screen.getByRole('button', { name: 'Choose directory' }));
        expect(directoryClick).toHaveBeenCalledOnce();
    });

    it('starts with a compact source form and progressively discloses secondary options', () => {
        renderAdvanced();
        expect(screen.getByText('Repository setup')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Choose archive' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Choose directory' })).toBeInTheDocument();
        expect(screen.getByLabelText('Analysis and import actions')).toBeInTheDocument();
        expect(screen.getByLabelText('Update site archive')).toHaveClass('update-site-hidden-file-input');
        const archiveInput = screen.getByLabelText('Update site archive') as HTMLInputElement;
        Object.defineProperty(archiveInput, 'showPicker', { configurable: true, value: undefined });
        const archiveClick = vi.spyOn(archiveInput, 'click').mockImplementation(() => undefined);
        fireEvent.click(screen.getByRole('button', { name: 'Choose archive' }));
        expect(archiveClick).toHaveBeenCalledOnce();
        expect(screen.getByText('Recovery options').closest('details')).not.toHaveAttribute('open');
        const advanced = screen.getByText('Specialized capabilities').closest('summary')!;
        expect(advanced.closest('details')).not.toHaveAttribute('open');
        fireEvent.click(advanced);
        expect(advanced.closest('details')).toHaveAttribute('open');
        expect(screen.getByRole('tab', { name: 'Remote p2' })).toBeInTheDocument();
    });

    it('shows the staged p2 flow, bundle roles, confidence and recovered layers', async () => {
        vi.mocked(analyzeUpdateSite).mockResolvedValue(analysis);
        renderAdvanced();
        const archive = screen.getByLabelText('Update site archive');
        fireEvent.change(archive, { target: { files: [new File(['site'], 'library.zip')] } });
        fireEvent.click(screen.getByRole('button', { name: 'Analyze repository' }));

        expect(await screen.findByRole('heading', { name: 'Library Update Site' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Recovery dashboard' })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('tab', { name: 'Recovery' }));
        expect(screen.getByText('MODEL PLUGIN')).toBeInTheDocument();
        expect(screen.getAllByText('Exact').length).toBeGreaterThan(0);
        expect(screen.getAllByText('Inferred').length).toBeGreaterThan(0);
        expect(screen.getByText('1 tools')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('tab', { name: 'Evidence' }));
        expect(screen.getByLabelText('Neutral path //Book/authors')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Import project and layers' })).toBeEnabled();
    });

    it('reanalyzes a lower-confidence candidate before enabling its import', async () => {
        vi.mocked(analyzeUpdateSite).mockResolvedValueOnce(analysis).mockResolvedValueOnce({ ...analysis, selectedCandidateId: 'inferred' });
        renderAdvanced();
        fireEvent.change(screen.getByLabelText('Update site archive'), { target: { files: [new File(['site'], 'library.zip')] } });
        fireEvent.click(screen.getByRole('button', { name: 'Analyze repository' }));
        await screen.findByRole('heading', { name: 'Library Update Site' });

        fireEvent.click(screen.getByRole('tab', { name: 'Recovery' }));
        const inferred = screen.getByRole('radio', { name: /Level 5 · CODE INFERENCE/i });
        fireEvent.click(inferred);
        expect(screen.getByRole('button', { name: 'Import project and layers' })).toBeDisabled();
        fireEvent.click(screen.getByRole('button', { name: 'Analyze selected candidate' }));

        await waitFor(() => expect(analyzeUpdateSite).toHaveBeenLastCalledWith(expect.any(Array), expect.objectContaining({ candidateId: 'inferred' })));
        await waitFor(() => expect(screen.getByRole('button', { name: 'Import project and layers' })).toBeEnabled());
    });

    it('materializes requested recovery layers and returns the persisted metamodel to the shell', async () => {
        vi.mocked(analyzeUpdateSite).mockResolvedValue(analysis);
        vi.mocked(materializeUpdateSiteLayers).mockResolvedValue({
            preview: { analysisId: 'p2-analysis', editorDefinition: {}, checkReport: { editorDefinitionId: 'rich-sirius-editor', diagnostics: [] },
                selectedPaletteGroups: 1, selectedTools: 1, selectedRepresentations: 1, selectedMappings: 1, selectedSemanticElements: 0, applicable: true, diagnostics: [] },
            metamodelId: 'library', metamodelEtag: 'm-etag', editorId: 'rich-sirius-editor', editorEtag: 'e-etag',
            persisted: true, rollbackAvailable: true, diagnostics: ['Canonical layer materialization: 6 validation rule(s), 3 quick fix(es), 1 rich editor(s).']
        });
        const onImported = vi.fn();
        renderAdvanced({ onImported });
        fireEvent.change(screen.getByLabelText('Update site archive'), { target: { files: [new File(['site'], 'library.zip')] } });
        fireEvent.click(screen.getByRole('button', { name: 'Analyze repository' }));
        await screen.findByRole('heading', { name: 'Library Update Site' });
        fireEvent.click(screen.getByRole('button', { name: 'Import project and layers' }));

        await waitFor(() => expect(materializeUpdateSiteLayers).toHaveBeenCalledWith(expect.any(Array),
            expect.objectContaining({ persist: true, candidateId: 'exact' })));
        expect(importUpdateSite).not.toHaveBeenCalled();
        expect(await screen.findByText(/Imported project/)).toHaveTextContent('library');
        expect(onImported).toHaveBeenCalledWith('library');
    });

    it('keeps an explicit semantic-only import path when all optional recovery layers are disabled', async () => {
        const imported: UpdateSiteImportResult = { analysisId: 'p2-analysis', metamodel: { id: 'library', etag: 'etag', preview: analysis.metamodelPreview! }, recoveryPackagePath: 'p2-analysis.recovery.json', recoveredLayers: analysis.recoveredLayers };
        vi.mocked(analyzeUpdateSite).mockResolvedValue(analysis);
        vi.mocked(importUpdateSite).mockResolvedValue(imported);
        const onImported = vi.fn();
        renderAdvanced({ onImported });
        for (const label of ['Recover palettes', 'Recover representations', 'Recover mappings']) {
            fireEvent.click(screen.getByRole('checkbox', { name: new RegExp(label, 'i') }));
        }
        fireEvent.change(screen.getByLabelText('Update site archive'), { target: { files: [new File(['site'], 'library.zip')] } });
        fireEvent.click(screen.getByRole('button', { name: 'Analyze repository' }));
        await screen.findByRole('heading', { name: 'Library Update Site' });
        fireEvent.click(screen.getByRole('button', { name: 'Import metamodel' }));

        await waitFor(() => expect(importUpdateSite).toHaveBeenCalled());
        expect(materializeUpdateSiteLayers).not.toHaveBeenCalled();
        expect(onImported).toHaveBeenCalledWith('library');
    });

    it('presents a compact dashboard, deep grammar logs and high-level explanations for unrecovered files', async () => {
        vi.mocked(analyzeUpdateSite).mockResolvedValue({ ...analysis,
            dashboard: { inspectedFiles: 24, recoveredFiles: 8, partialFiles: 3, supportingFiles: 10, unrecoverableFiles: 3, failedFiles: 0, recoveryCoverage: 39.5,
                confidence: [{ id: 'EXACT', label: 'Exact', value: 4, explanation: 'Original sources' }],
                fileStatus: [{ id: 'RECOVERED', label: 'Recovered', value: 8, explanation: 'Converted files' }],
                bundleRoles: [{ id: 'MODEL_PLUGIN', label: 'Model plugin', value: 1, explanation: 'Model bundle' }], stages: [],
                flowNodes: [{ id: 'source', label: 'Repository files', column: 0, explanation: 'Inputs' }, { id: 'classified', label: 'Classified artifacts', column: 1, explanation: 'Recognized' }, { id: 'recovered', label: 'Recovered', column: 2, explanation: 'Output' }],
                flowLinks: [{ source: 'source', target: 'classified', value: 20, explanation: 'Recognized' }, { source: 'classified', target: 'recovered', value: 8, explanation: 'Recovered' }] },
            logs: [{ id: 'grammar', stage: 'GRAMMAR_ANALYSIS', severity: 'WARNING', status: 'PARTIAL', title: 'Xtext grammar Library', message: 'Grammar inspected', sourcePath: 'library.xtext', artifactKind: 'XTEXT', purpose: 'Defines textual syntax.', recoveredAs: 'Grammar-derived mappings', nextAction: 'Review left recursion', confidence: 'DERIVED', details: ['12 parser rule(s)'] }],
            fileOutcomes: [{ sourcePath: 'icons/book.gif', artifactKind: 'OTHER', purpose: 'Icon used by generated UI.', status: 'NOT_RECOVERABLE', recoveredItems: 0, recoveredAs: '', explanation: 'Kept as an asset.', nextAction: 'Link it during materialization.', relatedElements: [] }],
            grammarInspections: [{ sourcePath: 'library.xtext', language: 'Xtext', grammarName: 'Library', parserRules: 12, terminalRules: 3, imports: 1, assignments: 8, crossReferences: 2, actions: 1, duplicateRules: [], possibleLeftRecursions: ['Expression'], inferredEntryRule: 'Model', entryInferenceMethod: 'XTEXT_FIRST_PARSER_RULE', entryInferenceConfidence: 0.98, recursiveRules: ['Expression'], automaticResolutionRules: ['Expression'], diagnostics: ['Direct left recursion'] }]
        });
        renderAdvanced();
        fireEvent.change(screen.getByLabelText('Update site archive'), { target: { files: [new File(['site'], 'library.zip')] } });
        fireEvent.click(screen.getByRole('button', { name: 'Analyze repository' }));
        expect(await screen.findByRole('heading', { name: 'Recovery dashboard' })).toBeInTheDocument();
        expect(screen.getByRole('img', { name: /file outcome distribution/i })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('tab', { name: 'Logs' }));
        expect(screen.getByText('Detailed import log')).toBeInTheDocument();
        expect(screen.getByText('Icon used by generated UI.')).toBeInTheDocument();
        expect(screen.getByText(/12 parser rules/i)).toBeInTheDocument();
        expect(screen.getByText(/Left-recursive rules/i)).toBeInTheDocument();
        expect(screen.getByText(/Expression/i)).toBeInTheDocument();
    });

    it('uses explicit reconciliation decisions and previews canonical layer materialization', async () => {
        const advanced = { ...analysis, reconciliation: { candidates: [{ candidateId: 'exact', confidence: 'EXACT' as const, elements: 4, uniqueElements: 1, conflictingElements: 1, coveragePercent: 100, recommendation: 'Use original Ecore' }],
            conflicts: [{ elementKey: 'CLASS:Book', elementKind: 'CLASS', displayName: 'Book', candidateSignatures: { exact: 'Book|title', inferred: 'Book|name' }, recommendedCandidateId: 'exact', explanation: 'Different features', blocking: true }],
            agreedElements: 3, unionElements: 4, agreementPercent: 75, recommendedCandidateId: 'exact', mergeReady: false, diagnostics: [] },
            semanticRecovery: { elements: [], representableCount: 0, partialCount: 0, unrepresentableCount: 0, diagnostics: [] } };
        vi.mocked(analyzeUpdateSite).mockResolvedValue(advanced);
        vi.mocked(reconcileUpdateSite).mockResolvedValue({ report: advanced.reconciliation, preview: analysis.metamodelPreview, applicable: true });
        vi.mocked(materializeUpdateSiteLayers).mockResolvedValue({ preview: { analysisId: 'p2-analysis', editorDefinition: {}, checkReport: { editorDefinitionId: 'editor', diagnostics: [] }, selectedPaletteGroups: 1, selectedTools: 1, selectedRepresentations: 1, selectedMappings: 1, selectedSemanticElements: 0, applicable: true, diagnostics: [] }, metamodelId: '', metamodelEtag: '', editorId: 'editor', editorEtag: '', persisted: false, rollbackAvailable: false, diagnostics: [] });
        renderAdvanced();
        fireEvent.change(screen.getByLabelText('Update site archive'), { target: { files: [new File(['site'], 'library.zip')] } });
        fireEvent.click(screen.getByRole('button', { name: 'Analyze repository' }));
        await screen.findByRole('heading', { name: 'Library Update Site' });
        fireEvent.click(screen.getByRole('tab', { name: 'Reconciliation' }));
        fireEvent.change(screen.getByLabelText('Evidence to keep'), { target: { value: 'inferred' } });
        fireEvent.click(screen.getByRole('button', { name: 'Build reconciled preview' }));
        await waitFor(() => expect(reconcileUpdateSite).toHaveBeenCalledWith(expect.any(Array), expect.objectContaining({ conflictCandidateIds: { 'CLASS:Book': 'inferred' } })));
        fireEvent.click(screen.getByRole('tab', { name: 'Materialization' }));
        fireEvent.click(screen.getByRole('button', { name: 'Preview materialization' }));
        expect(await screen.findByText('Materialization is valid')).toBeInTheDocument();
    });

    it('keeps live remote analysis read-only until a verified snapshot is available', async () => {
        vi.mocked(analyzeRemoteUpdateSite).mockResolvedValue({ analysis, fetchedResources: [],
            downloadedBytes: 0, cacheHits: 0, diagnostics: [] });
        renderAdvanced();
        openAdvancedTools();
        fireEvent.click(screen.getByRole('tab', { name: 'Remote p2' }));
        fireEvent.change(screen.getByLabelText('Repository URL'), { target: { value: 'https://updates.example.org/releases/' } });
        fireEvent.click(screen.getByRole('button', { name: 'Analyze live repository' }));
        await screen.findByText('Resources resolved');

        expect(screen.getByRole('button', { name: 'Import project and layers' })).toBeDisabled();
        fireEvent.click(screen.getByRole('tab', { name: 'Materialization' }));
        expect(screen.getByRole('heading', { name: 'Verified snapshot required' })).toBeInTheDocument();
    });

    it('imports and materializes from a verified remote snapshot', async () => {
        const snapshot = { lock: { schemaVersion: '1.0', snapshotId: 'p2-1234567890abcdef12345678',
            rootUrl: 'https://updates.example.org/releases/', treeSha256: 'a'.repeat(64), capturedAt: '2026-08-04T12:00:00Z',
            totalBytes: 4, resources: [], licenses: [] }, analysis, reused: false, diagnostics: [] };
        vi.mocked(createRemoteUpdateSiteSnapshot).mockResolvedValue(snapshot);
        vi.mocked(materializeRemoteSnapshot).mockResolvedValue({ preview: { analysisId: 'p2-analysis', editorDefinition: {},
            checkReport: { editorDefinitionId: 'editor', diagnostics: [] }, applicable: true, selectedPaletteGroups: 1,
            selectedTools: 1, selectedRepresentations: 1, selectedMappings: 1, selectedSemanticElements: 0, diagnostics: [] },
            persisted: true, metamodelId: 'library',
            metamodelEtag: 'etag', editorId: 'editor', editorEtag: 'editor-etag', rollbackAvailable: true, diagnostics: [] });
        renderAdvanced();
        openAdvancedTools();
        fireEvent.click(screen.getByRole('tab', { name: 'Remote p2' }));
        fireEvent.change(screen.getByLabelText('Repository URL'), { target: { value: 'https://updates.example.org/releases/' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create offline snapshot' }));
        await screen.findByText('Locked resources');

        expect(screen.getByRole('button', { name: 'Import project and layers' })).toBeEnabled();
        fireEvent.click(screen.getByRole('button', { name: 'Import project and layers' }));
        await waitFor(() => expect(materializeRemoteSnapshot).toHaveBeenCalledWith(snapshot.lock.snapshotId,
            expect.objectContaining({ candidateId: 'exact', persist: true })));
        expect(importRemoteSnapshotRecovery).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('tab', { name: 'Materialization' }));
        fireEvent.click(screen.getByRole('button', { name: 'Preview materialization' }));
        await waitFor(() => expect(materializeRemoteSnapshot).toHaveBeenCalledWith(snapshot.lock.snapshotId,
            expect.objectContaining({ persist: false })));
    });

});
