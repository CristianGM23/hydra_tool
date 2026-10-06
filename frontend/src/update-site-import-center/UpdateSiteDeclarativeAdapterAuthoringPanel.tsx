import { useEffect, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraCheckbox, HydraFormField, HydraSelect, HydraTooltip } from '../design-system';
import { deleteDeclarativeAdapter, downloadDeclarativeAdapter, importDeclarativeAdapter,
    downloadDeclarativeAdapterVersion, loadDeclarativeAdapterDocuments, loadDeclarativeAdapterVersions,
    previewDeclarativeAdapter, setDeclarativeAdapterEnabled, testDeclarativeAdapter } from './api';
import type { UpdateSiteAdapterFixtureResult, UpdateSiteConfidence, UpdateSiteDeclarativeAdapterDocument,
    UpdateSiteDeclarativeAdapterEntry, UpdateSiteDeclarativeAdapterImportResult,
    UpdateSiteDeclarativeAdapterValidation, UpdateSiteDeclarativeAdapterVersionEntry,
    UpdateSiteSemanticKind } from './types';

interface Props { onImported: () => Promise<void> | void; }
type Busy = 'PREVIEW' | 'IMPORT' | 'TEST' | 'REFRESH' | 'ACTIVATE' | 'DELETE' | 'EXPORT' | 'VERSIONS' | '';

const DEFAULT_DOCUMENT: UpdateSiteDeclarativeAdapterDocument = {
    schemaVersion: '1.0',
    adapter: { id: 'org.example:custom-extension', label: 'Custom Eclipse extension',
        extensionPointTokens: ['org.example.custom'], elementNames: ['definition'], semanticKind: 'RUNTIME_EXTENSION',
        neutralTarget: 'runtimeExtension', purpose: 'Recover a declarative custom extension without executing plug-in code.',
        idAttributes: ['id', 'name'], implementationAttributes: ['class'], confidence: 'DERIVED', enabled: true },
    annotations: { version: '1.0.0', author: '', source: '' }
};
const SEMANTIC_KINDS: UpdateSiteSemanticKind[] = ['COMMAND', 'HANDLER', 'MENU', 'EDITOR', 'VIEW', 'PROPERTY_PAGE',
    'PREFERENCE_PAGE', 'FORM', 'VALIDATION', 'FILE_EXTENSION', 'GENERATED_ITEM_PROVIDER', 'RUNTIME_EXTENSION', 'UNREPRESENTABLE'];
const CONFIDENCES: UpdateSiteConfidence[] = ['EXACT', 'RECOVERED', 'DERIVED', 'INFERRED'];
const ADAPTER_EXAMPLES: Record<string, UpdateSiteDeclarativeAdapterDocument> = {
    runtime: DEFAULT_DOCUMENT,
    palette: { ...DEFAULT_DOCUMENT, adapter: { ...DEFAULT_DOCUMENT.adapter, id: 'org.example:palette-extension',
        label: 'Palette extension', extensionPointTokens: ['palette', 'tooling'], elementNames: ['palette', 'group', 'tool'],
        semanticKind: 'FORM', neutralTarget: 'paletteDraft', purpose: 'Recover palette groups and tools as a neutral HYDRA palette draft.',
        idAttributes: ['id', 'name', 'label'], implementationAttributes: ['class', 'provider'] } },
    validation: { ...DEFAULT_DOCUMENT, adapter: { ...DEFAULT_DOCUMENT.adapter, id: 'org.example:validation-extension',
        label: 'Validation extension', extensionPointTokens: ['validation', 'constraint'], elementNames: ['validator', 'constraint'],
        semanticKind: 'VALIDATION', neutralTarget: 'validationDraft', purpose: 'Recover validation declarations and their identifiers without loading validators.',
        idAttributes: ['id', 'name'], implementationAttributes: ['class', 'validator'] } }
};

