import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react';
import { openFileInputPicker, HydraActionableErrorNotice, HydraButton, HydraCheckbox, HydraCreationModeSwitch, HydraFormField, HydraGuidedWorkflow, HydraOperationProgress, HydraTooltip } from '../design-system';
import { analyzeRemoteUpdateSiteSnapshot, analyzeUpdateSite, analyzeUpdateSiteRepository, analyzeUpdateSiteRepositorySnapshot, importRemoteSnapshotRecovery, importUpdateSite, importUpdateSiteRepositorySnapshot,
    loadUpdateSiteExtensionAdapters, materializeRemoteSnapshot, materializeUpdateSiteLayers, materializeUpdateSiteRepositorySnapshot,
    reconcileRemoteSnapshot, reconcileUpdateSite, reconcileUpdateSiteRepositorySnapshot, rollbackUpdateSiteMaterialization } from './api';
import { UpdateSiteImportLogPanel } from './UpdateSiteImportLogPanel';
import { UpdateSiteEvidencePanel } from './UpdateSiteEvidencePanel';
import { recommendedLayerSelection } from './updateSiteLayerSelection';
import { UpdateSiteRemoteRepositoryPanel } from './UpdateSiteRemoteRepositoryPanel';
import { UpdateSiteSourceStep } from './UpdateSiteSourceStep';
import { UpdateSiteLayerMergePanel } from './UpdateSiteLayerMergePanel';
import { UpdateSiteDeclarativeAdapterAuthoringPanel } from './UpdateSiteDeclarativeAdapterAuthoringPanel';
import { UpdateSitePerformancePanel } from './UpdateSitePerformancePanel';
import { AnalysisRequired, EmptyAnalysis, MaterializationPanel, Overview, ReconciliationPanel, RecoveryPanel, ReleasePanel, RemoteReadOnlyNotice, SectionNavigation, SemanticPanel, ControlSection } from './UpdateSiteImportCenterPanels';
import { EMPTY_RECONCILIATION, EMPTY_RELEASES, EMPTY_SEMANTIC, formatBytes, type UpdateSiteTab } from './UpdateSiteImportCenterModel';
import { UpdateSiteReleaseGovernancePanel } from './UpdateSiteReleaseGovernancePanel';
import type { UpdateSiteImportAnalysis, UpdateSiteImportOptions, UpdateSiteImportResult, UpdateSiteExtensionAdapterDescriptor, UpdateSiteLayerMaterializationResult, UpdateSiteLayerSelection, UpdateSiteReconciliationReport, UpdateSiteRepositorySourceSummary } from './types';
import { useHydraLanguage } from '../i18n';
import './update-site-import-center.css';
const UpdateSiteDistributionGovernancePanel = lazy(() => import('./UpdateSiteDistributionGovernancePanel')
    .then(module => ({ default: module.UpdateSiteDistributionGovernancePanel })));
