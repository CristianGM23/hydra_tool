import { useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraCheckbox, HydraFormField, HydraTooltip } from '../design-system';
import { applyCapabilitySemanticMerge, compareCapabilityPerformanceReports, createOfflineP2Mirror,
    downloadOfflineP2Mirror, downloadReleaseGateHtml, evaluateReleaseGate, previewCapabilitySemanticMerge,
    runAdapterBench } from './api';
import type { CapabilityPerformanceComparison, CapabilitySemanticMergePreview,
    CapabilitySemanticMergeRequest, HydraCapabilityPackage, HydraReleaseGateReport,
    UpdateSiteAdapterBenchCase, UpdateSiteAdapterBenchReport, UpdateSiteP2MirrorResult } from './types';

const EMPTY_PACKAGE = JSON.stringify({ manifest: { schemaVersion: '1.0', id: 'example.capabilities', name: 'Example capabilities',
    packageVersion: '1.0.0', metamodelId: 'example', metamodelOrigin: { kind: 'OWNED', sourceTechnology: null,
        sourceIdentifier: '', sourceVersion: '', sourceLocation: '', checksum: '', nativePackage: '', annotations: {}, evidence: {} },
    capabilities: [], dependencies: [], compatibility: [], checksums: {}, annotations: {} },
    canonicalDocuments: {}, resources: {} }, null, 2);
const DEFAULT_FIXTURES = JSON.stringify([{ name: 'positive command', expectedMatch: true,
    fixture: { extensionPoint: 'org.example.commands', elementName: 'command', attributes: { id: 'example.command', class: 'example.Command' } } },
{ name: 'negative element', expectedMatch: false,
    fixture: { extensionPoint: 'org.example.commands', elementName: 'invalid', attributes: { id: 'invalid' } } }], null, 2);