export function UpdateSiteDeclarativeAdapterAuthoringPanel({ onImported }: Props) {
    const [example, setExample] = useState('runtime');
    const [document, setDocument] = useState<UpdateSiteDeclarativeAdapterDocument>(DEFAULT_DOCUMENT);
    const [content, setContent] = useState(pretty(DEFAULT_DOCUMENT));
    const [jsonOutOfSync, setJsonOutOfSync] = useState(false);
    const [overwrite, setOverwrite] = useState(false);
    const [busy, setBusy] = useState<Busy>('');
    const [validation, setValidation] = useState<UpdateSiteDeclarativeAdapterValidation | null>(null);
    const [result, setResult] = useState<UpdateSiteDeclarativeAdapterImportResult | null>(null);
    const [fixtureExtension, setFixtureExtension] = useState('org.example.custom');
    const [fixtureElement, setFixtureElement] = useState('definition');
    const [fixtureAttributes, setFixtureAttributes] = useState('{"id":"example","class":"org.example.Handler"}');
    const [fixtureResult, setFixtureResult] = useState<UpdateSiteAdapterFixtureResult | null>(null);
    const [entries, setEntries] = useState<UpdateSiteDeclarativeAdapterEntry[]>([]);
    const [versions, setVersions] = useState<Record<string, UpdateSiteDeclarativeAdapterVersionEntry[]>>({});
    const [error, setError] = useState('');

    async function refresh(): Promise<void> {
        setBusy('REFRESH');
        try { setEntries(await loadDeclarativeAdapterDocuments()); setError(''); }
        catch (exception) { setError(message(exception, 'Could not list declarative adapters')); }
        finally { setBusy(''); }
    }
    useEffect(() => {
        let active = true;
        void loadDeclarativeAdapterDocuments().then(items => { if (active) setEntries(items); })
            .catch(exception => { if (active) setError(message(exception, 'Could not list declarative adapters')); });
        return () => { active = false; };
    }, []);

    function selectExample(value: string): void {
        const next = ADAPTER_EXAMPLES[value] ?? DEFAULT_DOCUMENT;
        setExample(value); setDocument(next); setContent(pretty(next)); setJsonOutOfSync(false);
        setValidation(null); setResult(null); setFixtureResult(null); setError('');
        setFixtureExtension(next.adapter.extensionPointTokens[0] ?? '');
        setFixtureElement(next.adapter.elementNames[0] ?? '');
    }

    function updateAdapter<K extends keyof UpdateSiteDeclarativeAdapterDocument['adapter']>(key: K,
            value: UpdateSiteDeclarativeAdapterDocument['adapter'][K]): void {
        const next = { ...document, adapter: { ...document.adapter, [key]: value } };
        setDocument(next); setContent(pretty(next)); setJsonOutOfSync(false); setValidation(null); setFixtureResult(null);
    }
    function updateList(key: 'extensionPointTokens' | 'elementNames' | 'idAttributes' | 'implementationAttributes', value: string): void {
        updateAdapter(key, value.split(',').map(item => item.trim()).filter(Boolean));
    }
    function updateAnnotation(key: string, value: string): void {
        const next = { ...document, annotations: { ...document.annotations, [key]: value } };
        setDocument(next); setContent(pretty(next)); setJsonOutOfSync(false); setValidation(null); setFixtureResult(null);
    }
    function editJson(value: string): void {
        setContent(value); setValidation(null); setResult(null); setFixtureResult(null);
        try { const parsed = JSON.parse(value) as UpdateSiteDeclarativeAdapterDocument; setDocument(parsed); setJsonOutOfSync(false); }
        catch { setJsonOutOfSync(true); }
    }
    async function preview(): Promise<void> {
        setBusy('PREVIEW'); try { setValidation(await previewDeclarativeAdapter(content)); setResult(null); setError(''); }
        catch (exception) { setError(message(exception, 'Could not validate the adapter')); } finally { setBusy(''); }
    }
    async function persist(): Promise<void> {
        setBusy('IMPORT'); try { const imported = await importDeclarativeAdapter(content, overwrite); setResult(imported); setValidation(imported.validation); setError('');
            if (imported.persisted) { await onImported(); await refresh(); } }
        catch (exception) { setError(message(exception, 'Could not import the adapter')); } finally { setBusy(''); }
    }
    async function testFixture(): Promise<void> {
        let attributes: Record<string, string>;
        try { attributes = JSON.parse(fixtureAttributes) as Record<string, string>; }
        catch { setError('Fixture attributes must be a JSON object.'); return; }
        setBusy('TEST'); try { setFixtureResult(await testDeclarativeAdapter(content, { extensionPoint: fixtureExtension, elementName: fixtureElement, attributes })); setError(''); }
        catch (exception) { setError(message(exception, 'Could not test the adapter fixture')); } finally { setBusy(''); }
    }
    async function toggle(entry: UpdateSiteDeclarativeAdapterEntry): Promise<void> {
        setBusy('ACTIVATE'); try { await setDeclarativeAdapterEnabled(entry.document.adapter.id, !entry.document.adapter.enabled); await refresh(); await onImported(); setError(''); }
        catch (exception) { setError(message(exception, 'Could not change adapter activation')); } finally { setBusy(''); }
    }
    async function remove(entry: UpdateSiteDeclarativeAdapterEntry): Promise<void> {
        setBusy('DELETE'); try { await deleteDeclarativeAdapter(entry.document.adapter.id); await refresh(); await onImported(); setError(''); }
        catch (exception) { setError(message(exception, 'Could not delete the adapter')); } finally { setBusy(''); }
    }
    async function exportEntry(entry: UpdateSiteDeclarativeAdapterEntry): Promise<void> {
        setBusy('EXPORT'); try { const blob = await downloadDeclarativeAdapter(entry.document.adapter.id); const url = URL.createObjectURL(blob); const anchor = window.document.createElement('a');
            anchor.href = url; anchor.download = entry.fileName; anchor.click(); URL.revokeObjectURL(url); setError(''); }
        catch (exception) { setError(message(exception, 'Could not export the adapter')); } finally { setBusy(''); }
    }

    async function showVersions(entry: UpdateSiteDeclarativeAdapterEntry): Promise<void> {
        setBusy('VERSIONS'); try { const loaded = await loadDeclarativeAdapterVersions(entry.document.adapter.id); setVersions(current => ({ ...current, [entry.document.adapter.id]: loaded })); setError(''); }
        catch (exception) { setError(message(exception, 'Could not load adapter versions')); } finally { setBusy(''); }
    }
    async function exportVersion(entry: UpdateSiteDeclarativeAdapterEntry, version: string): Promise<void> {
        setBusy('EXPORT'); try { const blob = await downloadDeclarativeAdapterVersion(entry.document.adapter.id, version); const url = URL.createObjectURL(blob); const anchor = window.document.createElement('a');
            anchor.href = url; anchor.download = `${entry.document.adapter.id.replace(':', '-')}-${version}.hydra-adapter.json`; anchor.click(); URL.revokeObjectURL(url); setError(''); }
        catch (exception) { setError(message(exception, 'Could not export the adapter version')); } finally { setBusy(''); }
    }

    const formValid = useMemo(() => document.adapter.id.trim() !== '' && document.adapter.label.trim() !== '', [document]);
    return <section className="update-site-v36-panel">
        <header className="update-site-section-header"><div><h3>Visual declarative adapter authoring</h3><p>The guided form and JSON editor stay synchronized. Fixtures explain why a rule matches before it is registered.</p></div>
            <HydraTooltip label="About visual adapter authoring" title="Safe adapter contract"><p>Documents remain data-only. HYDRA validates schema version, namespaced IDs, field limits and token syntax, and never loads scripts or executable classes.</p></HydraTooltip></header>
        <div className="update-site-adapter-authoring-grid">
            <section className="update-site-adapter-form"><h4>Guided form</h4>
                <HydraFormField label="Example contract" targetId="adapter-example" help="Start from a safe data-only example and then refine the synchronized form or JSON."><HydraSelect id="adapter-example" ariaLabel="Example contract" value={example} options={[{value:'runtime',label:'Runtime extension',description:'Start from an extension that contributes runtime behavior.'},{value:'palette',label:'Palette and tools',description:'Start from a visual palette/tool contribution.'},{value:'validation',label:'Validation declaration',description:'Start from a validation-oriented declaration.'}]} onChange={selectExample} /></HydraFormField>
                <HydraFormField label="Namespaced ID" targetId="adapter-id" required><input id="adapter-id" value={document.adapter.id} onChange={event => updateAdapter('id', event.target.value)} /></HydraFormField>
                <HydraFormField label="Label" targetId="adapter-label" required><input id="adapter-label" value={document.adapter.label} onChange={event => updateAdapter('label', event.target.value)} /></HydraFormField>
                <HydraFormField label="Document version" targetId="adapter-version" required help="Semantic version stored in annotations.version and retained in the local version history."><input id="adapter-version" value={document.annotations.version ?? '1.0.0'} onChange={event => updateAnnotation('version', event.target.value)} /></HydraFormField>
                <HydraFormField label="Extension-point tokens" targetId="adapter-tokens" help="Comma-separated substrings matched against the Eclipse extension point."><input id="adapter-tokens" value={document.adapter.extensionPointTokens.join(', ')} onChange={event => updateList('extensionPointTokens', event.target.value)} /></HydraFormField>
                <HydraFormField label="XML element names" targetId="adapter-elements"><input id="adapter-elements" value={document.adapter.elementNames.join(', ')} onChange={event => updateList('elementNames', event.target.value)} /></HydraFormField>
                <HydraFormField label="Semantic kind" targetId="adapter-kind"><HydraSelect id="adapter-kind" ariaLabel="Semantic kind" value={document.adapter.semanticKind} options={SEMANTIC_KINDS.map(kind => ({ value:kind,label:kind,description:'Semantic role assigned to elements recovered by this adapter.' }))} onChange={value => updateAdapter('semanticKind', value as UpdateSiteSemanticKind)} /></HydraFormField>
                <HydraFormField label="Neutral target" targetId="adapter-target"><input id="adapter-target" value={document.adapter.neutralTarget} onChange={event => updateAdapter('neutralTarget', event.target.value)} /></HydraFormField>
                <HydraFormField label="Purpose" targetId="adapter-purpose"><textarea id="adapter-purpose" value={document.adapter.purpose} onChange={event => updateAdapter('purpose', event.target.value)} /></HydraFormField>
                <HydraFormField label="ID attributes" targetId="adapter-id-attributes"><input id="adapter-id-attributes" value={document.adapter.idAttributes.join(', ')} onChange={event => updateList('idAttributes', event.target.value)} /></HydraFormField>
                <HydraFormField label="Implementation attributes" targetId="adapter-implementation-attributes"><input id="adapter-implementation-attributes" value={document.adapter.implementationAttributes.join(', ')} onChange={event => updateList('implementationAttributes', event.target.value)} /></HydraFormField>
                <HydraFormField label="Confidence" targetId="adapter-confidence"><HydraSelect id="adapter-confidence" ariaLabel="Confidence" value={document.adapter.confidence} options={CONFIDENCES.map(value => ({ value,label:value,description:'Confidence assigned to evidence produced by this adapter.' }))} onChange={value => updateAdapter('confidence', value as UpdateSiteConfidence)} /></HydraFormField>
                <HydraCheckbox label="Adapter enabled" checked={document.adapter.enabled} description="Disabled documents remain versioned but do not match extension declarations." onChange={event => updateAdapter('enabled', event.target.checked)} />
            </section>
            <section><h4>JSON contract</h4><HydraFormField label="Adapter document" targetId="declarative-adapter-json" required help="Changes in valid JSON are reflected in the form.">
                <textarea id="declarative-adapter-json" className="update-site-adapter-editor" value={content} onChange={event => editJson(event.target.value)} spellCheck={false} />
            </HydraFormField>{jsonOutOfSync && <p className="error-message">The JSON is incomplete or invalid; the guided form still shows the last valid document.</p>}
                <HydraCheckbox label="Replace an existing declarative adapter" checked={overwrite} description="Built-in server adapters can never be replaced." onChange={event => setOverwrite(event.target.checked)} />
                <div className="hydra-center-actions"><HydraButton loading={busy === 'PREVIEW'} onClick={() => void preview()}>Validate adapter</HydraButton>
                    <HydraButton variant="primary" disabled={!formValid || jsonOutOfSync} loading={busy === 'IMPORT'} onClick={() => void persist()}>Save adapter version</HydraButton></div>
                {validation !== null && <ValidationResult validation={validation} />}{result?.persisted && <div className="status-message">Saved <strong>{result.fileName}</strong>{result.replaced ? ' and replaced the prior version.' : '.'}</div>}
            </section>
        </div>
        <section className="update-site-adapter-fixture"><h4>Fixture test</h4><div className="update-site-v36-form-grid">
            <HydraFormField label="Extension point" targetId="fixture-extension"><input id="fixture-extension" value={fixtureExtension} onChange={event => setFixtureExtension(event.target.value)} /></HydraFormField>
            <HydraFormField label="Element name" targetId="fixture-element"><input id="fixture-element" value={fixtureElement} onChange={event => setFixtureElement(event.target.value)} /></HydraFormField>
            <HydraFormField label="Attributes JSON" targetId="fixture-attributes"><input id="fixture-attributes" value={fixtureAttributes} onChange={event => setFixtureAttributes(event.target.value)} /></HydraFormField></div>
            <HydraButton loading={busy === 'TEST'} onClick={() => void testFixture()}>Test fixture</HydraButton>
            {fixtureResult !== null && <div className={`update-site-adapter-validation ${fixtureResult.matched ? 'valid' : 'invalid'}`}><strong>{fixtureResult.matched ? 'Fixture matches' : 'Fixture does not match'}</strong>
                {fixtureResult.resolvedId !== '' && <small>Resolved ID: {fixtureResult.resolvedId}</small>}{fixtureResult.implementation !== '' && <small>Implementation: {fixtureResult.implementation}</small>}
                {[...fixtureResult.errors, ...fixtureResult.explanations].map(item => <p key={item}>{item}</p>)}</div>}
        </section>
        <section><header className="update-site-section-header"><div><h4>Persisted adapter versions</h4><p>Activate, disable, export or remove user-authored documents.</p></div><HydraButton loading={busy === 'REFRESH'} onClick={() => void refresh()}>Refresh</HydraButton></header>
            <div className="update-site-extension-adapter-grid">{entries.map(entry => <article key={entry.document.adapter.id}><header><div><strong>{entry.document.adapter.label}</strong><code>{entry.document.adapter.id}</code></div><span>{entry.document.adapter.enabled ? 'Active' : 'Disabled'} · v{entry.version}</span></header>
                <p>{entry.document.adapter.purpose}</p><small>Fingerprint {entry.fingerprint}</small><div className="hydra-center-actions"><HydraButton size="small" loading={busy === 'ACTIVATE'} onClick={() => void toggle(entry)}>{entry.document.adapter.enabled ? 'Disable' : 'Activate'}</HydraButton>
                    <HydraButton size="small" loading={busy === 'VERSIONS'} onClick={() => void showVersions(entry)}>Versions ({entry.availableVersions.length})</HydraButton>
                    <HydraButton size="small" loading={busy === 'EXPORT'} onClick={() => void exportEntry(entry)}>Export current</HydraButton><HydraButton size="small" variant="danger" loading={busy === 'DELETE'} onClick={() => void remove(entry)}>Delete</HydraButton></div>
                {(versions[entry.document.adapter.id]?.length ?? 0) > 0 && <div className="update-site-adapter-versions">{versions[entry.document.adapter.id].map(item => <div key={item.version}><span>v{item.version}{item.current ? ' · current' : ''}</span><code>{item.fingerprint.slice(0, 16)}</code><HydraButton size="small" onClick={() => void exportVersion(entry, item.version)}>Export</HydraButton></div>)}</div>}</article>)}</div>
        </section>
        {error !== '' && <HydraActionableErrorNotice className="error-message" message={error} context="update-site import" />}
    </section>;
}
function ValidationResult({ validation }: { validation: UpdateSiteDeclarativeAdapterValidation }) { return <div className={`update-site-adapter-validation ${validation.valid ? 'valid' : 'invalid'}`}><strong>{validation.valid ? 'Adapter contract is valid' : 'Adapter contract is invalid'}</strong>{validation.fingerprint !== '' && <small>Fingerprint: {validation.fingerprint}</small>}{validation.errors.map(item => <p key={item}>{item}</p>)}{validation.warnings.map(item => <p key={item}>{item}</p>)}</div>; }
function pretty(value: UpdateSiteDeclarativeAdapterDocument): string { return JSON.stringify(value, null, 2); }
function message(exception: unknown, fallback: string): string { return exception instanceof Error ? exception.message : fallback; }
