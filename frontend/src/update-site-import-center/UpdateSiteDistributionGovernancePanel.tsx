import { useMemo, useState, type ChangeEvent, type ReactNode } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraCheckbox, HydraFormField, HydraSelect, HydraTooltip } from '../design-system';
import { applyCapabilityOrchestration, approvePerformanceBaseline, downloadDistributionSbom,
    evaluateDistributionProvenance, evaluateMirrorUpdate, previewCapabilityOrchestration,
    rollbackMirrorChannel, runPerformanceAutomation, verifyDeclarativeAdapter } from './api';
import type { CapabilityOrchestrationPreview, CapabilityOrchestrationRequest,
    CapabilityPerformanceAutomationReport, CapabilityPerformanceScenarioRequest,
    HydraDistributionProvenanceReport, HydraDistributionProvenanceRequest, HydraCapabilityPackage, HydraLicensePolicy,
    UpdateSiteAdapterVerificationReport, UpdateSiteAdapterVerificationRequest,
    UpdateSiteMirrorChannel, UpdateSiteMirrorChannelState, UpdateSiteMirrorUpdateReport,
    UpdateSiteMirrorUpdateRequest } from './types';

const EMPTY_PACKAGE = { manifest: { schemaVersion: '1.0', id: 'example.capabilities', name: 'Example capabilities',
    packageVersion: '1.0.0', metamodelId: 'example', metamodelOrigin: { kind: 'OWNED', sourceTechnology: null,
        sourceIdentifier: '', sourceVersion: '', sourceLocation: '', checksum: '', nativePackage: '', annotations: {}, evidence: {} },
    capabilities: [], dependencies: [], compatibility: [], checksums: {}, annotations: {} },
    canonicalDocuments: {}, resources: {} };
const ORCHESTRATION = JSON.stringify({ basePackage: EMPTY_PACKAGE, sources: [{ sourceId: 'recovered-a', precedence: 10,
    afterSourceIds: [], capabilityPackage: EMPTY_PACKAGE, decisions: {} }],
    targetMetamodelId: 'example', expectedEtags: {}, layerOrder: [], persist: false }, null, 2);
const SAMPLE_ADAPTER = JSON.stringify({ schemaVersion: '1.0', adapter: {
    id: 'org.example:verified-extension', label: 'Verified extension', extensionPointTokens: ['org.example.verified'],
    elementNames: ['definition'], semanticKind: 'RUNTIME_EXTENSION', neutralTarget: 'runtimeExtension',
    purpose: 'Recover a declarative extension without executing code.', idAttributes: ['id'],
    implementationAttributes: ['class'], confidence: 'EXACT', enabled: true }, annotations: { version: '1.0.0' } }, null, 2);
const ADAPTER_VERIFICATION = JSON.stringify({ adapterId: '', version: '', document: SAMPLE_ADAPTER, fuzzCases: 32,
    invariants: ['EXPECTED_MATCH', 'REQUIRED_ID', 'REQUIRED_IMPLEMENTATION'] }, null, 2);
const PERFORMANCE = JSON.stringify({ scenarioId: 'update-site-overview', capabilityId: 'update-site-import-center', warmupCount: 1,
    samples: [sample('warmup', 520), sample('run-1', 500), sample('run-2', 510), sample('run-3', 495)],
    budget: { capabilityId: 'update-site-import-center', maxRenderMillis: 1200, maxHeapBytes: 268435456,
        maxRequests: 24, maxResponseBytes: 16777216, maxAnalysisMillis: 30000 }, allowedNoisePercent: 20, approveBaseline: false }, null, 2);

