import { useMemo, useState, type ChangeEvent } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraFormField, HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { applyCapabilityLayerTransaction, previewCapabilityLayerTransaction, rollbackCapabilityLayerTransaction } from './api';
import type { CapabilityLayerRollbackResult, CapabilityLayerTransactionPreview, CapabilityLayerTransactionRequest, CapabilityLayerTransactionResult } from './types';

interface Props { metamodelId: string; }
type Busy = 'PREVIEW' | 'APPLY' | 'ROLLBACK' | '';
const MAX_BROWSER_PACKAGE_BYTES = 16 * 1024 * 1024;
const LAYER_ORDER = ['METAMODEL', 'EDITOR_DEFINITION', 'REPRESENTATION_DEFINITION', 'PALETTE_DEFINITION',
    'FORM_DEFINITION', 'EXPRESSION_MODEL', 'VALIDATION_MODEL', 'ACTION_MODEL', 'OPERATION_BEHAVIOR_MODEL',
    'EVENT_DEFINITION_MODEL', 'EVENT_BINDING_MODEL', 'EVENT_FLOW_MODEL', 'GAMIFICATION_DEFINITION_MODEL',
    'GAMIFICATION_BINDING_MODEL', 'INTEGRATION_PROFILE'];

export function CapabilityTransactionPanel({ metamodelId }: Props) {
    const { t } = useHydraLanguage();
    const [packageJson, setPackageJson] = useState(() => template(metamodelId));
    const [etagJson, setEtagJson] = useState('{}');
    const [sourceName, setSourceName] = useState(t('Built-in example package'));
    const [preview, setPreview] = useState<CapabilityLayerTransactionPreview | null>(null);
    const [result, setResult] = useState<CapabilityLayerTransactionResult | null>(null);
    const [rollback, setRollback] = useState<CapabilityLayerRollbackResult | null>(null);
    const [rollbackSelection, setRollbackSelection] = useState<string[]>([]);
    const [busy, setBusy] = useState<Busy>('');
    const [error, setError] = useState('');

    const parsedPackage = useMemo(() => parseObject(packageJson), [packageJson]);
    const parsedEtags = useMemo(() => parseStringMap(etagJson), [etagJson]);
    const packageSummary = useMemo(() => summarizePackage(parsedPackage, t), [parsedPackage, t]);
    const request = useMemo<CapabilityLayerTransactionRequest | null>(() => parsedPackage === null || parsedEtags === null ? null : ({
        capabilityPackage: parsedPackage, targetMetamodelId: metamodelId, expectedEtags: parsedEtags,
        layerOrder: LAYER_ORDER, persist: false,
    }), [metamodelId, parsedEtags, parsedPackage]);

    async function choosePackage(event: ChangeEvent<HTMLInputElement>): Promise<void> {
        const file = event.target.files?.[0];
        event.target.value = '';
        if (file === undefined) return;
        if (file.size > MAX_BROWSER_PACKAGE_BYTES) { setError(t('The selected capability package is too large for the browser editor.')); return; }
        try {
            const text = await file.text();
            if (parseObject(text) === null) throw new Error(t('The selected file is not a valid capability package JSON document.'));
            setPackageJson(text); setEtagJson('{}'); setSourceName(file.name); resetPreview(); setError('');
        } catch (exception) { setError(message(exception, t('Could not read the selected capability package.'))); }
    }

    async function inspect(): Promise<void> {
        if (request === null) { setError(t('The capability package must be valid JSON.')); return; }
        setBusy('PREVIEW');
        try {
            let nextRequest = request;
            let next = await previewCapabilityLayerTransaction(nextRequest);
            const automaticallyGuarded = Object.keys(nextRequest.expectedEtags).length === 0
                ? Object.fromEntries(next.items.filter(item => item.currentEtag !== '').map(item => [item.documentPath, item.currentEtag])) : {};
            if (Object.keys(automaticallyGuarded).length > 0) {
                nextRequest = { ...nextRequest, expectedEtags: automaticallyGuarded };
                setEtagJson(JSON.stringify(automaticallyGuarded, null, 2));
                next = await previewCapabilityLayerTransaction(nextRequest);
            }
            setPreview(next); setResult(null); setRollback(null); setError('');
        } catch (exception) { setError(message(exception, t('Could not check the portable layers.'))); }
        finally { setBusy(''); }
    }

    async function apply(): Promise<void> {
        if (request === null) { setError(t('The capability package must be valid JSON.')); return; }
        setBusy('APPLY');
        try {
            const next = await applyCapabilityLayerTransaction(request);
            setResult(next); setPreview(next.preview); setRollbackSelection(next.preview.items.map(item => item.documentPath)); setRollback(null); setError('');
        } catch (exception) { setError(message(exception, t('Could not apply the portable layers.'))); }
        finally { setBusy(''); }
    }

    async function restore(): Promise<void> {
        if (result === null || result.transactionId === '') return;
        setBusy('ROLLBACK');
        try { setRollback(await rollbackCapabilityLayerTransaction(result.transactionId, rollbackSelection, result.appliedEtags)); setError(''); }
        catch (exception) { setError(message(exception, t('Could not undo the applied layers.'))); }
        finally { setBusy(''); }
    }

    function resetPreview(): void { setPreview(null); setResult(null); setRollback(null); setRollbackSelection([]); }

    return <section className="capability-transaction-panel capability-portable-layers">
        <header><div><p>{t('Portable layers')}</p><h2>{t('Move a complete capability safely')}</h2><span>{t('Choose one capability package. HYDRA checks every affected layer, captures current versions automatically, applies them in dependency order and keeps a guarded undo journal.')}</span></div>
            <HydraTooltip label={t('About portable layers')} title={t('Safe multi-layer change')}><p>{t('Nothing is written during the check. Existing layers receive optimistic-concurrency guards automatically, and the final apply verifies them again before each atomic replacement.')}</p></HydraTooltip></header>

        <div className="capability-package-source">
            <div><strong>{t('Capability package')}</strong><span>{sourceName}</span><small>{packageSummary}</small></div>
            <label className="capability-package-file-button"><input accept=".json,application/json" type="file" onChange={event => void choosePackage(event)} />{t('Choose package file')}</label>
        </div>

        <div className="capability-portable-explainer" role="note">
            <strong>{t('What HYDRA will do')}</strong>
            <span>{t('1. Inspect the package and destination. 2. Protect existing layers with their current versions. 3. Apply only when the complete set is safe. 4. Keep selective undo available for the applied layers.')}</span>
        </div>

        <div className="hydra-center-actions">
            <HydraButton loading={busy === 'PREVIEW'} onClick={() => void inspect()}>{t('Check changes')}</HydraButton>
            <HydraButton variant="primary" disabled={preview?.applicable !== true} loading={busy === 'APPLY'} onClick={() => void apply()}>{t('Apply portable layers')}</HydraButton>
            <HydraButton variant="danger" disabled={result?.rollbackAvailable !== true || rollback?.journalClosed === true || rollbackSelection.length === 0} loading={busy === 'ROLLBACK'} onClick={() => void restore()}>{t('Undo applied layers')}</HydraButton>
        </div>

        {error !== '' && <HydraActionableErrorNotice message={error} context="portable layers" onDismiss={() => setError('')} />}
        {preview !== null && <div className={`capability-transaction-preview ${preview.applicable ? 'valid' : 'invalid'}`}>
            <header><strong>{preview.applicable ? t('All layers are ready to apply') : t('Some layers need attention')}</strong><span>{preview.items.length} {t('documents')} · {preview.totalBytes} {t('bytes')}</span></header>
            {[...preview.diagnostics, ...preview.items.flatMap(item => item.diagnostics.map(value => `${item.documentPath}: ${value}`))].map(value => <p key={value}>{t(value)}</p>)}
            <div>{preview.items.map(item => <article key={item.documentPath}><div><strong>{item.order + 1}. {t(item.artifactKind)}</strong><code>{item.documentPath}</code></div><span>{item.action === 'CREATE' ? t('New layer') : t('Replace safely')} · {item.applicable ? t('ready') : t('blocked')}</span></article>)}</div>
        </div>}
        {result?.persisted && <><div className="status-message">{t('Portable layers applied successfully.')} <strong>{Object.keys(result.appliedEtags).length}</strong> {t('layers have guarded undo available.')}</div>
            <fieldset className="capability-transaction-rollback-selection"><legend>{t('Choose layers to undo')}</legend>
                {result.preview.items.map(item => <label key={item.documentPath}><input type="checkbox" checked={rollbackSelection.includes(item.documentPath)} onChange={event => setRollbackSelection(current => event.target.checked ? [...new Set([...current, item.documentPath])] : current.filter(path => path !== item.documentPath))} />
                    <span>{t(item.artifactKind)}</span><code>{item.documentPath}</code></label>)}</fieldset></>}
        {rollback !== null && <div className="status-message">{t('Restored')} {rollback.restoredDocuments.length} {t('layer(s)')}; {rollback.skippedDocuments.length} {t('skipped')}. {rollback.journalClosed ? t('Undo journal closed.') : t('Undo remains available for the other layers.')}</div>}

        <details className="capability-transaction-advanced">
            <summary>{t('Advanced package and concurrency details')}</summary>
            <p>{t('These fields are optional expert controls. The normal flow reads a package file and captures replacement guards automatically.')}</p>
            <div className="capability-transaction-editors">
                <HydraFormField label={t('Capability package JSON')} targetId="capability-package-json" required help={t('HYDRA package document used by the portable-layer service.')}>
                    <textarea id="capability-package-json" value={packageJson} onChange={event => { setPackageJson(event.target.value); setSourceName(t('Edited package JSON')); resetPreview(); }} spellCheck={false} />
                </HydraFormField>
                <HydraFormField label={t('Concurrency guards')} targetId="capability-package-etags" help={t('HYDRA fills these current layer versions during Check changes. Experts may review or replace them before applying.') }>
                    <textarea id="capability-package-etags" value={etagJson} onChange={event => { setEtagJson(event.target.value); resetPreview(); }} spellCheck={false} />
                </HydraFormField>
            </div>
        </details>
    </section>;
}