interface Props { onImported?: (metamodelId: string) => void; }
type AnalysisSource = 'LOCAL' | 'REMOTE_LIVE' | 'REMOTE_SNAPSHOT' | 'REPOSITORY_SNAPSHOT' | '';
const DEFAULT_OPTIONS: UpdateSiteImportOptions = {
    targetId: '', overwrite: false, strict: false, candidateId: '', recoverPalettes: true,
    recoverRepresentations: true, recoverMappings: true, persistRecoveryPackage: true
};
const EMPTY_LAYER_SELECTION: UpdateSiteLayerSelection = { paletteGroupIds: [], paletteToolIds: [], representationIds: [], mappingIds: [], semanticElementIds: [], explicit: true };
export function UpdateSiteImportCenter({ onImported }: Props) {
    const { t } = useHydraLanguage();
    const archiveInputRef = useRef<HTMLInputElement>(null);
    const directoryInputRef = useRef<HTMLInputElement>(null);
    const [files, setFiles] = useState<File[]>([]);
    const [options, setOptions] = useState<UpdateSiteImportOptions>(DEFAULT_OPTIONS);
    const [analysis, setAnalysis] = useState<UpdateSiteImportAnalysis | null>(null);
    const [analysisSource, setAnalysisSource] = useState<AnalysisSource>('');
    const [remoteSnapshotId, setRemoteSnapshotId] = useState('');
    const [repositoryUrl, setRepositoryUrl] = useState('');
    const [repositoryReference, setRepositoryReference] = useState('');
    const [repositorySubdirectory, setRepositorySubdirectory] = useState('');
    const [repositoryProfileId, setRepositoryProfileId] = useState('');
    const [repositorySummary, setRepositorySummary] = useState<UpdateSiteRepositorySourceSummary | null>(null);
    const [result, setResult] = useState<UpdateSiteImportResult | null>(null);
    const [busy, setBusy] = useState<'ANALYZE' | 'IMPORT' | 'RECONCILE' | 'MATERIALIZE' | 'ROLLBACK' | ''>('');
    const [operationStage, setOperationStage] = useState<'prepare' | 'execute' | 'finalize'>('prepare');
    const [error, setError] = useState('');
    const [tab, setTab] = useState<UpdateSiteTab>('OVERVIEW');
    const [conflictDecisions, setConflictDecisions] = useState<Record<string, string>>({});
    const [reconciledName, setReconciledName] = useState('');
    const [editorId, setEditorId] = useState('');
    const [editorName, setEditorName] = useState('');
    const [materialization, setMaterialization] = useState<UpdateSiteLayerMaterializationResult | null>(null);
    const [layerSelection, setLayerSelection] = useState<UpdateSiteLayerSelection>(EMPTY_LAYER_SELECTION);
    const [extensionAdapters, setExtensionAdapters] = useState<UpdateSiteExtensionAdapterDescriptor[]>([]);
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('GUIDED');
    const [guidedStep, setGuidedStep] = useState('source');
    const selectedCandidate = useMemo(() => analysis?.candidates.find(item => item.id === options.candidateId)
        ?? analysis?.candidates.find(item => item.id === analysis.selectedCandidateId), [analysis, options.candidateId]);
    const previewMatchesSelection = analysis !== null && selectedCandidate !== undefined
        && selectedCandidate.id === analysis.selectedCandidateId;
    const reconciliation = analysis?.reconciliation ?? EMPTY_RECONCILIATION;
    function selectFiles(next: FileList | null): void {
        const selected = Array.from(next ?? []);
        setFiles(selected); setAnalysis(null); setAnalysisSource(''); setRemoteSnapshotId(''); setRepositorySummary(null); setResult(null); setMaterialization(null); setLayerSelection(EMPTY_LAYER_SELECTION); setError(''); setTab('OVERVIEW');
        setOptions(current => ({ ...current, candidateId: '' }));
    }
    function clearSelectedSource(): void { selectFiles(null); }
    function updateBoolean(key: keyof Pick<UpdateSiteImportOptions, 'overwrite' | 'strict' | 'recoverPalettes'
            | 'recoverRepresentations' | 'recoverMappings' | 'persistRecoveryPackage'>, value: boolean): void {
        setOptions(current => ({ ...current, [key]: value }));
    }
    async function analyze(candidateId = options.candidateId): Promise<void> {
        if (files.length === 0) { setError('Choose an update-site archive or directory first.'); return; }
        setBusy('ANALYZE'); setOperationStage('prepare'); setResult(null); setMaterialization(null);
        try {
            setOperationStage('execute');
            const next = await analyzeUpdateSite(files, { ...options, candidateId });
            setOperationStage('finalize');
            setAnalysis(next); setAnalysisSource('LOCAL'); setRemoteSnapshotId(''); setOptions(current => ({ ...current, candidateId: next.selectedCandidateId }));
            setLayerSelection(recommendedLayerSelection(next));
            setConflictDecisions(defaultConflictDecisions(next.reconciliation ?? EMPTY_RECONCILIATION)); setError('');
        } catch (exception) { setError(message(exception, 'Could not analyze the update site')); }
        finally { setBusy(''); }
    }
    async function analyzeRepositorySource(): Promise<void> {
        if (repositoryUrl.trim() === '') { setError('Enter a Git, GitHub or GitLab repository URL first.'); return; }
        setBusy('ANALYZE'); setOperationStage('prepare'); setResult(null); setMaterialization(null);
        try {
            setOperationStage('execute');
            const result = await analyzeUpdateSiteRepository({ repositoryUrl: repositoryUrl.trim(), reference: repositoryReference.trim(),
                subdirectory: repositorySubdirectory.trim(), profileId: repositoryProfileId.trim(), options });
            setOperationStage('finalize');
            const next = result.analysis;
            setAnalysis(next); setAnalysisSource('REPOSITORY_SNAPSHOT'); setRemoteSnapshotId(result.source.snapshotId); setRepositorySummary(result.source);
            setOptions(current => ({ ...current, candidateId: next.selectedCandidateId }));
            setLayerSelection(recommendedLayerSelection(next));
            setConflictDecisions(defaultConflictDecisions(next.reconciliation ?? EMPTY_RECONCILIATION)); setError('');
        } catch (exception) { setError(message(exception, 'Could not analyze the repository source')); }
        finally { setBusy(''); }
    }

    async function importSelected(): Promise<boolean> {
        if (options.candidateId === '' || analysisSource === 'REMOTE_LIVE' || analysisSource === '') return false;
        if (analysisSource === 'LOCAL' && files.length === 0) return false;
        if (['REMOTE_SNAPSHOT', 'REPOSITORY_SNAPSHOT'].includes(analysisSource) && remoteSnapshotId === '') return false;
        // The primary project-import action must not silently stop at the metamodel when recovery layers were requested.
        // Materialization persists the metamodel and then delegates visual/validation/quick-fix recovery to the shared deep pipeline.
        if (options.recoverPalettes || options.recoverRepresentations || options.recoverMappings) {
            return materialize(true);
        }
        setBusy('IMPORT'); setOperationStage('prepare');
        try {
            setOperationStage('execute');
            const imported = analysisSource === 'LOCAL' ? await importUpdateSite(files, options)
                : analysisSource === 'REPOSITORY_SNAPSHOT' ? await importUpdateSiteRepositorySnapshot(remoteSnapshotId, options)
                : await importRemoteSnapshotRecovery(remoteSnapshotId, options);
            setOperationStage('finalize');
            setResult(imported); setError(''); onImported?.(imported.metamodel.id); return true;
        } catch (exception) { setError(message(exception, 'Could not import the recovered metamodel')); return false; }
        finally { setBusy(''); }
    }
    async function reconcile(): Promise<void> {
        if (analysisSource === 'REMOTE_LIVE' || analysisSource === '') { setError('Create or import a verified snapshot before reconciling a remote repository.'); return; }
        const request = { targetId: reconciledName || options.targetId,
            primaryCandidateId: reconciliation.recommendedCandidateId || options.candidateId,
            conflictCandidateIds: conflictDecisions };
        setBusy('RECONCILE'); setOperationStage('prepare');
        try {
            setOperationStage('execute');
            const reconciled = analysisSource === 'LOCAL' ? await reconcileUpdateSite(files, request)
                : analysisSource === 'REPOSITORY_SNAPSHOT' ? await reconcileUpdateSiteRepositorySnapshot(remoteSnapshotId, request)
                : await reconcileRemoteSnapshot(remoteSnapshotId, request);
            setOperationStage('finalize');
            setError('');
            setAnalysis(current => current === null ? current : { ...current, metamodelPreview: reconciled.preview,
                selectedCandidateId: reconciliation.recommendedCandidateId || current.selectedCandidateId,
                importable: reconciled.applicable });
        } catch (exception) { setError(message(exception, 'Could not reconcile the recovery candidates')); }
        finally { setBusy(''); }
    }
    async function materialize(persist: boolean): Promise<boolean> {
        if (analysis === null) return false;
        if (analysisSource === 'REMOTE_LIVE' || analysisSource === '') { setError('Create or import a verified snapshot before materializing a remote repository.'); return false; }
        const request = { targetId: options.targetId, candidateId: options.candidateId, editorId, editorName,
            strict: options.strict, persist, selection: layerSelection };
        setBusy('MATERIALIZE'); setOperationStage('prepare');
        try {
            setOperationStage('execute');
            const next = analysisSource === 'LOCAL' ? await materializeUpdateSiteLayers(files, request)
                : analysisSource === 'REPOSITORY_SNAPSHOT' ? await materializeUpdateSiteRepositorySnapshot(remoteSnapshotId, request)
                : await materializeRemoteSnapshot(remoteSnapshotId, request);
            setOperationStage('finalize');
            setMaterialization(next); setError('');
            if (persist && next.persisted && next.metamodelId !== '') onImported?.(next.metamodelId);
            return true;
        } catch (exception) { setError(message(exception, 'Could not materialize the recovered layers')); return false; }
        finally { setBusy(''); }
    }
    async function reanalyze(candidateId = options.candidateId): Promise<void> {
        if (analysisSource === 'LOCAL') { await analyze(candidateId); return; }
        if (!['REMOTE_SNAPSHOT', 'REPOSITORY_SNAPSHOT'].includes(analysisSource) || remoteSnapshotId === '') {
            setError('Create or import a verified snapshot before reanalyzing a remote candidate.'); return;
        }
        setBusy('ANALYZE'); setOperationStage('prepare'); setResult(null); setMaterialization(null);
        try {
            setOperationStage('execute');
            const next = analysisSource === 'REPOSITORY_SNAPSHOT'
                ? await analyzeUpdateSiteRepositorySnapshot(remoteSnapshotId, { ...options, candidateId })
                : await analyzeRemoteUpdateSiteSnapshot({ snapshotId: remoteSnapshotId, options: { ...options, candidateId } });
            setOperationStage('finalize');
            setAnalysis(next); setOptions(current => ({ ...current, candidateId: next.selectedCandidateId }));
            setLayerSelection(recommendedLayerSelection(next));
            setConflictDecisions(defaultConflictDecisions(next.reconciliation ?? EMPTY_RECONCILIATION)); setError('');
        } catch (exception) { setError(message(exception, 'Could not reanalyze the stored snapshot')); }
        finally { setBusy(''); }
    }
    async function rollback(): Promise<void> {
        if (materialization === null || !materialization.rollbackAvailable) return;
        setBusy('ROLLBACK'); setOperationStage('prepare');
        try {
            setOperationStage('execute');
            const rolledBack = await rollbackUpdateSiteMaterialization({ metamodelId: materialization.metamodelId,
                metamodelEtag: materialization.metamodelEtag, editorId: materialization.editorId, editorEtag: materialization.editorEtag });
            setOperationStage('finalize');
            setError(rolledBack.diagnostics.join('; ')); setMaterialization(null);
        } catch (exception) { setError(message(exception, 'Could not roll back the materialization')); }
        finally { setBusy(''); }
    }
    useEffect(() => {
        let active = true;
        void loadUpdateSiteExtensionAdapters().then(adapters => {
            if (active) setExtensionAdapters(adapters);
        }).catch(() => {
            if (active) setExtensionAdapters([]);
        });
        return () => { active = false; };
    }, []);
    const sourceReady = files.length > 0 || analysisSource === 'REMOTE_LIVE' || analysisSource === 'REMOTE_SNAPSHOT' || analysisSource === 'REPOSITORY_SNAPSHOT' || repositoryUrl.trim() !== '';
    const analysisReady = analysis !== null && analysis.candidates.length > 0 && options.candidateId !== '';
    const selectionReady = analysisReady && previewMatchesSelection;
    const importReady = selectionReady && analysis?.importable === true && ['LOCAL', 'REMOTE_SNAPSHOT', 'REPOSITORY_SNAPSHOT'].includes(analysisSource);
    const layerCount = Number(options.recoverPalettes) + Number(options.recoverRepresentations) + Number(options.recoverMappings);
    const guidedSteps = [
        {
            id: 'source', title: 'Repository source', description: 'Choose a local update site, a Git/GitHub/GitLab repository, or an allowlisted remote p2 repository.',
            checks: [{ id: 'source-chosen', label: 'A repository source is available', complete: sourceReady, detail: files.length > 0 ? `${files.length} local file(s)` : analysisSource === '' ? 'Choose an archive, directory or Remote p2' : analysisSource.replaceAll('_', ' ') }],
            content: <UpdateSiteSourceStep files={files} analysisSource={analysisSource} repositoryUrl={repositoryUrl}
                repositoryReference={repositoryReference} repositorySubdirectory={repositorySubdirectory} repositoryProfileId={repositoryProfileId}
                repositorySummary={repositorySummary} busy={busy === 'ANALYZE'} onFilesSelected={selectFiles} onClearFiles={clearSelectedSource}
                onRepositoryUrlChange={setRepositoryUrl} onRepositoryReferenceChange={setRepositoryReference}
                onRepositorySubdirectoryChange={setRepositorySubdirectory} onRepositoryProfileIdChange={setRepositoryProfileId}
                onAnalyzeRepository={() => void analyzeRepositorySource()} onOpenRemote={() => setTab('REMOTE')} />
        },
        {
            id: 'recovery-plan', title: 'Recovery plan', description: 'Choose the destination, neutral layers and safety policy before analysis.',
            checks: [
                { id: 'layers-selected', label: 'Recovery layer decision recorded', complete: true, detail: layerCount === 0 ? 'Semantic metamodel only' : `${layerCount} optional visual layer type(s) · validations and quick fixes auto-recovered` },
                { id: 'traceability', label: 'Traceability decision recorded', complete: true, detail: options.persistRecoveryPackage ? 'Recovery package will be persisted' : 'Recovery package persistence disabled explicitly' }
            ],
            content: <section className="update-site-guided-recovery">
                <HydraFormField label="Target metamodel ID" targetId="guided-update-site-target-id" help="Leave empty to keep the recovered package identifier. Provenance remains tied to the original bytes."><input id="guided-update-site-target-id" value={options.targetId} onChange={event => setOptions(current => ({ ...current, targetId: event.target.value }))} placeholder="Recovered identifier when empty" /></HydraFormField>
                <div className="update-site-guided-option-grid">
                    <section><header><h4>Neutral layers</h4><HydraTooltip label="About recovery layers" title="Recovery layers"><p>Palettes recover creation tools, representations recover diagram/view definitions, and mappings connect semantic elements to visual or grammar elements. Disable any layer you do not trust; the neutral metamodel can still be recovered independently.</p></HydraTooltip></header><HydraCheckbox label="Recover palettes" checked={options.recoverPalettes} description="GMFTool, Sirius and grammar-derived tools." onChange={event => updateBoolean('recoverPalettes', event.target.checked)} /><HydraCheckbox label="Recover representations" checked={options.recoverRepresentations} description="GMF diagrams, Sirius viewpoints and textual representations." onChange={event => updateBoolean('recoverRepresentations', event.target.checked)} /><HydraCheckbox label="Recover mappings" checked={options.recoverMappings} description="Semantic-to-visual and semantic-to-grammar mappings." onChange={event => updateBoolean('recoverMappings', event.target.checked)} /></section>
                    <section><header><h4>Safety</h4><HydraTooltip label="About recovery safety" title="Recovery safety"><p>Strict mode accepts only exact recovery. Overwrite permits replacing a document with the same stable ID. Persist recovery package stores diagnostics, provenance and drafts for later auditing.</p></HydraTooltip></header><HydraCheckbox label="Strict exact recovery" checked={options.strict} description="Reject lower-confidence reconstruction." onChange={event => updateBoolean('strict', event.target.checked)} /><HydraCheckbox label="Overwrite existing metamodel" checked={options.overwrite} description="Replace the same stable document only after validation." onChange={event => updateBoolean('overwrite', event.target.checked)} /><HydraCheckbox label="Persist recovery package" checked={options.persistRecoveryPackage} description="Keep logs, provenance and layer drafts." onChange={event => updateBoolean('persistRecoveryPackage', event.target.checked)} /></section>
                </div>
            </section>
        },
        {
            id: 'analyze', title: 'Analyze safely', description: 'Inspect metadata, bundles and artifacts without executing recovered plug-ins.',
            checks: [
                { id: 'analysis-complete', label: 'Repository analysis completed', complete: analysisReady, detail: analysis === null ? 'Run analysis' : `${analysis.repository.repositoryFileCount} files · ${analysis.candidates.length} candidate(s)` },
                { id: 'candidate-preview', label: 'Selected candidate matches the analyzed preview', complete: selectionReady, detail: selectionReady ? options.candidateId : 'Reanalyze after changing candidate' }
            ],
            content: <section className="update-site-guided-analysis"><div className="update-site-control-readiness"><strong>{analysis === null ? 'Ready to analyze' : previewMatchesSelection ? 'Selection analyzed' : 'Candidate requires reanalysis'}</strong><span>{analysis === null ? 'HYDRA will inspect metadata and files read-only.' : `${analysis.repository.repositoryFileCount} files inspected · ${analysis.candidates.length} recovery candidate(s).`}</span></div><div className="update-site-setup-actions"><HydraButton loading={busy === 'ANALYZE'} disabled={files.length === 0 && !['REMOTE_SNAPSHOT', 'REPOSITORY_SNAPSHOT'].includes(analysisSource)} onClick={() => void (['REMOTE_SNAPSHOT', 'REPOSITORY_SNAPSHOT'].includes(analysisSource) ? reanalyze() : analyze())}>{analysis === null ? 'Analyze repository' : 'Reanalyze selection'}</HydraButton><HydraButton variant="ghost" disabled={analysis === null} onClick={() => setTab('OVERVIEW')}>Review results</HydraButton></div></section>
        },
        {
            id: 'review', title: 'Review evidence', description: 'Check confidence, diagnostics, recovery layers and structural differences before importing.',
            checks: [
                { id: 'candidate-selected', label: 'Recovery candidate selected', complete: options.candidateId !== '', detail: selectedCandidate?.id ?? (options.candidateId || 'No candidate') },
                { id: 'preview-importable', label: 'Candidate is importable', complete: analysis?.importable === true, detail: analysis?.importable === true ? 'No blocking recovery diagnostic' : 'Review Logs, Evidence or Reconciliation' }
            ],
            content: <section className="update-site-guided-review"><div className="update-site-guided-review-actions"><HydraButton size="small" onClick={() => setTab('OVERVIEW')}>Overview</HydraButton><HydraButton size="small" onClick={() => setTab('RECOVERY')}>Recovery</HydraButton><HydraButton size="small" onClick={() => setTab('EVIDENCE')}>Evidence</HydraButton><HydraButton size="small" onClick={() => setTab('LOGS')}>Diagnostics</HydraButton><HydraButton size="small" onClick={() => setTab('RECONCILIATION')}>Reconciliation</HydraButton></div><p>The detailed workspace below remains fully available. The guided assistant only controls progression; it does not replace reconciliation, materialization, merge, semantic recovery or adapter tools.</p></section>
        },
        {
            id: 'import', title: 'Import recovered project', description: 'Persist only the analyzed candidate after all required checks are green.',
            checks: [
                { id: 'verified-bytes', label: 'Source can be persisted from verified bytes', complete: ['LOCAL', 'REMOTE_SNAPSHOT', 'REPOSITORY_SNAPSHOT'].includes(analysisSource), detail: analysisSource === 'REMOTE_LIVE' ? 'Create a verified snapshot first' : analysisSource || 'No analyzed source' },
                { id: 'ready-import', label: 'Selected candidate is ready to import', complete: importReady, detail: importReady ? 'Import enabled' : 'Resolve red checks first' }
            ],
            content: <section className="update-site-guided-import"><strong>{importReady ? (layerCount === 0 ? 'Ready to create the HYDRA metamodel' : 'Ready to import the HYDRA project and recovered layers') : 'Import is still protected'}</strong><p>The final action uses exactly the reviewed source bytes. Safe validation and quick-fix evidence is materialized together with the selected visual layers.</p></section>
        }
    ];
    return <section className="update-site-import-center">
        <HydraOperationProgress
            active={busy !== ''}
            compact
            title={busy === 'MATERIALIZE' ? 'Recovering and saving project layers' : busy === 'RECONCILE' ? 'Reconciling recovery evidence' : busy === 'IMPORT' ? 'Importing recovered project' : busy === 'ROLLBACK' ? 'Rolling back recovered layers' : 'Analyzing update site'}
            detail={operationStage === 'prepare'
                ? 'Preparing the verified source and the requested recovery options…'
                : operationStage === 'execute'
                    ? busy === 'MATERIALIZE' ? 'Recovering the selected editor, validation, quick-fix and visual layers in dependency-safe order…'
                        : busy === 'IMPORT' ? 'Persisting the verified metamodel and recovery package…'
                            : busy === 'RECONCILE' ? 'Resolving the selected recovery evidence…'
                                : busy === 'ROLLBACK' ? 'Restoring the previous saved layer state…'
                                    : 'Inspecting repository metadata, bundles and recoverable project definitions…'
                    : 'Applying the returned result and confirming the completed operation…'}
            currentStageId={operationStage}
            completed={operationStage === 'finalize'}
            stages={[{ id: 'prepare', label: 'Prepare verified source' }, { id: 'execute', label: busy === 'ANALYZE' ? 'Analyze recoverable content' : busy === 'RECONCILE' ? 'Reconcile evidence' : busy === 'ROLLBACK' ? 'Restore saved state' : 'Process requested layers' }, { id: 'finalize', label: 'Confirm result' }]}
        />
        {error !== '' && <HydraActionableErrorNotice className="error-message" message={error} context="update-site import" />}
        {result !== null && <div className="status-message">Imported <strong>{result.metamodel.id}</strong>.
            Recovery package: <code>{result.recoveryPackagePath || 'not persisted'}</code></div>}
        {materialization?.persisted && <div className="status-message">{t('Imported project')} <strong>{materialization.metamodelId}</strong> {t('with recovered editor, validation and quick-fix layers.')}
            {materialization.diagnostics.length > 0 && <small>{materialization.diagnostics.at(-1)}</small>}</div>}
        <div className="update-site-import-layout">
            <section className="update-site-setup-card" aria-labelledby="update-site-setup-title">
                <header className="update-site-setup-header">
                    <div>
                        <p className="update-site-eyebrow">Repository setup</p>
                        <h3 id="update-site-setup-title">Choose, analyze and import</h3>
                        <span>Complete the three compact steps. Open recovery options only when the defaults are not suitable.</span>
                    </div>
                    <HydraTooltip label="About repository setup" title="Repository setup">
                        <p>HYDRA inspects metadata and files without executing plug-in code. Analysis is always required before import. Review the recovered candidate, confidence and missing evidence before enabling import; changing the selected candidate requires a new analysis.</p>
                    </HydraTooltip>
                </header>
                <div className="update-site-mode-row"><HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} guidedLabel="Guided recovery" advancedLabel="Advanced workspace" /><span>The advanced recovery, reconciliation, materialization and merge tools remain unchanged.</span></div>
                {creationMode === 'GUIDED' ? <HydraGuidedWorkflow title="Recover an Eclipse update site step by step" description="HYDRA verifies each stage before the next one is unlocked. Pending, ready, incomplete and completed states are shown explicitly." steps={guidedSteps} currentStepId={guidedStep} onStepChange={setGuidedStep} onFinish={importSelected} finishLabel={busy === 'IMPORT' || busy === 'MATERIALIZE' ? 'Importing…' : 'Finish import'} className="update-site-guided-workflow" /> : <>
                <div className="update-site-setup-grid">
                    <ControlSection title="1. Source"
                        help="Choose either a compressed update site or an unpacked directory. HYDRA preserves relative paths and never executes plug-in code.">
                        <HydraFormField label="Update site source" targetId="update-site-archive" required
                            help="ZIP, JAR and unpacked p2 repositories are accepted. content.xml/content.jar and artifacts.xml/artifacts.jar improve exact discovery.">
                            <input ref={archiveInputRef} className="update-site-hidden-file-input"
                                id="update-site-archive" aria-label="Update site archive" type="file"
                                accept=".zip,.jar,.xml,.p2,.updatesite"
                                onChange={event => selectFiles(event.target.files)} />
                            <input ref={directoryInputRef} className="update-site-hidden-file-input"
                                aria-label="Update site directory" type="file" multiple
                                {...({ webkitdirectory: '', directory: '' } as Record<string, string>)}
                                onChange={event => selectFiles(event.target.files)} />
                            <div className="update-site-source-pickers">
                                <HydraButton size="small" onClick={() => openFileInputPicker(archiveInputRef.current)}>Choose archive</HydraButton>
                                <HydraButton size="small" onClick={() => openFileInputPicker(directoryInputRef.current)}>Choose directory</HydraButton>
                            </div>
                        </HydraFormField>
                        <div className={`update-site-file-summary${files.length > 0 ? ' selected' : ''}`}>
                            <div>
                                <strong>{files.length === 0 ? 'No source selected' : `${files.length} file(s) selected`}</strong>
                                <small>{files.length === 0 ? 'Select one local source or use Remote p2.'
                                    : `${formatBytes(files.reduce((total, file) => total + file.size, 0))} uploaded · ${files[0].webkitRelativePath || files[0].name}`}</small>
                            </div>
                            {files.length > 0 && <HydraButton size="small" variant="ghost" onClick={clearSelectedSource}>Clear</HydraButton>}
                        </div>
                    </ControlSection>
                    <ControlSection title="2. Destination"
                        help="The target ID is the stable neutral identifier used by HYDRA. Leave it empty to use the recovered package identifier.">
                        <HydraFormField label="Target metamodel ID" targetId="update-site-target-id"
                            help="Changing this value does not change source provenance; it only chooses the saved HYDRA document ID.">
                            <input id="update-site-target-id" value={options.targetId}
                                onChange={event => setOptions(current => ({ ...current, targetId: event.target.value }))}
                                placeholder="Recovered identifier when empty" />
                        </HydraFormField>
                        <p className="update-site-destination-note">The original fingerprint, evidence and source paths remain unchanged.</p>
                    </ControlSection>
                    <section className="update-site-execution-card" aria-label="Analysis and import actions">
                        <header>
                            <div><span>3</span><h3>Analyze and import</h3></div>
                            <HydraTooltip label="About analysis and import" title="Analyze and import">
                                <p>Analysis creates a read-only preview. Import remains disabled until the selected candidate and preview match. If you change the source, candidate or recovery options, analyze again so the import is based on the same evidence you reviewed.</p>
                            </HydraTooltip>
                        </header>
                        <div className="update-site-control-readiness">
                            <strong>{analysis === null ? 'Ready to analyze' : previewMatchesSelection ? 'Selection analyzed' : 'Candidate requires reanalysis'}</strong>
                            <span>{analysis === null
                                ? 'Select a source and start the staged inspection.'
                                : `${analysis.repository.repositoryFileCount} files inspected · ${analysis.candidates.length} recovery candidate(s).`}</span>
                        </div>
                        <div className="update-site-setup-actions">
                            <HydraButton loading={busy === 'ANALYZE'} disabled={files.length === 0}
                                onClick={() => void analyze()}>Analyze repository</HydraButton>
                            <HydraButton variant="primary" loading={busy === 'IMPORT' || busy === 'MATERIALIZE'}
                                disabled={!['LOCAL', 'REMOTE_SNAPSHOT', 'REPOSITORY_SNAPSHOT'].includes(analysisSource) || !analysis?.importable || !previewMatchesSelection}
                                onClick={() => void importSelected()}>{layerCount === 0 ? 'Import metamodel' : 'Import project and layers'}</HydraButton>
                        </div>
                    </section>
                </div>
                <details className="update-site-advanced-options">
                    <summary>
                        <span><strong>Recovery options</strong><small>Layers, confidence and traceability</small></span>
                        <span aria-hidden="true">⌄</span>
                    </summary>
                    <div className="update-site-advanced-options-content">
                        <section>
                            <header><h4>Neutral layers</h4><HydraTooltip label="About neutral layers" title="Neutral layers"><p>Choose the visual layers to materialize. Safe validation and quick-fix sources are recovered automatically with a project import; disable all three visual options only when you explicitly want a semantic-metamodel-only import.</p></HydraTooltip></header>
                            <div className="update-site-layer-options" aria-label="Recovered layers">
                                <HydraCheckbox label="Recover palettes" checked={options.recoverPalettes}
                                    description="GMFTool groups, Sirius tools and grammar-derived creation tools."
                                    onChange={event => updateBoolean('recoverPalettes', event.target.checked)} />
                                <HydraCheckbox label="Recover representations" checked={options.recoverRepresentations}
                                    description="GMF diagrams, Sirius viewpoints and textual representations."
                                    onChange={event => updateBoolean('recoverRepresentations', event.target.checked)} />
                                <HydraCheckbox label="Recover mappings" checked={options.recoverMappings}
                                    description="Links between semantic elements, visual elements and grammar rules."
                                    onChange={event => updateBoolean('recoverMappings', event.target.checked)} />
                            </div>
                        </section>
                        <section>
                            <header><h4>Safety and traceability</h4><HydraTooltip label="About safety and traceability" title="Safety and traceability"><p>Strict mode protects against lower-confidence reconstruction. The recovery package preserves logs, provenance and layer drafts for later review.</p></HydraTooltip></header>
                            <div className="update-site-layer-options">
                                <HydraCheckbox label="Strict exact recovery" checked={options.strict}
                                    description="Allow import only when the selected source is classified as exact."
                                    onChange={event => updateBoolean('strict', event.target.checked)} />
                                <HydraCheckbox label="Overwrite existing metamodel" checked={options.overwrite}
                                    description="Replace a document with the same stable ID only after validation."
                                    onChange={event => updateBoolean('overwrite', event.target.checked)} />
                                <HydraCheckbox label="Persist recovery package" checked={options.persistRecoveryPackage}
                                    description="Store provenance, logs, diagnostics and recovered layer drafts."
                                    onChange={event => updateBoolean('persistRecoveryPackage', event.target.checked)} />
                            </div>
                        </section>
                    </div>
                </details>
                </>}
            </section>
            <div className="update-site-import-results">
                <SectionNavigation selected={tab} onSelect={setTab} />
                <div className="update-site-section-content">
                    {tab === 'OVERVIEW' && (analysis === null ? <EmptyAnalysis /> : <Overview analysis={analysis} />)}
                    {tab === 'REMOTE' && <UpdateSiteRemoteRepositoryPanel options={options} onAnalysis={(next, snapshotId) => {
                        setAnalysis(next); setAnalysisSource(snapshotId === null ? 'REMOTE_LIVE' : 'REMOTE_SNAPSHOT');
                        setRemoteSnapshotId(snapshotId ?? ''); setOptions(current => ({ ...current, candidateId: next.selectedCandidateId }));
                        setLayerSelection(recommendedLayerSelection(next));
                        setConflictDecisions(defaultConflictDecisions(next.reconciliation ?? EMPTY_RECONCILIATION));
                        setError('');
                    }} />}
                    {tab === 'ADAPTERS' && <UpdateSiteDeclarativeAdapterAuthoringPanel onImported={async () => {
                        setExtensionAdapters(await loadUpdateSiteExtensionAdapters());
                    }} />}
                    {tab === 'PERFORMANCE' && <UpdateSitePerformancePanel performance={analysis?.performance} />}
                    {tab === 'GOVERNANCE' && <><Suspense fallback={<p>Loading distribution governance…</p>}><UpdateSiteDistributionGovernancePanel /></Suspense><UpdateSiteReleaseGovernancePanel /></>}
                    {analysis !== null && <>
                        {tab === 'RECOVERY' && <RecoveryPanel analysis={analysis} options={options}
                            setOptions={setOptions} previewMatchesSelection={previewMatchesSelection} analyze={reanalyze} />}
                        {tab === 'LOGS' && <UpdateSiteImportLogPanel logs={analysis.logs ?? []}
                            files={analysis.fileOutcomes ?? []} grammars={analysis.grammarInspections ?? []}
                            missingDependencies={analysis.repository.missingDependencies}
                            diagnostics={[...analysis.diagnostics, ...analysis.repository.diagnostics]} />}
                        {tab === 'EVIDENCE' && <UpdateSiteEvidencePanel analysis={analysis} />}
                        {tab === 'RECONCILIATION' && (analysisSource !== 'REMOTE_LIVE' ? <ReconciliationPanel report={reconciliation}
                            decisions={conflictDecisions} setDecisions={setConflictDecisions}
                            targetId={reconciledName} setTargetId={setReconciledName}
                            busy={busy === 'RECONCILE'} onReconcile={() => void reconcile()} /> : <RemoteReadOnlyNotice capability="reconciliation" />)}
                        {tab === 'MATERIALIZATION' && (analysisSource !== 'REMOTE_LIVE' ? <MaterializationPanel analysis={analysis}
                            selection={layerSelection} setSelection={setLayerSelection}
                            editorId={editorId} setEditorId={setEditorId} editorName={editorName}
                            setEditorName={setEditorName} result={materialization} busy={busy}
                            onPreview={() => void materialize(false)} onPersist={() => void materialize(true)}
                            onRollback={() => void rollback()} /> : <RemoteReadOnlyNotice capability="materialization" />)}
                        {tab === 'MERGE' && (analysisSource !== 'REMOTE_LIVE'
                            ? <UpdateSiteLayerMergePanel files={files} snapshotId={analysisSource === 'REMOTE_SNAPSHOT' ? remoteSnapshotId : undefined}
                                analysis={analysis} selection={layerSelection} />
                            : <RemoteReadOnlyNotice capability="selective merge" />)}
                        {tab === 'RELEASES' && <ReleasePanel release={analysis.releaseComparison ?? EMPTY_RELEASES} />}
                        {tab === 'SEMANTIC' && <SemanticPanel semantic={analysis.semanticRecovery ?? EMPTY_SEMANTIC} adapters={extensionAdapters} />}
                    </>}
                    {analysis === null && !['OVERVIEW', 'REMOTE', 'ADAPTERS', 'PERFORMANCE', 'EVOLUTION'].includes(tab)
                        && <AnalysisRequired />}
                </div>
            </div>
        </div>
    </section>;
}
function message(exception: unknown, fallback: string): string { return exception instanceof Error ? exception.message : fallback; }
function defaultConflictDecisions(report: UpdateSiteReconciliationReport): Record<string, string> {
    return Object.fromEntries(report.conflicts.map(conflict => [conflict.elementKey, conflict.recommendedCandidateId]));
}