export function UpdateSiteDistributionGovernancePanel() {
    const [orchestrationText, setOrchestrationText] = useState(ORCHESTRATION);
    const [orchestration, setOrchestration] = useState<CapabilityOrchestrationPreview | null>(null);
    const [baselineMirrorId, setBaselineMirrorId] = useState('');
    const [candidateMirrorId, setCandidateMirrorId] = useState('');
    const [channel, setChannel] = useState<UpdateSiteMirrorChannel>('INTERNAL');
    const [promote, setPromote] = useState(false);
    const [allowRemovals, setAllowRemovals] = useState(false);
    const [mirrorUpdate, setMirrorUpdate] = useState<UpdateSiteMirrorUpdateReport | null>(null);
    const [channelState, setChannelState] = useState<UpdateSiteMirrorChannelState | null>(null);
    const [adapterText, setAdapterText] = useState(ADAPTER_VERIFICATION);
    const [adapterReport, setAdapterReport] = useState<UpdateSiteAdapterVerificationReport | null>(null);
    const [performanceText, setPerformanceText] = useState(PERFORMANCE);
    const [performanceReport, setPerformanceReport] = useState<CapabilityPerformanceAutomationReport | null>(null);
    const [productId, setProductId] = useState('hydra-package');
    const [version, setVersion] = useState('1.0.0');
    const [releaseGateId, setReleaseGateId] = useState('');
    const [licensePolicy, setLicensePolicy] = useState<HydraLicensePolicy>('STRICT');
    const [packageText, setPackageText] = useState(JSON.stringify(EMPTY_PACKAGE, null, 2));
    const [provenance, setProvenance] = useState<HydraDistributionProvenanceReport | null>(null);
    const [busy, setBusy] = useState('');
    const [error, setError] = useState('');
    const passedBlocks = useMemo(() => [orchestration?.applicable, mirrorUpdate?.promotable,
        adapterReport?.passed, performanceReport?.passed, provenance?.valid].filter(Boolean).length,
    [orchestration, mirrorUpdate, adapterReport, performanceReport, provenance]);

    async function runOrchestration(persist: boolean): Promise<void> {
        setBusy('AR');
        try {
            const request = parse<CapabilityOrchestrationRequest>(orchestrationText, 'orchestration request');
            const normalized = { ...request, persist };
            const result = persist ? await applyCapabilityOrchestration(normalized) : await previewCapabilityOrchestration(normalized);
            setOrchestration('preview' in result ? result.preview : result); setError('');
        } catch (exception) { setError(message(exception, 'Could not evaluate multiorigin orchestration')); }
        finally { setBusy(''); }
    }

    async function runMirrorUpdate(): Promise<void> {
        setBusy('AS');
        try {
            const request: UpdateSiteMirrorUpdateRequest = { baselineMirrorId, candidateMirrorId, channel,
                promote, allowRemovals, retainPrevious: true, validateRollback: true };
            setMirrorUpdate(await evaluateMirrorUpdate(request)); setError('');
        } catch (exception) { setError(message(exception, 'Could not evaluate mirror update')); }
        finally { setBusy(''); }
    }

    async function rollbackChannel(): Promise<void> {
        setBusy('AS_ROLLBACK');
        try { setChannelState(await rollbackMirrorChannel(channel)); setError(''); }
        catch (exception) { setError(message(exception, 'Could not roll back the mirror channel')); }
        finally { setBusy(''); }
    }

    async function runAdapterVerification(): Promise<void> {
        setBusy('AT');
        try { setAdapterReport(await verifyDeclarativeAdapter(parse<UpdateSiteAdapterVerificationRequest>(adapterText, 'adapter verification request'))); setError(''); }
        catch (exception) { setError(message(exception, 'Could not verify the declarative adapter')); }
        finally { setBusy(''); }
    }

    async function runPerformance(): Promise<void> {
        setBusy('AU');
        try { setPerformanceReport(await runPerformanceAutomation(parse<CapabilityPerformanceScenarioRequest>(performanceText, 'performance scenario'))); setError(''); }
        catch (exception) { setError(message(exception, 'Could not run the performance scenario')); }
        finally { setBusy(''); }
    }

    async function approveBaseline(): Promise<void> {
        if (performanceReport === null) return; setBusy('AU_APPROVE');
        try { await approvePerformanceBaseline(performanceReport.reportId); setError('Baseline approved.'); }
        catch (exception) { setError(message(exception, 'Could not approve the performance baseline')); }
        finally { setBusy(''); }
    }

    async function runProvenance(): Promise<void> {
        setBusy('AV');
        try {
            const request: HydraDistributionProvenanceRequest = { productId, version, channel, licensePolicy,
                capabilityPackage: parse<HydraCapabilityPackage>(packageText, 'capability package'), orchestrationId: orchestration?.orchestrationId ?? '',
                mirrorUpdateReportId: mirrorUpdate?.reportId ?? '', adapterVerificationReportIds: adapterReport === null ? [] : [adapterReport.reportId],
                performanceAutomationReportId: performanceReport?.reportId ?? '', releaseGateReportId: releaseGateId,
                annotations: { 'hydra.ui': 'distribution-governance' } };
            setProvenance(await evaluateDistributionProvenance(request)); setError('');
        } catch (exception) { setError(message(exception, 'Could not build distribution provenance')); }
        finally { setBusy(''); }
    }

    async function downloadSbom(): Promise<void> {
        if (provenance === null) return;
        try { download(await downloadDistributionSbom(provenance.reportId), `${provenance.reportId}.hydra-sbom.json`); }
        catch (exception) { setError(message(exception, 'Could not download the SBOM')); }
    }

    return <section className="update-site-v39-workbench">
        <header className="update-site-section-header"><div>
            <h3>Multiorigin delivery and verifiable distribution</h3><p>Compose several sources, update offline mirrors, fuzz adapters, automate performance baselines and publish a traced SBOM.</p></div>
            <HydraTooltip label="About distribution governance" title="Evidence-driven distribution"><p>Orchestration, mirror promotion, adapter verification, performance and provenance share one evidence-driven workflow. Review each block independently first; promote or publish only when the linked evidence explains what was verified and what still needs attention.</p></HydraTooltip></header>
        <div className="update-site-v39-progress"><strong>{passedBlocks}/5 blocks currently passing</strong><span>Each result remains independent until AV links the evidence.</span></div>
        {error !== '' && <HydraActionableErrorNotice className="error-message" message={error} context="update-site import" />}

        <Block code="AR" title="Multiorigin orchestration" description="Topologically order more than two portable sources and replay every AM decision deterministically.">
            <textarea aria-label="Multiorigin orchestration request" value={orchestrationText} onChange={(event: ChangeEvent<HTMLTextAreaElement>) => setOrchestrationText(event.target.value)} />
            <div className="hydra-center-actions"><HydraButton loading={busy === 'AR'} onClick={() => void runOrchestration(false)}>Preview orchestration</HydraButton>
                <HydraButton variant="primary" loading={busy === 'AR'} onClick={() => void runOrchestration(true)}>Apply transaction</HydraButton></div>
            {orchestration && <Result valid={orchestration.applicable} title={orchestration.orchestrationId}
                detail={`${orchestration.steps.length} replay step(s) · ${orchestration.precedenceOrder.join(' → ')}`} />}
        </Block>

        <Block code="AS" title="Incremental mirror channels" description="Compare AN mirrors, block unsafe stable removals, retain history and validate rollback.">
            <div className="update-site-v39-grid three"><HydraFormField label="Baseline mirror" targetId="v39-baseline"><input id="v39-baseline" value={baselineMirrorId} onChange={(event: ChangeEvent<HTMLInputElement>) => setBaselineMirrorId(event.target.value)} /></HydraFormField>
                <HydraFormField label="Candidate mirror" targetId="v39-candidate"><input id="v39-candidate" value={candidateMirrorId} onChange={(event: ChangeEvent<HTMLInputElement>) => setCandidateMirrorId(event.target.value)} /></HydraFormField>
                <HydraFormField label="Channel" targetId="v39-channel"><HydraSelect id="v39-channel" ariaLabel="Mirror channel" showFilter={false} value={channel} options={[{ value: 'INTERNAL', label: 'Internal' }, { value: 'BETA', label: 'Beta' }, { value: 'STABLE', label: 'Stable' }]} onChange={(value: string) => setChannel(value as UpdateSiteMirrorChannel)} /></HydraFormField></div>
            <div className="update-site-v39-checkboxes"><HydraCheckbox label="Promote candidate after validation" checked={promote} onChange={(event: ChangeEvent<HTMLInputElement>) => setPromote(event.target.checked)} />
                <HydraCheckbox label="Allow explicit removals" checked={allowRemovals} onChange={(event: ChangeEvent<HTMLInputElement>) => setAllowRemovals(event.target.checked)} /></div>
            <div className="hydra-center-actions"><HydraButton loading={busy === 'AS'} onClick={() => void runMirrorUpdate()}>Compare mirrors</HydraButton>
                <HydraButton loading={busy === 'AS_ROLLBACK'} onClick={() => void rollbackChannel()}>Rollback channel</HydraButton></div>
            {mirrorUpdate && <Result valid={mirrorUpdate.promotable} title={mirrorUpdate.reportId}
                detail={`${mirrorUpdate.added} added · ${mirrorUpdate.changed} changed · ${mirrorUpdate.removed} removed`} />}
            {channelState && <small>Current {channelState.channel}: {channelState.currentMirrorId || 'none'} · {channelState.history.length} retained</small>}
        </Block>

        <Block code="AT" title="Advanced adapter verification" description="Generate fixtures, fuzz bounded XML structures, check invariants and minimize failures.">
            <textarea aria-label="Advanced adapter verification request" value={adapterText} onChange={(event: ChangeEvent<HTMLTextAreaElement>) => setAdapterText(event.target.value)} />
            <HydraButton loading={busy === 'AT'} onClick={() => void runAdapterVerification()}>Verify adapter</HydraButton>
            {adapterReport && <Result valid={adapterReport.passed} title={adapterReport.reportId}
                detail={`${adapterReport.generatedFixtures} generated fixtures · ${adapterReport.fuzzCases} fuzz cases · ${adapterReport.failures} failures`} />}
        </Block>

        <Block code="AU" title="Automated performance baselines" description="Discard warm-up runs, calculate p50/p95, reject noisy samples and compare with an approved baseline.">
            <textarea aria-label="Performance automation scenario" value={performanceText} onChange={(event: ChangeEvent<HTMLTextAreaElement>) => setPerformanceText(event.target.value)} />
            <div className="hydra-center-actions"><HydraButton loading={busy === 'AU'} onClick={() => void runPerformance()}>Run scenario</HydraButton>
                <HydraButton disabled={!performanceReport?.passed} loading={busy === 'AU_APPROVE'} onClick={() => void approveBaseline()}>Approve baseline</HydraButton></div>
            {performanceReport && <Result valid={performanceReport.passed} title={performanceReport.reportId}
                detail={`p95 render ${performanceReport.percentiles.renderP95} ms · noise ${performanceReport.observedNoisePercent}%`} />}
        </Block>

        <Block code="AV" title="Distribution provenance and SBOM" description="Link the package, AR, AS, AT, AU and AQ evidence under one immutable distribution attestation.">
            <div className="update-site-v39-grid three"><HydraFormField label="Product" targetId="v39-product"><input id="v39-product" value={productId} onChange={(event: ChangeEvent<HTMLInputElement>) => setProductId(event.target.value)} /></HydraFormField>
                <HydraFormField label="Version" targetId="v39-version"><input id="v39-version" value={version} onChange={(event: ChangeEvent<HTMLInputElement>) => setVersion(event.target.value)} /></HydraFormField>
                <HydraFormField label="AQ gate report" targetId="v39-gate"><input id="v39-gate" value={releaseGateId} onChange={(event: ChangeEvent<HTMLInputElement>) => setReleaseGateId(event.target.value)} /></HydraFormField></div>
            <HydraFormField label="License policy" targetId="v39-license-policy"><HydraSelect id="v39-license-policy" ariaLabel="Distribution license policy" showFilter={false} value={licensePolicy}
                options={[{ value: 'STRICT', label: 'Strict' }, { value: 'COMPATIBLE_ONLY', label: 'Compatible only' }, { value: 'ALLOW_UNKNOWN', label: 'Allow unknown' }]}
                onChange={(value: string) => setLicensePolicy(value as HydraLicensePolicy)} /></HydraFormField>
            <textarea aria-label="Distribution capability package" value={packageText} onChange={(event: ChangeEvent<HTMLTextAreaElement>) => setPackageText(event.target.value)} />
            <div className="hydra-center-actions"><HydraButton variant="primary" loading={busy === 'AV'} onClick={() => void runProvenance()}>Build verified SBOM</HydraButton>
                <HydraButton disabled={provenance === null} onClick={() => void downloadSbom()}>Download SBOM</HydraButton></div>
            {provenance && <Result valid={provenance.valid} title={provenance.reportId}
                detail={`${provenance.components.length} components · attestation ${provenance.attestationSha256.slice(0, 16)}…`} />}
        </Block>
    </section>;
}