function parseObject(value: string): Record<string, unknown> | null { try { const parsed=JSON.parse(value) as unknown; return parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Record<string, unknown> : null; } catch { return null; } }
function parseStringMap(value: string): Record<string, string> | null { try { const parsed=JSON.parse(value) as unknown; if(parsed===null||typeof parsed!=='object'||Array.isArray(parsed)) return null; const entries=Object.entries(parsed as Record<string, unknown>); if(entries.some(([,item])=>typeof item!=='string')) return null; return Object.fromEntries(entries) as Record<string,string>; } catch { return null; } }
function summarizePackage(value: Record<string, unknown> | null, t: (value: string) => string): string { if(value===null) return t('Invalid JSON'); const manifest=value.manifest as Record<string, unknown>|undefined; const docs=value.canonicalDocuments as Record<string, unknown>|undefined; const name=typeof manifest?.name==='string'&&manifest.name.trim()!==''?manifest.name:t('Capability package'); return `${name} · ${docs===undefined?0:Object.keys(docs).length} ${t('layer document(s)')}`; }
function template(metamodelId: string): string { const id = metamodelId === '' ? 'example' : metamodelId; return JSON.stringify({ manifest: { schemaVersion: '1.0', id: `${id}-portable-layers`, name: `${id} portable layers`, packageVersion: '1.0.0', metamodelId: id, metamodelOrigin: { kind: 'OWNED', technology: null, sourceUri: '', sourceRevision: '', sourceChecksum: '', importedAt: '', synchronizedAt: '', elementMappings: {}, annotations: {} }, capabilities: [{ id: 'metamodel', kind: 'METAMODEL', documentId: `metamodels/${id}.metamodel.json`, schemaVersion: '2.1', required: true, compiled: false, annotations: {} }], dependencies: [], compatibility: [], checksums: {}, annotations: {} }, canonicalDocuments: { [`metamodels/${id}.metamodel.json`]: { schemaVersion: '2.1', id, name: id, classes: [], enums: [], dataTypes: [], relations: [], annotations: {} } }, resources: {} }, null, 2); }
function message(exception: unknown, fallback: string): string { return exception instanceof Error ? exception.message : fallback; }