export function UpdateSiteReleaseGovernancePanel() {
    const [existingText, setExistingText] = useState(EMPTY_PACKAGE);
    const [recoveredText, setRecoveredText] = useState(EMPTY_PACKAGE);
    const [targetMetamodelId, setTargetMetamodelId] = useState('example');
    const [expectedEtagsText, setExpectedEtagsText] = useState('{}');
    const [layerOrderText, setLayerOrderText] = useState('[]');
    const [mergePreview, setMergePreview] = useState<CapabilitySemanticMergePreview | null>(null);
    const [snapshotId, setSnapshotId] = useState('');
    const [mirror, setMirror] = useState<UpdateSiteP2MirrorResult | null>(null);
    const [adapterDocument, setAdapterDocument] = useState('');
    const [fixturesText, setFixturesText] = useState(DEFAULT_FIXTURES);
    const [bench, setBench] = useState<UpdateSiteAdapterBenchReport | null>(null);
    const [baselinePerformanceId, setBaselinePerformanceId] = useState('');
    const [candidatePerformanceId, setCandidatePerformanceId] = useState('');
    const [comparison, setComparison] = useState<CapabilityPerformanceComparison | null>(null);
    const [productId, setProductId] = useState('hydra-package');
    const [version, setVersion] = useState('1.0.0');
    const [releasePackageText, setReleasePackageText] = useState(EMPTY_PACKAGE);
    const [requireMirror, setRequireMirror] = useState(false);
    const [requireBench, setRequireBench] = useState(false);
    const [requirePerformance, setRequirePerformance] = useState(false);
    const [gate, setGate] = useState<HydraReleaseGateReport | null>(null);
    const [busy, setBusy] = useState('');
    const [error, setError] = useState('');

    const unresolved = useMemo(() => mergePreview?.dependencies.filter(item => !item.resolved).length ?? 0, [mergePreview]);

    async function previewMerge(persist: boolean): Promise<void> {
        setBusy(persist ? 'MERGE_APPLY' : 'MERGE_PREVIEW');
        try {
            const request = mergeRequest(existingText, recoveredText, targetMetamodelId, expectedEtagsText, layerOrderText, persist);
            if (persist) {
                const result = await applyCapabilitySemanticMerge(request); setMergePreview(result.preview);
            } else setMergePreview(await previewCapabilitySemanticMerge(request));
            setError('');
        } catch (exception) { setError(message(exception, 'Could not evaluate the semantic merge')); }
        finally { setBusy(''); }
    }

    async function createMirror(): Promise<void> {
        if (snapshotId.trim() === '') { setError('Enter a verified snapshot ID first.'); return; }
        setBusy('MIRROR');
        try { setMirror(await createOfflineP2Mirror(snapshotId.trim())); setError(''); }
        catch (exception) { setError(message(exception, 'Could not create the offline p2 mirror')); }
        finally { setBusy(''); }
    }

    async function downloadMirror(): Promise<void> {
        if (mirror === null) return; setBusy('MIRROR_DOWNLOAD');
        try { download(await downloadOfflineP2Mirror(mirror.manifest.mirrorId), `${mirror.manifest.mirrorId}.zip`); setError(''); }
        catch (exception) { setError(message(exception, 'Could not download the p2 mirror')); }
        finally { setBusy(''); }
    }

    async function runBench(): Promise<void> {
        setBusy('BENCH');
        try {
            const fixtures = parseJson<UpdateSiteAdapterBenchCase[]>(fixturesText, 'fixture suite');
            setBench(await runAdapterBench({ adapterId: '', version: '', document: adapterDocument,
                fixtures, includeMutations: true })); setError('');
        } catch (exception) { setError(message(exception, 'Could not run the adapter bench')); }
        finally { setBusy(''); }
    }

    async function comparePerformance(): Promise<void> {
        setBusy('PERFORMANCE');
        try { setComparison(await compareCapabilityPerformanceReports(baselinePerformanceId, candidatePerformanceId)); setError(''); }
        catch (exception) { setError(message(exception, 'Could not compare the performance reports')); }
        finally { setBusy(''); }
    }

    async function evaluateGate(): Promise<void> {
        setBusy('GATE');
        try {
            const capabilityPackage = parseJson<HydraCapabilityPackage>(releasePackageText, 'release package');
            setGate(await evaluateReleaseGate({ productId, version, capabilityPackage, semanticMerge: mergePreview,
                mirrorId: mirror?.manifest.mirrorId ?? '', adapterBenchReportIds: bench === null ? [] : [bench.reportId],
                performanceBaselineReportId: baselinePerformanceId, performanceCandidateReportId: candidatePerformanceId,
                requireSemanticMerge: mergePreview !== null, requireMirror, requireAdapterBench: requireBench,
                requirePerformance, annotations: { 'hydra.ui': 'release-governance' } })); setError('');
        } catch (exception) { setError(message(exception, 'Could not evaluate the release gate')); }
        finally { setBusy(''); }
    }

    async function downloadGate(): Promise<void> {
        if (gate === null) return; setBusy('GATE_DOWNLOAD');
        try { download(await downloadReleaseGateHtml(gate.reportId), `${gate.reportId}.html`); setError(''); }
        catch (exception) { setError(message(exception, 'Could not download the release gate report')); }
        finally { setBusy(''); }
    }

    return <section className="update-site-v38-workbench">
        <header className="update-site-section-header"><div>
            <h3>Semantic evolution and release gate</h3><p>Merge portable layers, publish an offline p2 mirror, test declarative adapters, compare performance and block incompatible releases.</p></div>
            <HydraTooltip label="About release governance" title="Verified publication workflow"><p>Semantic merge, mirrors, adapter verification and performance evidence are evaluated together before publication. Use the release gate to identify the exact blocking check, correct that evidence source and rerun the gate instead of changing unrelated recovered layers.</p></HydraTooltip></header>
        {error !== '' && <HydraActionableErrorNotice className="error-message" message={error} context="update-site import" />}

        <EvolutionBlock code="AM" title="Deep semantic merge" description="Resolve rules, quick fixes, operations, events, flow nodes, gamification rules, mappings and bindings by stable ID and semantic signature.">
            <div className="update-site-v38-grid two"><JsonField id="v38-existing" label="Existing capability package" value={existingText} setValue={setExistingText} />
                <JsonField id="v38-recovered" label="Recovered capability package" value={recoveredText} setValue={setRecoveredText} /></div>
            <HydraFormField label="Target metamodel ID" targetId="v38-target"><input id="v38-target" value={targetMetamodelId} onChange={event => setTargetMetamodelId(event.target.value)} /></HydraFormField>
            <div className="update-site-v38-grid two"><JsonField id="v38-etags" label="Expected ETags by HYDRA document path" value={expectedEtagsText} setValue={setExpectedEtagsText} />
                <JsonField id="v38-layer-order" label="Optional HYDRA layer order" value={layerOrderText} setValue={setLayerOrderText} /></div>
            <div className="hydra-center-actions"><HydraButton loading={busy === 'MERGE_PREVIEW'} onClick={() => void previewMerge(false)}>Preview semantic merge</HydraButton>
                <HydraButton variant="primary" loading={busy === 'MERGE_APPLY'} disabled={mergePreview === null || !mergePreview.applicable} onClick={() => void previewMerge(true)}>Apply through transaction</HydraButton></div>
            {mergePreview !== null && <ResultState valid={mergePreview.applicable} title={mergePreview.applicable ? 'Merge is applicable' : 'Merge is blocked'}
                details={`${mergePreview.items.length} entities · ${mergePreview.conflicts.length} conflicts · ${unresolved} unresolved references`} diagnostics={mergePreview.diagnostics} />}
        </EvolutionBlock>

        <EvolutionBlock code="AN" title="Offline p2 mirror" description="Regenerate content and artifact indexes from verified bytes and export a reproducible repository only after license review.">
            <HydraFormField label="Verified snapshot ID" targetId="v38-snapshot"><input id="v38-snapshot" value={snapshotId} onChange={event => setSnapshotId(event.target.value)} placeholder="p2-…" /></HydraFormField>
            <div className="hydra-center-actions"><HydraButton variant="primary" loading={busy === 'MIRROR'} onClick={() => void createMirror()}>Create mirror</HydraButton>
                <HydraButton disabled={mirror === null || !mirror.validation.valid} loading={busy === 'MIRROR_DOWNLOAD'} onClick={() => void downloadMirror()}>Download mirror</HydraButton></div>
            {mirror !== null && <ResultState valid={mirror.validation.valid} title={mirror.validation.valid ? 'Mirror consumed successfully' : 'Mirror validation failed'}
                details={`${mirror.validation.verifiedArtifacts}/${mirror.validation.declaredArtifacts} artifacts · ${mirror.manifest.mirrorId}`} diagnostics={mirror.validation.diagnostics} />}
        </EvolutionBlock>

        <EvolutionBlock code="AO" title="Adapter test bench" description="Run positive and negative fixtures, controlled mutations and rule coverage without executing external code.">
            <div className="update-site-v38-grid two"><JsonField id="v38-adapter" label="Inline adapter document" value={adapterDocument} setValue={setAdapterDocument} />
                <JsonField id="v38-fixtures" label="Fixture suite" value={fixturesText} setValue={setFixturesText} /></div>
            <HydraButton variant="primary" loading={busy === 'BENCH'} onClick={() => void runBench()}>Run fixtures and mutations</HydraButton>
            {bench !== null && <ResultState valid={bench.failedFixtures === 0} title={bench.failedFixtures === 0 ? 'Fixture suite passed' : 'Fixture suite failed'}
                details={`${bench.passedFixtures} passed · ${bench.failedFixtures} failed · ${bench.killedMutations} mutations killed · ${bench.survivingMutations} survived`} diagnostics={bench.diagnostics} />}
        </EvolutionBlock>

        <EvolutionBlock code="AP" title="Performance regression" description="Compare two AL reports and surface stage, heap, response and chunk regressions before release.">
            <div className="update-site-v38-grid two"><HydraFormField label="Baseline report ID" targetId="v38-perf-base"><input id="v38-perf-base" value={baselinePerformanceId} onChange={event => setBaselinePerformanceId(event.target.value)} /></HydraFormField>
                <HydraFormField label="Candidate report ID" targetId="v38-perf-candidate"><input id="v38-perf-candidate" value={candidatePerformanceId} onChange={event => setCandidatePerformanceId(event.target.value)} /></HydraFormField></div>
            <HydraButton loading={busy === 'PERFORMANCE'} disabled={baselinePerformanceId === '' || candidatePerformanceId === ''} onClick={() => void comparePerformance()}>Compare executions</HydraButton>
            {comparison !== null && <ResultState valid={!comparison.regression} title={comparison.regression ? 'Material regression detected' : 'No material regression'}
                details={`render ${signed(comparison.renderDelta)} ms · heap ${signed(comparison.heapDelta)} B · analysis ${signed(comparison.analysisDelta)} ms`} diagnostics={comparison.diagnostics} />}
        </EvolutionBlock>

        <EvolutionBlock code="AQ" title="Compatibility and publication gate" description="Aggregate package, merge, mirror, adapter and performance evidence into an immutable release attestation.">
            <div className="update-site-v38-grid two"><HydraFormField label="Product ID" targetId="v38-product"><input id="v38-product" value={productId} onChange={event => setProductId(event.target.value)} /></HydraFormField>
                <HydraFormField label="Version" targetId="v38-version"><input id="v38-version" value={version} onChange={event => setVersion(event.target.value)} /></HydraFormField></div>
            <JsonField id="v38-release-package" label="Capability package selected for release" value={releasePackageText} setValue={setReleasePackageText} />
            <div className="update-site-layer-options"><HydraCheckbox label="Require p2 mirror" checked={requireMirror} onChange={event => setRequireMirror(event.target.checked)} />
                <HydraCheckbox label="Require adapter bench" checked={requireBench} onChange={event => setRequireBench(event.target.checked)} />
                <HydraCheckbox label="Require performance comparison" checked={requirePerformance} onChange={event => setRequirePerformance(event.target.checked)} /></div>
            <div className="hydra-center-actions"><HydraButton variant="primary" loading={busy === 'GATE'} onClick={() => void evaluateGate()}>Evaluate release gate</HydraButton>
                <HydraButton disabled={gate === null} loading={busy === 'GATE_DOWNLOAD'} onClick={() => void downloadGate()}>Download HTML attestation</HydraButton></div>
            {gate !== null && <div className={`update-site-v38-gate ${gate.passed ? 'valid' : 'invalid'}`}><strong>{gate.passed ? 'Release gate passed' : 'Release blocked'}</strong><code>{gate.attestationSha256}</code>
                {gate.checks.map(check => <article key={check.code}><span className={`status-${check.status.toLowerCase()}`}>{check.status}</span><div><strong>{check.code}</strong><p data-hydra-runtime-message="true" data-hydra-message-context="update" data-hydra-message-code={check.code} data-hydra-message-severity={check.status}>{check.message}</p></div></article>)}</div>}
        </EvolutionBlock>
    </section>;
}

