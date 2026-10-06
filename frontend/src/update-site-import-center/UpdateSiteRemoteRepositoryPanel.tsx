import { useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraCheckbox, HydraFormField, HydraTooltip } from '../design-system';
import { analyzeRemoteUpdateSite, analyzeRemoteUpdateSiteSnapshot, checkRemoteUpdateSiteSnapshotDrift,
    createRemoteUpdateSiteSnapshot, downloadRemoteUpdateSiteSnapshot, importRemoteUpdateSiteSnapshot,
    reviewRemoteUpdateSiteSnapshotLicenses } from './api';
import type { UpdateSiteImportAnalysis, UpdateSiteImportOptions, UpdateSiteRemoteAnalysisResult,
    UpdateSiteRemoteSnapshotDriftReport, UpdateSiteRemoteSnapshotLicenseEntry,
    UpdateSiteRemoteSnapshotResult } from './types';

interface Props {
    options: UpdateSiteImportOptions;
    onAnalysis: (analysis: UpdateSiteImportAnalysis, snapshotId: string | null) => void;
}

type Busy = 'ANALYZE' | 'SNAPSHOT' | 'IMPORT' | 'OFFLINE' | 'DRIFT' | 'LICENSE' | 'EXPORT' | '';

export function UpdateSiteRemoteRepositoryPanel({ options, onAnalysis }: Props) {
    const [repositoryUrl, setRepositoryUrl] = useState('');
    const [expectedSha256, setExpectedSha256] = useState('');
    const [maximumDepth, setMaximumDepth] = useState(2);
    const [useCache, setUseCache] = useState(true);
    const [busy, setBusy] = useState<Busy>('');
    const [error, setError] = useState('');
    const [result, setResult] = useState<UpdateSiteRemoteAnalysisResult | null>(null);
    const [snapshot, setSnapshot] = useState<UpdateSiteRemoteSnapshotResult | null>(null);
    const [drift, setDrift] = useState<UpdateSiteRemoteSnapshotDriftReport | null>(null);
    const [snapshotFile, setSnapshotFile] = useState<File | null>(null);
    const [licenseJson, setLicenseJson] = useState('[]');

    const request = { repositoryUrl: repositoryUrl.trim(), expectedSha256: expectedSha256.trim().toLowerCase(),
        maximumDepth, useCache, options };

    async function analyze(): Promise<void> {
        if (repositoryUrl.trim() === '') { setError('Enter an HTTPS update-site URL.'); return; }
        setBusy('ANALYZE');
        try {
            const next = await analyzeRemoteUpdateSite(request);
            setResult(next); setSnapshot(null); setDrift(null); setError(''); onAnalysis(next.analysis, null);
        } catch (exception) { setError(message(exception, 'Could not analyze the remote repository')); }
        finally { setBusy(''); }
    }

    async function createSnapshot(): Promise<void> {
        if (repositoryUrl.trim() === '') { setError('Enter an HTTPS update-site URL.'); return; }
        setBusy('SNAPSHOT');
        try {
            const next = await createRemoteUpdateSiteSnapshot(request);
            setSnapshot(next); setLicenseJson(licenseTemplate(next.lock.licenses, next.lock.resources));
            setResult(null); setDrift(null); setError(''); onAnalysis(next.analysis, next.lock.snapshotId);
        } catch (exception) { setError(message(exception, 'Could not create the reproducible p2 snapshot')); }
        finally { setBusy(''); }
    }

    async function importSnapshot(): Promise<void> {
        if (snapshotFile === null) { setError('Select a .hydra-p2-snapshot.zip archive.'); return; }
        setBusy('IMPORT');
        try {
            const next = await importRemoteUpdateSiteSnapshot(snapshotFile, options);
            setSnapshot(next); setLicenseJson(licenseTemplate(next.lock.licenses, next.lock.resources));
            setResult(null); setDrift(null); setError(''); onAnalysis(next.analysis, next.lock.snapshotId);
        } catch (exception) { setError(message(exception, 'Could not import the p2 snapshot')); }
        finally { setBusy(''); }
    }

    async function analyzeOffline(): Promise<void> {
        if (snapshot === null) return;
        setBusy('OFFLINE');
        try { const next = await analyzeRemoteUpdateSiteSnapshot({ snapshotId: snapshot.lock.snapshotId, options }); onAnalysis(next, snapshot.lock.snapshotId); setError(''); }
        catch (exception) { setError(message(exception, 'Could not reanalyze the stored snapshot')); }
        finally { setBusy(''); }
    }

    async function checkDrift(): Promise<void> {
        if (snapshot === null) return;
        setBusy('DRIFT');
        try { setDrift(await checkRemoteUpdateSiteSnapshotDrift(snapshot.lock.snapshotId)); setError(''); }
        catch (exception) { setError(message(exception, 'Could not compare the snapshot with the remote repository')); }
        finally { setBusy(''); }
    }

    async function saveLicenseReview(): Promise<void> {
        if (snapshot === null) return;
        let licenses: UpdateSiteRemoteSnapshotLicenseEntry[];
        try { licenses = JSON.parse(licenseJson) as UpdateSiteRemoteSnapshotLicenseEntry[]; }
        catch { setError('License review must be valid JSON.'); return; }
        setBusy('LICENSE');
        try {
            const lock = await reviewRemoteUpdateSiteSnapshotLicenses(snapshot.lock.snapshotId, licenses);
            setSnapshot(current => current === null ? current : { ...current, lock });
            setLicenseJson(JSON.stringify(lock.licenses, null, 2)); setError('');
        } catch (exception) { setError(message(exception, 'Could not save the snapshot license review')); }
        finally { setBusy(''); }
    }

    async function exportSnapshot(): Promise<void> {
        if (snapshot === null) return;
        setBusy('EXPORT');
        try {
            const blob = await downloadRemoteUpdateSiteSnapshot(snapshot.lock.snapshotId);
            const url = URL.createObjectURL(blob); const anchor = document.createElement('a');
            anchor.href = url; anchor.download = `${snapshot.lock.snapshotId}.hydra-p2-snapshot.zip`; anchor.click();
            URL.revokeObjectURL(url); setError('');
        } catch (exception) { setError(message(exception, 'Could not export the snapshot')); }
        finally { setBusy(''); }
    }

    return <section className="update-site-v36-panel">
        <header className="update-site-section-header"><div><h3>Reproducible remote p2 trees</h3><p>Analyze an allowlisted repository directly or capture every resolved byte in a content-addressed offline snapshot.</p></div>
            <HydraTooltip label="About reproducible remote p2 trees" title="Remote snapshot policy"><p>HYDRA blocks redirects, local addresses and non-HTTPS URLs. A snapshot locks each URL, virtual path, byte count and SHA-256, and can be reanalyzed without contacting the remote server.</p></HydraTooltip></header>
        <div className="update-site-v36-form-grid">
            <HydraFormField label="Repository URL" targetId="remote-update-site-url" required help="Use an HTTPS archive URL or the base URL of a p2 repository.">
                <input id="remote-update-site-url" value={repositoryUrl} onChange={event => setRepositoryUrl(event.target.value)} placeholder="https://updates.example.org/releases/latest/" />
            </HydraFormField>
            <HydraFormField label="Expected SHA-256" targetId="remote-update-site-sha" help="Optional for archives. Analysis stops if the downloaded content does not match.">
                <input id="remote-update-site-sha" value={expectedSha256} onChange={event => setExpectedSha256(event.target.value)} placeholder="64 hexadecimal characters" />
            </HydraFormField>
            <HydraFormField label="Composite depth" targetId="remote-update-site-depth">
                <input id="remote-update-site-depth" type="number" min="0" max="16" value={maximumDepth} onChange={event => setMaximumDepth(Math.max(0, Number(event.target.value) || 0))} />
            </HydraFormField>
            <HydraCheckbox label="Use content-addressed cache" checked={useCache} description="Reuse verified resources while preserving the lockfile hashes." onChange={event => setUseCache(event.target.checked)} />
        </div>
        <div className="hydra-center-actions">
            <HydraButton loading={busy === 'ANALYZE'} onClick={() => void analyze()}>Analyze live repository</HydraButton>
            <HydraButton variant="primary" loading={busy === 'SNAPSHOT'} onClick={() => void createSnapshot()}>Create offline snapshot</HydraButton>
        </div>
        <div className="update-site-snapshot-import"><HydraFormField label="Import offline snapshot" targetId="remote-snapshot-file" help="The lockfile, tree hash, declared sizes and every content-addressed object are verified before storage.">
            <input id="remote-snapshot-file" type="file" accept=".zip,.hydra-p2-snapshot.zip" onChange={event => setSnapshotFile(event.target.files?.[0] ?? null)} />
        </HydraFormField><HydraButton loading={busy === 'IMPORT'} disabled={snapshotFile === null} onClick={() => void importSnapshot()}>Verify and import snapshot</HydraButton></div>
        {error !== '' && <HydraActionableErrorNotice className="error-message" message={error} context="update-site import" />}
        {result !== null && <RemoteEvidence result={result} />}
        {snapshot !== null && <div className="update-site-v36-result">
            <div className="update-site-v36-kpis"><article><strong>{snapshot.lock.resources.length}</strong><span>Locked resources</span></article>
                <article><strong>{formatBytes(snapshot.lock.totalBytes)}</strong><span>Snapshot bytes</span></article>
                <article><strong>{snapshot.reused ? 'Reused' : 'Created'}</strong><span>Content identity</span></article>
                <article><strong>{snapshot.lock.snapshotId}</strong><span>Snapshot ID</span></article></div>
            <dl className="update-site-performance-details"><div><dt>Tree SHA-256</dt><dd><code>{snapshot.lock.treeSha256}</code></dd></div>
                <div><dt>Root URL</dt><dd>{snapshot.lock.rootUrl}</dd></div></dl>
            <HydraFormField label="Per-resource license review" targetId="remote-snapshot-licenses" required help="Export stays blocked until every locked resource identifies author, origin, license, obligations and compatible redistribution.">
                <textarea id="remote-snapshot-licenses" value={licenseJson} onChange={event => setLicenseJson(event.target.value)} spellCheck={false} />
            </HydraFormField>
            <div className="hydra-center-actions"><HydraButton loading={busy === 'OFFLINE'} onClick={() => void analyzeOffline()}>Reanalyze offline</HydraButton>
                <HydraButton loading={busy === 'DRIFT'} onClick={() => void checkDrift()}>Check remote drift</HydraButton>
                <HydraButton loading={busy === 'LICENSE'} onClick={() => void saveLicenseReview()}>Save license review</HydraButton>
                <HydraButton loading={busy === 'EXPORT'} disabled={!licenseReviewComplete(snapshot.lock.licenses, snapshot.lock.resources.length)} onClick={() => void exportSnapshot()}>Export snapshot</HydraButton></div>
            {!licenseReviewComplete(snapshot.lock.licenses, snapshot.lock.resources.length) && <div className="update-site-adapter-validation invalid"><strong>Export requires license review</strong><small>Complete every resource entry and explicitly confirm compatibility and redistribution permission.</small></div>}
            {drift !== null && <div className={drift.drifted ? 'update-site-adapter-validation invalid' : 'update-site-adapter-validation valid'}>
                <strong>{drift.drifted ? 'Remote repository has drifted' : 'Remote repository still matches'}</strong>
                <small>{drift.unchanged} unchanged · {drift.changed} changed · {drift.unavailable} unavailable</small>
                {drift.items.filter(item => item.status !== 'UNCHANGED').map(item => <p key={item.url}>{item.status}: {item.url} — {item.explanation}</p>)}
            </div>}
        </div>}
    </section>;
}