function Block({ code, title, description, children }: { code: string; title: string; description: string; children: ReactNode }) {
    return <article className="update-site-v39-block"><header><span>{code}</span><div><h4>{title}</h4><p>{description}</p></div></header>{children}</article>;
}
function Result({ valid, title, detail }: { valid: boolean; title: string; detail: string }) {
    return <div className={`update-site-v39-result ${valid ? 'valid' : 'invalid'}`}><strong>{valid ? 'Passed' : 'Blocked'} · {title}</strong><span>{detail}</span></div>;
}
function parse<T>(value: string, label: string): T { try { return JSON.parse(value) as T; } catch (exception) { throw new Error(`Invalid ${label}: ${message(exception, 'JSON parse error')}`, { cause: exception }); } }
function message(exception: unknown, fallback: string): string { return exception instanceof Error && exception.message.trim() !== '' ? exception.message : fallback; }
function download(blob: Blob, name: string): void { const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = name; anchor.click(); URL.revokeObjectURL(url); }
function sample(runId: string, renderMillis: number) { return { runId, capabilityId: 'update-site-import-center', capturedAt: '2026-08-04T00:00:00Z', renderMillis,
    heapBytes: 134217728, requestCount: 12, responseBytes: 4194304, analysisMillis: 5000,
    stageMillis: { analysis: 4200 }, chunkBytes: { 'update-site-import-center.js': 620000 } }; }
