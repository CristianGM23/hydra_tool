import { useEffect, useMemo, useRef, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraFormField, HydraSelect, HydraTooltip } from '../design-system';
import { clearUpdateSiteCache, downloadCapabilityPerformanceHtml, evaluateCapabilityPerformance,
    compareCapabilityPerformanceReports, loadCapabilityPerformanceBudgets, loadCapabilityPerformanceReports, loadUpdateSiteCacheMetrics } from './api';
import type { CapabilityPerformanceBudget, CapabilityPerformanceReport, CapabilityPerformanceSample,
    CapabilityPerformanceComparison, UpdateSiteAnalysisCacheMetrics, UpdateSiteAnalysisPerformance } from './types';

interface Props { performance?: UpdateSiteAnalysisPerformance; }
const EMPTY: UpdateSiteAnalysisCacheMetrics = { entries: 0, hits: 0, misses: 0, writes: 0, maximumEntries: 0 };

export function UpdateSitePerformancePanel({ performance }: Props) {
    const mountedAt = useRef(performanceNow());
    const [metrics, setMetrics] = useState<UpdateSiteAnalysisCacheMetrics>(EMPTY);
    const [budgets, setBudgets] = useState<CapabilityPerformanceBudget[]>([]);
    const [reports, setReports] = useState<CapabilityPerformanceReport[]>([]);
    const [capabilityId, setCapabilityId] = useState('update-site-import-center');
    const [renderMillis, setRenderMillis] = useState(0);
    const [heapBytes, setHeapBytes] = useState(0);
    const [requestCount, setRequestCount] = useState(0);
    const [responseBytes, setResponseBytes] = useState(0);
    const [report, setReport] = useState<CapabilityPerformanceReport | null>(null);
    const [baselineId, setBaselineId] = useState('');
    const [candidateId, setCandidateId] = useState('');
    const [comparison, setComparison] = useState<CapabilityPerformanceComparison | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    async function refresh(): Promise<void> {
        setBusy(true);
        try {
            const [nextMetrics, nextBudgets, nextReports] = await Promise.all([
                loadUpdateSiteCacheMetrics(), loadCapabilityPerformanceBudgets(), loadCapabilityPerformanceReports()
            ]);
            setMetrics(nextMetrics); setBudgets(nextBudgets); setReports(nextReports); setError('');
        } catch (exception) { setError(message(exception, 'Could not load performance metrics')); }
        finally { setBusy(false); }
    }

    useEffect(() => {
        setRenderMillis(Math.max(0, Math.round(performanceNow() - mountedAt.current)));
        const memory = browserHeap(); if (memory > 0) setHeapBytes(memory);
        const resources = resourceSummary(); setRequestCount(resources.requests); setResponseBytes(resources.bytes);
        void refresh();
    }, []);

    async function clear(): Promise<void> {
        setBusy(true);
        try { await clearUpdateSiteCache(); await refresh(); }
        catch (exception) { setError(message(exception, 'Could not clear the analysis cache')); setBusy(false); }
    }

    async function evaluate(): Promise<void> {
        setBusy(true);
        const sample: CapabilityPerformanceSample = { runId: '', capabilityId, capturedAt: new Date().toISOString(),
            renderMillis, heapBytes, requestCount, responseBytes, analysisMillis: performance?.totalMillis ?? 0,
            stageMillis: performance?.stageMillis ?? { 'update-site-analysis': performance?.totalMillis ?? 0 }, chunkBytes: resourceChunks() };
        try { const next = await evaluateCapabilityPerformance(sample); setReport(next); setReports(current => [next, ...current.filter(item => item.reportId !== next.reportId)]); setError(''); }
        catch (exception) { setError(message(exception, 'Could not evaluate the capability budget')); }
        finally { setBusy(false); }
    }

    async function compare(): Promise<void> {
        if (baselineId === '' || candidateId === '') return;
        setBusy(true);
        try { setComparison(await compareCapabilityPerformanceReports(baselineId, candidateId)); setError(''); }
        catch (exception) { setError(message(exception, 'Could not compare performance reports')); }
        finally { setBusy(false); }
    }

    async function downloadHtml(item: CapabilityPerformanceReport): Promise<void> {
        setBusy(true);
        try { const blob = await downloadCapabilityPerformanceHtml(item.reportId); const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${item.reportId}.html`; anchor.click(); URL.revokeObjectURL(url); setError(''); }
        catch (exception) { setError(message(exception, 'Could not export the HTML report')); }
        finally { setBusy(false); }
    }

    const totalReads = metrics.hits + metrics.misses;
    const hitRate = totalReads === 0 ? 0 : (metrics.hits / totalReads) * 100;
    const selectedBudget = useMemo(() => budgets.find(item => item.capabilityId === capabilityId), [budgets, capabilityId]);
    return <section className="update-site-v36-panel">
        <header className="update-site-section-header"><div><h3>Capability performance budgets</h3><p>Compare render time, memory, requests, response volume and staged analysis time across independently loaded HYDRA capabilities.</p></div>
            <HydraTooltip label="About capability performance" title="Performance contract"><p>Measurements are stored as comparable JSON reports. Each report can also be exported as self-contained HTML with violations and actionable chunk/cache recommendations.</p></HydraTooltip></header>
        <div className="update-site-v36-kpis"><article><strong>{performance?.totalMillis ?? 0} ms</strong><span>Last analysis</span></article>
            <article><strong>{performance?.cacheHit ? 'Yes' : 'No'}</strong><span>Analysis cache hit</span></article><article><strong>{metrics.entries}/{metrics.maximumEntries}</strong><span>Cached analyses</span></article>
            <article><strong>{hitRate.toFixed(1)}%</strong><span>Cache hit rate</span></article></div>
        <div className="update-site-performance-budget-form">
            <HydraFormField label="Capability" targetId="performance-capability"><HydraSelect id="performance-capability" ariaLabel="Capability" value={capabilityId} options={budgets.map(item => ({ value:item.capabilityId,label:item.capabilityId,description:'Performance budget registered for this capability.' }))} onChange={setCapabilityId} /></HydraFormField>
            <NumberField id="performance-render" label="Render ms" value={renderMillis} setValue={setRenderMillis} />
            <NumberField id="performance-heap" label="Heap bytes" value={heapBytes} setValue={setHeapBytes} />
            <NumberField id="performance-requests" label="Requests" value={requestCount} setValue={setRequestCount} />
            <NumberField id="performance-response" label="Response bytes" value={responseBytes} setValue={setResponseBytes} />
        </div>
        {selectedBudget !== undefined && <dl className="update-site-performance-details"><div><dt>Render budget</dt><dd>{selectedBudget.maxRenderMillis} ms</dd></div><div><dt>Heap budget</dt><dd>{selectedBudget.maxHeapBytes}</dd></div>
            <div><dt>Request budget</dt><dd>{selectedBudget.maxRequests}</dd></div><div><dt>Response budget</dt><dd>{selectedBudget.maxResponseBytes}</dd></div><div><dt>Analysis budget</dt><dd>{selectedBudget.maxAnalysisMillis} ms</dd></div></dl>}
        <div className="hydra-center-actions"><HydraButton loading={busy} onClick={() => void refresh()}>Refresh metrics</HydraButton><HydraButton variant="primary" loading={busy} onClick={() => void evaluate()}>Evaluate budget</HydraButton>
            <HydraButton variant="danger" loading={busy} onClick={() => void clear()}>Clear analysis cache</HydraButton></div>
        {report !== null && <PerformanceResult report={report} onDownload={() => void downloadHtml(report)} busy={busy} />}
        {reports.length > 1 && <section className="update-site-performance-comparison"><h4>Regression comparison</h4><div className="update-site-performance-budget-form">
            <HydraFormField label="Baseline" targetId="performance-baseline"><HydraSelect id="performance-baseline" ariaLabel="Baseline" value={baselineId} placeholder="Choose report" options={reports.map(item => ({ value:item.reportId,label:`${item.sample.capabilityId} · ${item.reportId}`,description:'Use this execution as the reference for the regression comparison.' }))} onChange={setBaselineId} /></HydraFormField>
            <HydraFormField label="Candidate" targetId="performance-candidate"><HydraSelect id="performance-candidate" ariaLabel="Candidate" value={candidateId} placeholder="Choose report" options={reports.map(item => ({ value:item.reportId,label:`${item.sample.capabilityId} · ${item.reportId}`,description:'Compare this execution against the selected baseline.' }))} onChange={setCandidateId} /></HydraFormField>
        </div><HydraButton disabled={baselineId === '' || candidateId === '' || baselineId === candidateId} loading={busy} onClick={() => void compare()}>Compare reports</HydraButton>
        {comparison !== null && <div className={`update-site-adapter-validation ${comparison.regression ? 'invalid' : 'valid'}`}><strong>{comparison.regression ? 'Regression detected' : 'No material regression'}</strong><small>Render {comparison.renderDelta} ms · Heap {comparison.heapDelta} B · Analysis {comparison.analysisDelta} ms</small>{comparison.diagnostics.map(item => <p key={item}>{item}</p>)}</div>}</section>}
        {reports.length > 0 && <section className="update-site-performance-history"><h4>Comparable executions</h4>{reports.slice(0, 8).map(item => <article key={item.reportId}><div><strong>{item.sample.capabilityId}</strong><code>{item.reportId}</code></div><span>{item.evaluation.passed ? 'Passed' : `${item.evaluation.violations.length} violation(s)`}</span><HydraButton size="small" onClick={() => void downloadHtml(item)}>HTML</HydraButton></article>)}</section>}
        {error !== '' && <HydraActionableErrorNotice className="error-message" message={error} context="update-site import" />}
    </section>;
}

function NumberField({ id, label, value, setValue }: { id: string; label: string; value: number; setValue: (value: number) => void }) {
    return <HydraFormField label={label} targetId={id}><input id={id} type="number" min="0" value={value} onChange={event => setValue(Math.max(0, Number(event.target.value) || 0))} /></HydraFormField>;
}
function PerformanceResult({ report, onDownload, busy }: { report: CapabilityPerformanceReport; onDownload: () => void; busy: boolean }) {
    return <div className={`update-site-adapter-validation ${report.evaluation.passed ? 'valid' : 'invalid'}`}><strong>{report.evaluation.passed ? 'Capability is within budget' : 'Capability exceeds its budget'}</strong><small>Report {report.reportId}</small>
        {report.evaluation.violations.map(item => <p key={item}>{item}</p>)}{report.evaluation.recommendations.map(item => <p key={item}>{item}</p>)}<HydraButton size="small" loading={busy} onClick={onDownload}>Download HTML report</HydraButton></div>;
}
function performanceNow(): number { return typeof performance === 'undefined' ? 0 : performance.now(); }
function browserHeap(): number { if (typeof performance === 'undefined') return 0; const extended = performance as Performance & { memory?: { usedJSHeapSize?: number } }; return Math.max(0, extended.memory?.usedJSHeapSize ?? 0); }
function resourceSummary(): { requests: number; bytes: number } { if (typeof performance === 'undefined') return { requests: 0, bytes: 0 }; const items = performance.getEntriesByType('resource') as PerformanceResourceTiming[]; return { requests: items.length, bytes: items.reduce((sum, item) => sum + Math.max(0, item.transferSize || item.encodedBodySize || 0), 0) }; }
function resourceChunks(): Record<string, number> { if (typeof performance === 'undefined') return {}; const result: Record<string, number> = {}; (performance.getEntriesByType('resource') as PerformanceResourceTiming[]).filter(item => /\.(js|css)(\?|$)/.test(item.name)).forEach(item => { const name = item.name.split('/').pop()?.split('?')[0] ?? item.name; result[name] = Math.max(result[name] ?? 0, item.transferSize || item.encodedBodySize || 0); }); return result; }
function message(exception: unknown, fallback: string): string { return exception instanceof Error ? exception.message : fallback; }