function RemoteEvidence({ result }: { result: UpdateSiteRemoteAnalysisResult }) {
    return <div className="update-site-v36-result"><div className="update-site-v36-kpis"><article><strong>{result.fetchedResources.length}</strong><span>Resources resolved</span></article>
        <article><strong>{formatBytes(result.downloadedBytes)}</strong><span>Downloaded</span></article><article><strong>{result.cacheHits}</strong><span>Cache hits</span></article>
        <article><strong>{result.analysis.repository.repositoryFileCount}</strong><span>Files analyzed</span></article></div>
        <div className="update-site-remote-evidence">{result.fetchedResources.map(resource => <article key={`${resource.url}:${resource.virtualPath}`}>
            <div><strong>{resource.virtualPath}</strong><code>{resource.url}</code></div><span>{resource.cacheHit ? 'cache' : 'network'} · depth {resource.depth} · {formatBytes(resource.bytes)}</span>
            <small>SHA-256 {resource.sha256}</small></article>)}</div>{result.diagnostics.map(diagnostic => <small key={diagnostic}>{diagnostic}</small>)}</div>;
}
function formatBytes(value: number): string { if (value < 1024) return `${value} B`; if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`; return `${(value / (1024 * 1024)).toFixed(1)} MiB`; }
function licenseReviewComplete(licenses: UpdateSiteRemoteSnapshotLicenseEntry[], resourceCount: number): boolean {
    return licenses.length === resourceCount && resourceCount > 0 && licenses.every(item => item.virtualPath !== '' && item.author !== ''
        && item.origin !== '' && item.license !== '' && item.obligations !== '' && item.compatible && item.redistributionAllowed);
}
function licenseTemplate(licenses: UpdateSiteRemoteSnapshotLicenseEntry[], resources: UpdateSiteRemoteSnapshotResult['lock']['resources']): string {
    const byPath = new Map((licenses ?? []).map(item => [item.virtualPath, item]));
    return JSON.stringify(resources.map(resource => byPath.get(resource.virtualPath) ?? { virtualPath: resource.virtualPath,
        author: '', origin: resource.url, license: '', obligations: '', compatible: false, redistributionAllowed: false }), null, 2);
}
function message(exception: unknown, fallback: string): string { return exception instanceof Error ? exception.message : fallback; }
