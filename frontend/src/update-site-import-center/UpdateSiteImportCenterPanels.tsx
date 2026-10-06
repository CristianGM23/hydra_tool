import type { Dispatch, ReactNode, SetStateAction } from 'react';
import { HydraButton, HydraFormField, HydraSelect, HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { UpdateSiteImportDashboard } from './UpdateSiteImportDashboard';
import { UpdateSiteExtensionAdapterPanel } from './UpdateSiteExtensionAdapterPanel';
import { UpdateSiteLayerSelectionPanel } from './UpdateSiteLayerSelectionPanel';
import { UpdateSiteRecoveryIntelligence } from './UpdateSiteRecoveryIntelligence';
import { UpdateSiteReleaseComparisonPanel } from './UpdateSiteReleaseComparisonPanel';
import type { UpdateSiteBundleDescriptor, UpdateSiteConfidence, UpdateSiteImportAnalysis,
    UpdateSiteImportOptions, UpdateSiteLayerMaterializationResult,
    UpdateSiteLayerSelection, UpdateSiteReconciliationReport, UpdateSiteReleaseComparison,
    UpdateSiteSemanticRecovery, UpdateSiteExtensionAdapterDescriptor } from './types';

import { EMPTY_DASHBOARD, EMPTY_SEMANTIC, TAB_DEFINITIONS, type UpdateSiteTab, formatBytes } from './UpdateSiteImportCenterModel';
export function RemoteReadOnlyNotice({ capability }: { capability: string }) {
    return <section className="hydra-empty-state update-site-empty-state"><h3>Verified snapshot required</h3>
        <p>Live remote analysis remains read-only. Create or import a checksum-verified snapshot before using {capability}, so persistence reuses exactly the bytes that were analyzed.</p></section>;
}
export function EmptyAnalysis() {
    return <section className="hydra-empty-state update-site-empty-state update-site-empty-simple">
        <span className="update-site-empty-icon" aria-hidden="true">1</span>
        <h3>Choose a source to begin</h3>
        <p>HYDRA reads the update site without executing plug-in code. Start with an archive, a directory or a configured remote p2 repository.</p>
        <div className="update-site-simple-steps"><span><b>1</b> Choose source</span><span><b>2</b> Analyze safely</span><span><b>3</b> Review findings</span><span><b>4</b> Import what you trust</span></div>
    </section>;
}
export function AnalysisRequired() {
    return <section className="hydra-empty-state update-site-empty-state"><h3>Analysis required</h3>
        <p>Analyze a local or allowlisted remote p2 repository before opening this capability.</p></section>;
}
export function Overview({ analysis }: { analysis: UpdateSiteImportAnalysis }) {
    const dashboard = analysis.dashboard ?? EMPTY_DASHBOARD;
    const issueCount = dashboard.partialFiles + dashboard.unrecoverableFiles + dashboard.failedFiles;
    return <div className="update-site-overview-sections update-site-overview-simple">
        <section className="update-site-result-summary">
            <header><div><span>Analysis result</span><h3>{issueCount === 0 ? 'Ready for review' : 'Review recommended'}</h3><p>{issueCount === 0 ? 'HYDRA found no blocking recovery problems in the inspected files.' : `${issueCount} file outcome(s) need attention before you import everything.`}</p></div><span className="update-site-coverage-badge">{Math.round(dashboard.recoveryCoverage)}% recovered</span></header>
            <div className="update-site-simple-metrics"><article><strong>{dashboard.inspectedFiles}</strong><span>Files inspected</span></article><article><strong>{dashboard.recoveredFiles}</strong><span>Recovered</span></article><article><strong>{dashboard.partialFiles}</strong><span>Partial</span></article><article><strong>{dashboard.failedFiles + dashboard.unrecoverableFiles}</strong><span>Need attention</span></article></div>
        </section>
        <RepositorySummary analysis={analysis} />
        <UpdateSiteRecoveryIntelligence analysis={analysis} />
        <section className="update-site-recovered-content-summary">
            <header><div><strong>What HYDRA recovered</strong><span>A compact view of the reusable content found before you open technical details.</span></div></header>
            <div className="update-site-simple-metrics">
                <article><strong>{analysis.candidates.length}</strong><span>Metamodel candidates</span></article>
                <article><strong>{analysis.recoveredLayers.palettes.length}</strong><span>Palette groups</span></article>
                <article><strong>{analysis.recoveredLayers.representations.length}</strong><span>Representations</span></article>
                <article><strong>{analysis.recoveredLayers.mappings.length}</strong><span>Mappings</span></article>
                <article><strong>{analysis.semanticRecovery?.representableCount ?? 0}</strong><span>Semantic elements</span></article>
                <article><strong>{analysis.grammarInspections?.length ?? 0}</strong><span>Grammars</span></article>
            </div>
        </section>
        <section className="update-site-next-step">
            <SectionHeader title="What should I do next?" help="Use the simple path first. Detailed technical evidence remains available only when you need it." />
            <div className="update-site-next-step-grid compact"><article><span>1</span><div><strong>Choose the recovery candidate</strong><p>Open Recovery and keep the recommended source unless you have a reason to choose another.</p></div></article><article><span>2</span><div><strong>Review warnings</strong><p>Open Logs only if HYDRA reports partial, failed or unrecoverable files.</p></div></article><article><span>3</span><div><strong>Create HYDRA content</strong><p>Use Materialization or Merge after the candidate is reviewed.</p></div></article></div>
        </section>
        <details className="update-site-technical-dashboard"><summary><span><strong>Detailed technical dashboard</strong><small>Coverage, stages, file outcomes and internal recovery flow.</small></span><b aria-hidden="true">⌄</b></summary><UpdateSiteImportDashboard dashboard={dashboard} /></details>
    </div>;
}
export function RecoveryPanel({ analysis, options, setOptions, previewMatchesSelection, analyze }: {
    analysis: UpdateSiteImportAnalysis;
    options: UpdateSiteImportOptions;
    setOptions: Dispatch<SetStateAction<UpdateSiteImportOptions>>;
    previewMatchesSelection: boolean;
    analyze: (candidateId?: string) => Promise<void>;
}) {
    const { t } = useHydraLanguage();
    return <div className="update-site-recovery-sections">
        <section>
            <SectionHeader title="Escalated metamodel recovery"
                help="HYDRA searches original metamodels first, then GenModel, plug-in registry, EMF bytecode and finally general code inference. Confidence is never promoted silently." />
            <div className="update-site-candidates">
                {analysis.candidates.map(candidate => <label
                    className={`update-site-candidate${candidate.id === options.candidateId ? ' selected' : ''}`}
                    key={candidate.id}>
                    <input type="radio" name="update-site-candidate"
                        checked={candidate.id === options.candidateId}
                        onChange={() => setOptions(current => ({ ...current, candidateId: candidate.id }))} />
                    <span className="update-site-candidate-main">
                        <strong>{t('Level')} {candidate.level} · {t(human(candidate.method))}</strong>
                        <code>{candidate.sourcePath}</code>
                        <span>{t(candidate.rationale)}</span>
                        {candidate.warnings.map(warning => <small key={warning}>{t(warning)}</small>)}
                    </span>
                    <Confidence value={candidate.confidence} />
                </label>)}
            </div>
            {!previewMatchesSelection && options.candidateId !== ''
                && <HydraButton onClick={() => void analyze(options.candidateId)}>Analyze selected candidate</HydraButton>}
        </section>
        <BundleInventory bundles={analysis.repository.bundles} />
        <RecoveredLayers analysis={analysis} />
    </div>;
}
export function SectionNavigation({ selected, onSelect }: { selected: UpdateSiteTab; onSelect: (tab: UpdateSiteTab) => void }) {
    const current = TAB_DEFINITIONS.find(definition => definition.id === selected) ?? TAB_DEFINITIONS[0];
    const groups = [
        { id: 'understand', title: '1. Understand', description: 'See what HYDRA found and which recovery candidate is safest.', tabs: TAB_DEFINITIONS.filter(item => item.group === 'Understand') },
        { id: 'review', title: '2. Review', description: 'Inspect diagnostics and evidence only when you need more detail.', tabs: TAB_DEFINITIONS.filter(item => item.group === 'Review') },
        { id: 'act', title: '3. Build', description: 'Resolve differences, materialize layers or merge into an existing editor.', tabs: TAB_DEFINITIONS.filter(item => item.group === 'Act') },
    ];
    const renderButton = (definition: typeof TAB_DEFINITIONS[number]) => <HydraButton
        aria-label={definition.label} aria-selected={selected === definition.id} data-group={definition.group}
        key={definition.id} onClick={() => onSelect(definition.id)} role="tab" type="button">
        <strong>{definition.label}</strong><small>{definition.description}</small>
    </HydraButton>;
    const expert = TAB_DEFINITIONS.filter(item => item.group === 'Advanced');
    return <div className="update-site-navigation update-site-navigation-simplified">
        <div className="update-site-navigation-sections">
            {groups.map(group => <section key={group.id} className="update-site-nav-section">
                <header><strong>{group.title}</strong><span>{group.description}</span></header>
                <nav aria-label={`${group.title} update site sections`} role="tablist">{group.tabs.map(renderButton)}</nav>
            </section>)}
            <details className="update-site-specialized-capabilities" open={current.group === 'Advanced' ? true : undefined}>
                <summary><span><strong>Specialized capabilities</strong><small>Remote repositories, release comparison, semantic recovery, adapters and performance tools.</small></span><b aria-hidden="true">⌄</b></summary>
                <nav aria-label="Specialized update site capabilities" role="tablist">{expert.map(renderButton)}</nav>
            </details>
        </div>
        <div className="update-site-active-section-summary" aria-live="polite"><strong>{current.label}</strong><span>{current.description}</span></div>
    </div>;
}
export function ControlSection({ title, help, children }: { title: string; help: string; children: ReactNode }) {
    return <section className="update-site-control-section">
        <header>
            <h3>{title}</h3>
            <HydraTooltip label={`About ${title}`} title={title}><p>{help}</p></HydraTooltip>
        </header>
        {children}
    </section>;
}
export function ReconciliationPanel({ report, decisions, setDecisions, targetId, setTargetId, busy, onReconcile }: { report: UpdateSiteReconciliationReport; decisions: Record<string, string>;
    setDecisions: React.Dispatch<React.SetStateAction<Record<string, string>>>; targetId: string; setTargetId: (value: string) => void; busy: boolean; onReconcile: () => void }) {
    return <section><SectionHeader title="Metamodel reconciliation assistant" help="Compares Ecore, GenModel, registry and bytecode candidates element by element. HYDRA never resolves a structural discrepancy without an explicit source decision." />
        <div className="update-site-reconciliation-summary"><article><strong>{report.candidates.length}</strong><span>Candidates compared</span></article><article><strong>{report.unionElements}</strong><span>Union elements</span></article><article><strong>{report.conflicts.filter(conflict => conflict.blocking).length}</strong><span>Decisions required</span></article><article><strong>{report.conflicts.filter(conflict => !conflict.blocking).length}</strong><span>Safe auto-resolutions</span></article><article><strong>{Math.round(report.agreementPercent)}%</strong><span>Agreement</span></article></div>
        <div className="update-site-coverage-list">{report.candidates.map(candidate => <article key={candidate.candidateId}><header><strong>{candidate.candidateId}</strong><Confidence value={candidate.confidence} /></header><progress max="100" value={candidate.coveragePercent} /><small>{Math.round(candidate.coveragePercent)}% coverage · {candidate.uniqueElements} unique · {candidate.conflictingElements} conflicting</small><p>{candidate.recommendation}</p></article>)}</div>
        {report.conflicts.map(conflict => <article className={`update-site-conflict${conflict.blocking ? ' blocking' : ' safe'}`} key={conflict.elementKey}><header><div><strong>{conflict.displayName}</strong><code>{conflict.elementKey}</code><small>{conflict.blocking ? 'Manual decision required' : 'Safe automatic resolution available'}</small></div><HydraTooltip label={`About conflict ${conflict.displayName}`} title={conflict.blocking ? "Structural discrepancy" : "Descriptive difference"}><p>{conflict.explanation}</p><p>Signatures summarize structure for comparison; they are not source code. Use them to decide which evidence preserves the intended element shape, cardinality and references before accepting a reconciliation decision.</p></HydraTooltip></header>
            <HydraFormField label="Evidence to keep" targetId={`conflict-${safeDomId(conflict.elementKey)}`}><HydraSelect id={`conflict-${safeDomId(conflict.elementKey)}`} ariaLabel="Evidence to keep" value={decisions[conflict.elementKey] ?? ''} placeholder="Choose evidence" options={Object.keys(conflict.candidateSignatures).map(candidateId => ({ value:candidateId,label:`${candidateId}${candidateId === conflict.recommendedCandidateId ? ' · recommended' : ''}`,description:candidateId === conflict.recommendedCandidateId?'Recommended evidence according to the recovery comparison.':'Alternative evidence available for this conflicting element.' }))} onChange={value => setDecisions(current => ({ ...current, [conflict.elementKey]: value }))} /></HydraFormField>
        </article>)}
        <HydraFormField label="Reconciled metamodel ID" targetId="reconciled-target"><input id="reconciled-target" value={targetId} onChange={event => setTargetId(event.target.value)} placeholder="Optional stable ID" /></HydraFormField>
        <HydraButton variant="primary" loading={busy} disabled={report.candidates.length === 0 || report.conflicts.some(conflict => conflict.blocking && !decisions[conflict.elementKey])} onClick={onReconcile}>Build reconciled preview</HydraButton>
    </section>;
}
export function MaterializationPanel({ analysis, selection, setSelection, editorId, setEditorId, editorName, setEditorName, result, busy, onPreview, onPersist, onRollback }: { analysis: UpdateSiteImportAnalysis; selection: UpdateSiteLayerSelection; setSelection: (value: UpdateSiteLayerSelection) => void; editorId: string; setEditorId: (value: string) => void; editorName: string; setEditorName: (value: string) => void; result: UpdateSiteLayerMaterializationResult | null; busy: string; onPreview: () => void; onPersist: () => void; onRollback: () => void }) {
    const layers = analysis.recoveredLayers; const semantic = analysis.semanticRecovery ?? EMPTY_SEMANTIC;
    return <section><SectionHeader title="HYDRA layer materialization" help="Converts selected GMF, Sirius, Xtext and generated-editor drafts into an editable HYDRA editor definition. Preview validates references before anything is stored." />
        <div className="update-site-materialization-counts"><article><strong>{layers.palettes.length}</strong><span>Palette groups</span></article><article><strong>{layers.representations.length}</strong><span>Representations</span></article><article><strong>{layers.mappings.length}</strong><span>Mappings</span></article><article><strong>{semantic.elements.length}</strong><span>Semantic drafts</span></article></div>
        <UpdateSiteLayerSelectionPanel analysis={analysis} selection={selection} onChange={setSelection} />
        <div className="update-site-materialization-fields"><HydraFormField label="Editor ID" targetId="recovered-editor-id"><input id="recovered-editor-id" value={editorId} onChange={event => setEditorId(event.target.value)} placeholder="Generated when empty" /></HydraFormField><HydraFormField label="Editor name" targetId="recovered-editor-name"><input id="recovered-editor-name" value={editorName} onChange={event => setEditorName(event.target.value)} placeholder="Recovered editor" /></HydraFormField></div>
        <div className="hydra-center-actions"><HydraButton loading={busy === 'MATERIALIZE'} onClick={onPreview}>Preview materialization</HydraButton><HydraButton variant="primary" loading={busy === 'MATERIALIZE'} onClick={onPersist}>Import and materialize selected layers</HydraButton>{result?.rollbackAvailable && <HydraButton variant="danger" loading={busy === 'ROLLBACK'} onClick={onRollback}>Rollback materialization</HydraButton>}</div>
        {result !== null && <div className={`update-site-materialization-result${result.preview.applicable ? ' applicable' : ' blocked'}`}><strong>{result.preview.applicable ? 'Materialization is valid' : 'Materialization is blocked'}</strong><span>{result.preview.selectedTools} tools · {result.preview.selectedRepresentations} representations · {result.preview.selectedMappings} mappings</span>{Array.from(new Set([...result.preview.diagnostics, ...result.diagnostics])).map(message => <small key={message}>{message}</small>)}</div>}
    </section>;
}
export function ReleasePanel({ release }: { release: UpdateSiteReleaseComparison }) {
    return <UpdateSiteReleaseComparisonPanel release={release} />;
}
export function SemanticPanel({ semantic, adapters }: { semantic: UpdateSiteSemanticRecovery; adapters: UpdateSiteExtensionAdapterDescriptor[] }) {
    return <section><SectionHeader title="Generated editor semantics" help="Recovers commands, handlers, menus, editors, views, forms, validations and runtime registrations from plugin.xml and generated class structure. Unsupported extension points are preserved explicitly." />
        <div className="update-site-semantic-summary"><article><strong>{semantic.representableCount}</strong><span>Representable</span></article><article><strong>{semantic.partialCount}</strong><span>Partial</span></article><article><strong>{semantic.unrepresentableCount}</strong><span>Unrepresentable</span></article></div>
        <div className="update-site-semantic-list">{semantic.elements.map(element => <article key={element.id}><header><div><strong>{element.label || element.id}</strong><span>{human(element.kind)}</span></div><Confidence value={element.confidence} /></header><p>{element.purpose}</p>{element.neutralTarget !== '' && <small>Neutral target: {element.neutralTarget}</small>}<code>{element.sourcePath}</code>{element.warnings.map(warning => <small key={warning}>{warning}</small>)}</article>)}</div>
        <UpdateSiteExtensionAdapterPanel adapters={adapters} semantic={semantic} />
    </section>;
}

export function RepositorySummary({ analysis }: { analysis: UpdateSiteImportAnalysis }) {
    const repository = analysis.repository;
    return <section><header><h3>{repository.name}</h3><code>{repository.sourceFingerprint.slice(0, 24)}…</code></header><div className="update-site-summary-grid"><article><strong>{repository.features.length}</strong><span>Features</span></article><article><strong>{repository.bundles.length}</strong><span>Plug-ins</span></article><article><strong>{repository.repositoryFileCount}</strong><span>Files inspected</span></article><article><strong>{analysis.provenance.length}</strong><span>Traced elements</span></article></div><p>p2 content metadata: <strong>{repository.contentMetadataFound ? 'found' : 'not found'}</strong> · artifact metadata: <strong>{repository.artifactMetadataFound ? 'found' : 'not found'}</strong> · source size: {formatBytes(repository.repositorySizeBytes)}</p>{analysis.metamodelPreview !== null && <p>Preview: <strong>{analysis.metamodelPreview.metamodel.name}</strong> · {analysis.metamodelPreview.statistics.classes} classes · {analysis.metamodelPreview.statistics.relations} relations.</p>}</section>;
}
export function BundleInventory({ bundles }: { bundles: UpdateSiteBundleDescriptor[] }) { return <section><SectionHeader title="Bundle classification" help="Role classification combines bundle names, OSGi dependencies, plugin.xml extensions and contained model/editor artifacts." /><div className="update-site-table-wrap"><table className="update-site-table"><thead><tr><th>Bundle</th><th>Role</th><th>Relevant artifacts</th><th>Reason</th></tr></thead><tbody>{bundles.map(bundle => <tr key={`${bundle.id}:${bundle.version}`}><td><strong>{bundle.id}</strong><br /><small>{bundle.version}</small></td><td>{bundle.kind.replaceAll('_', ' ')}</td><td>{bundle.artifacts.filter(item => item.kind !== 'JAVA_CLASS').length}</td><td>{bundle.classificationSignals.join('; ')}</td></tr>)}</tbody></table></div></section>; }
export function RecoveredLayers({ analysis }: { analysis: UpdateSiteImportAnalysis }) { const layers = analysis.recoveredLayers; return <section><SectionHeader title="Neutral layer drafts" help="Drafts remain separate until materialization. You can accept only the palette groups, representations and mappings you trust." /><div className="update-site-layer-grid"><article><strong>{layers.palettes.length}</strong><span>Palette groups</span><small>{layers.palettes.reduce((total, group) => total + group.tools.length, 0)} tools</small></article><article><strong>{layers.representations.length}</strong><span>Representations</span><small>{unique(layers.representations.map(item => item.technology)).join(', ') || 'None'}</small></article><article><strong>{layers.mappings.length}</strong><span>Mappings</span><small>Semantic, visual and grammar mappings</small></article></div>{layers.diagnostics.map(message => <p className="error-message" key={message}>{message}</p>)}</section>; }
function SectionHeader({ title, help }: { title: string; help: string }) { return <header className="update-site-section-header"><div><h3>{title}</h3></div><HydraTooltip label={`About ${title}`} title={title}><p>{help}</p></HydraTooltip></header>; }
function Confidence({ value }: { value: UpdateSiteConfidence }) { const { t } = useHydraLanguage(); return <span className={`update-site-confidence confidence-${value.toLowerCase()}`}>{t(value === 'EXACT' ? 'Exact' : value === 'RECOVERED' ? 'Recovered' : value === 'DERIVED' ? 'Derived' : 'Inferred')}</span>; }
function unique(values: string[]): string[] { return Array.from(new Set(values)); }
function human(value: string): string { return value.replaceAll('_', ' ').toLowerCase().replace(/^./, letter => letter.toUpperCase()); }


function safeDomId(value: string): string { return value.replaceAll(/[^A-Za-z0-9_-]/g, '-'); }