function EvolutionBlock({ code, title, description, children }: { code: string; title: string; description: string; children: React.ReactNode }) {
    return <section className="update-site-v38-block"><header><span>{code}</span><div><h4>{title}</h4><p>{description}</p></div></header>{children}</section>;
}
function JsonField({ id, label, value, setValue }: { id: string; label: string; value: string; setValue: (value: string) => void }) {
    return <HydraFormField label={label} targetId={id}><textarea id={id} rows={10} value={value} onChange={event => setValue(event.target.value)} spellCheck={false} /></HydraFormField>;
}
function ResultState({ valid, title, details, diagnostics }: { valid: boolean; title: string; details: string; diagnostics: string[] }) {
    return <div className={`update-site-adapter-validation ${valid ? 'valid' : 'invalid'}`}><strong>{title}</strong><small>{details}</small>{diagnostics.slice(0, 8).map(item => <p key={item}>{item}</p>)}</div>;
}
function mergeRequest(existingText: string, recoveredText: string, targetMetamodelId: string, expectedEtagsText: string, layerOrderText: string, persist: boolean): CapabilitySemanticMergeRequest {
    return { existingPackage: parseJson(existingText, 'existing package'), recoveredPackage: parseJson(recoveredText, 'recovered package'),
        targetMetamodelId, expectedEtags: parseJson<Record<string, string>>(expectedEtagsText, 'ETag map'),
        layerOrder: parseJson<string[]>(layerOrderText, 'layer order'), defaultDecision: 'MERGE', decisions: {}, persist };
}
function parseJson<T>(value: string, label: string): T { try { return JSON.parse(value) as T; } catch (exception) { throw new Error(`Invalid ${label}: ${exception instanceof Error ? exception.message : 'JSON parse error'}`, { cause: exception }); } }
function download(blob: Blob, name: string): void { const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = name; anchor.click(); URL.revokeObjectURL(url); }
function signed(value: number): string { return value > 0 ? `+${value}` : String(value); }
function message(exception: unknown, fallback: string): string { return exception instanceof Error ? exception.message : fallback; }
