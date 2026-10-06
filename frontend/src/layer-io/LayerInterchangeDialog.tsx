import { HydraButton } from '../design-system/HydraButton';
import { useEffect, useMemo, useRef, useState } from 'react';
import { HydraActionableErrorNotice, HydraGuidedWorkflow, HydraOperationProgress, HydraTooltip, HydraWindowControls } from '../design-system';
import { explainInputError } from '../api/inputErrorExplanation';
import { analyzeMetamodelImport, importAndSaveMetamodel, importPreparedMetamodel } from '../metamodel-designer/importApi';
import type { MetamodelImportOptions, MetamodelImportPreview } from '../metamodel-designer/importTypes';
import type { MetaModel } from '../metamodel-designer/types';
import { loadRemoteLayerArtifact, loadRemoteStorageProfiles, RemoteStorageProfileFields, saveRemoteLayerArtifact } from '../remote-storage';
import type { RemoteStorageProfileDescriptor } from '../remote-storage';
import { compareAssociatedMetamodel } from './metamodelAssociation';

import type {
    LayerExportArtifact,
    LayerImportAnalysis,
    LayerImportAnalysisRequest,
    LayerImportRequest,
    LayerImportResult,
    LayerInterchangeFormat,
    LayerMergeStrategy
} from './layerInterchangeTypes';

interface Props<TFormat extends string> {
    mode: 'IMPORT' | 'EXPORT';
    layerName: string;
    description: string;
    formats: readonly LayerInterchangeFormat<TFormat>[];
    defaultFormat: TFormat;
    currentMetamodel?: MetaModel;
    onClose: () => void;
    onAnalyzeImport?: (request: LayerImportAnalysisRequest<TFormat>) => LayerImportAnalysis | Promise<LayerImportAnalysis>;
    onImport?: (request: LayerImportRequest<TFormat>) => LayerImportResult | Promise<LayerImportResult>;
    onExport?: (format: TFormat) => LayerExportArtifact | Promise<LayerExportArtifact>;
}

export function LayerInterchangeDialog<TFormat extends string>({ mode, layerName, description, formats, defaultFormat, currentMetamodel, onClose, onAnalyzeImport, onImport, onExport }: Props<TFormat>) {
    const [format, setFormat] = useState<TFormat>(defaultFormat);
    const [strategy, setStrategy] = useState<LayerMergeStrategy>('MERGE');
    const [fileName, setFileName] = useState('');
    const [fileContent, setFileContent] = useState('');
    const [step, setStep] = useState(mode === 'IMPORT' ? 'source' : 'format');
    const [busy, setBusy] = useState(false);
    const [committing, setCommitting] = useState(false);
    const [operationStage, setOperationStage] = useState<'prepare' | 'metamodel' | 'layer' | 'complete'>('prepare');
    const [result, setResult] = useState<LayerImportResult | null>(null);
    const [analysis, setAnalysis] = useState<LayerImportAnalysis | null>(null);
    const [selectedItemIds, setSelectedItemIds] = useState<string[]>([]);
    const [failure, setFailure] = useState('');
    const [transferLocation, setTransferLocation] = useState<'LOCAL'|'REMOTE'>('LOCAL');
    const [remoteProfiles, setRemoteProfiles] = useState<RemoteStorageProfileDescriptor[]>([]);
    const [remoteProfileId, setRemoteProfileId] = useState('');
    const [remotePath, setRemotePath] = useState('');
    const [remoteVersion, setRemoteVersion] = useState('');
    const [remoteLoading, setRemoteLoading] = useState(false);
    const [associatedMetamodelFile, setAssociatedMetamodelFile] = useState<File | null>(null);
    const [associatedMetamodelPreview, setAssociatedMetamodelPreview] = useState<MetamodelImportPreview | null>(null);
    const [associatedMetamodelAnalysisJobId, setAssociatedMetamodelAnalysisJobId] = useState('');
    const [associatedMetamodelTargetId, setAssociatedMetamodelTargetId] = useState('');
    const [saveAssociatedMetamodel, setSaveAssociatedMetamodel] = useState(true);
    const [overwriteAssociatedMetamodel, setOverwriteAssociatedMetamodel] = useState(false);
    const [associatedMetamodelBusy, setAssociatedMetamodelBusy] = useState(false);
    const [associatedMetamodelFailure, setAssociatedMetamodelFailure] = useState('');
    const associatedMetamodelInputRef = useRef<HTMLInputElement | null>(null);
    const associatedMetamodelAnalysisSequence = useRef(0);
    const selectedFormat = formats.find(item => item.id === format) ?? formats[0];
    const accept = useMemo(() => formats.flatMap(item => item.extensions).join(','), [formats]);
    const analysisRequired = mode === 'IMPORT' && onAnalyzeImport !== undefined;
    const previewItems = useMemo(() => analysis?.items ?? [], [analysis]);
    const categories = useMemo(() => [...new Set<string>(previewItems.map(item => item.category))].sort((left, right) => left.localeCompare(right)), [previewItems]);
    const selectedCategories = useMemo(() => categories.filter(category => previewItems.some(item => item.category === category && selectedItemIds.includes(item.id))), [categories, previewItems, selectedItemIds]);

    useEffect(() => {
        if(transferLocation !== 'REMOTE' || remoteProfiles.length > 0) return;
        let active = true;
        void loadRemoteStorageProfiles().then(profiles => {
            if (!active) return;
            setRemoteProfiles(profiles);
            const first = profiles.find(profile => profile.enabled && profile.configured);
            if (first !== undefined) setRemoteProfileId(current => current === '' ? first.id : current);
        }).catch(() => undefined);
        return () => { active = false; };
    }, [transferLocation, remoteProfiles.length]);

    function resetAnalysis(): void {
        setAnalysis(null);
        setSelectedItemIds([]);
        setResult(null);
    }

    function chooseFormat(next: TFormat): void {
        setFormat(next);
        resetAnalysis();
    }

    function applyLoadedSource(name: string, content: string): void {
        setFailure('');
        resetAnalysis();
        const lowerName = name.toLowerCase();
        const detected = [...formats]
            .flatMap(candidate => candidate.extensions.map(extension => ({ candidate, extension })))
            .filter(item => lowerName.endsWith(item.extension.toLowerCase()))
            .sort((left, right) => right.extension.length - left.extension.length)[0]?.candidate;
        if (detected !== undefined) setFormat(detected.id);
        setFileName(name);
        setFileContent(content);
    }

    async function readFile(file: File | undefined): Promise<void> {
        if (file === undefined) return;
        applyLoadedSource(file.name, await file.text());
        setRemoteVersion('');
    }

    async function loadRemoteSource(): Promise<void> {
        if (remoteProfileId === '' || remotePath.trim() === '') { setFailure(explainInputError(new Error('Remote profile and remote path are required'), 'Choose a remote profile and path before loading the layer.', `${layerName} remote import`)); return; }
        setRemoteLoading(true); setFailure('');
        try {
            const artifact = await loadRemoteLayerArtifact(remoteProfileId, remotePath.trim());
            applyLoadedSource(artifact.fileName, artifact.content);
            setRemoteVersion(artifact.version);
        } catch (error) { setFailure(explainInputError(error, 'Could not load the remote layer.', `${layerName} remote import`)); }
        finally { setRemoteLoading(false); }
    }

    function associatedMetamodelOptions(targetId = associatedMetamodelTargetId.trim()): MetamodelImportOptions {
        return {
            formatId: 'auto',
            targetId,
            overwrite: overwriteAssociatedMetamodel,
            preserveLayout: true,
            preserveStyles: true,
            strict: false
        };
    }

    async function readAssociatedMetamodel(file: File | undefined): Promise<void> {
        if (file === undefined) return;
        const sequence = ++associatedMetamodelAnalysisSequence.current;
        setAssociatedMetamodelFile(file);
        setAssociatedMetamodelPreview(null);
        setAssociatedMetamodelAnalysisJobId('');
        setAssociatedMetamodelTargetId('');
        setAssociatedMetamodelFailure('');
        resetAnalysis();
        await analyzeAssociatedMetamodel(file, sequence, '');
    }

    async function analyzeAssociatedMetamodel(file: File | null = associatedMetamodelFile, sequence = ++associatedMetamodelAnalysisSequence.current, targetId = associatedMetamodelTargetId.trim()): Promise<void> {
        if (file === null) return;
        setAssociatedMetamodelBusy(true);
        setAssociatedMetamodelFailure('');
        resetAnalysis();
        try {
            let completedJobId = '';
            const preview = await analyzeMetamodelImport(file, associatedMetamodelOptions(targetId), job => {
                if(associatedMetamodelAnalysisSequence.current === sequence && job.status === 'COMPLETED') completedJobId = job.jobId;
            });
            if(associatedMetamodelAnalysisSequence.current !== sequence) return;
            setAssociatedMetamodelAnalysisJobId(completedJobId);
            setAssociatedMetamodelPreview(preview);
            if(targetId === '') setAssociatedMetamodelTargetId(preview.metamodel.id);
        } catch (error) {
            if(associatedMetamodelAnalysisSequence.current !== sequence) return;
            setAssociatedMetamodelPreview(null);
            setAssociatedMetamodelFailure(explainInputError(error, 'Could not analyze the associated metamodel.', 'associated metamodel analysis'));
        } finally {
            if(associatedMetamodelAnalysisSequence.current === sequence) setAssociatedMetamodelBusy(false);
        }
    }

    function detachAssociatedMetamodel(): void {
        associatedMetamodelAnalysisSequence.current++;
        setAssociatedMetamodelFile(null);
        setAssociatedMetamodelPreview(null);
        setAssociatedMetamodelAnalysisJobId('');
        setAssociatedMetamodelTargetId('');
        setAssociatedMetamodelFailure('');
        setAssociatedMetamodelBusy(false);
        setSaveAssociatedMetamodel(true);
        setOverwriteAssociatedMetamodel(false);
        if(associatedMetamodelInputRef.current !== null) associatedMetamodelInputRef.current.value = '';
        resetAnalysis();
    }

    function augmentWithAssociatedMetamodel(next: LayerImportAnalysis): LayerImportAnalysis {
        if (associatedMetamodelPreview === null || currentMetamodel === undefined) return next;
        const compatibility = compareAssociatedMetamodel(associatedMetamodelPreview.metamodel, currentMetamodel);
        return {
            ...next,
            summary: `${next.summary} ${compatibility.summary}`,
            metrics: [...next.metrics, ...compatibility.metrics],
            diagnostics: [...(next.diagnostics ?? []), ...compatibility.diagnostics]
        };
    }

    async function analyzeImport(): Promise<void> {
        if (mode !== 'IMPORT' || onAnalyzeImport === undefined) return;
        if (fileName === '' || fileContent.trim() === '') {
            setFailure(explainInputError(new Error('Source file is required and must not be empty'), 'Select a non-empty source file before analyzing the import.', `${layerName} import`));
            return;
        }
        if (associatedMetamodelFile !== null && associatedMetamodelPreview === null) {
            setFailure(explainInputError(new Error('Associated metamodel analysis is required before layer analysis'), 'Analyze the associated metamodel before analyzing the layer import.', `${layerName} import`));
            return;
        }
        setBusy(true);
        setFailure('');
        setResult(null);
        try {
            const next = augmentWithAssociatedMetamodel(await onAnalyzeImport({ format, fileName, content: fileContent }));
            setAnalysis(next);
            setSelectedItemIds((next.items ?? []).filter(item => item.selectedByDefault !== false).map(item => item.id));
        } catch (error) {
            const message = error instanceof Error ? error.message : `Could not analyze ${layerName}.`;
            setAnalysis({ summary: 'The source could not be parsed safely, so no HYDRA document has been changed.', canImport: false, metrics: [{ id: 'analysis-errors', label: 'Blocking analysis errors', value: 1, tone: 'ERROR', detail: message }], diagnostics: [{ severity: 'ERROR', category: 'Import analysis', message }], warnings: [] });
            setSelectedItemIds([]);
            setFailure('');
        } finally {
            setBusy(false);
        }
    }

    async function finish(): Promise<boolean> {
        setFailure('');
        setBusy(true);
        setCommitting(true);
        setOperationStage('prepare');
        try {
            if (mode === 'IMPORT') {
                if (onImport === undefined || fileName === '' || fileContent.trim() === '') throw new Error('Select a non-empty source file before importing.');
                if (analysisRequired && (analysis === null || analysis.canImport === false)) throw new Error('Complete a successful source analysis before importing.');
                if (previewItems.length > 0 && selectedItemIds.length === 0) throw new Error('Select at least one definition to import.');
                if (associatedMetamodelFile !== null && associatedMetamodelPreview === null) throw new Error('Analyze the associated metamodel before importing the layer.');
                let associatedSavedId = '';
                if (associatedMetamodelFile !== null && associatedMetamodelPreview !== null && saveAssociatedMetamodel) {
                    setOperationStage('metamodel');
                    const saved = associatedMetamodelAnalysisJobId !== ''
                        ? await importPreparedMetamodel(associatedMetamodelAnalysisJobId)
                        : await importAndSaveMetamodel(associatedMetamodelFile, associatedMetamodelOptions());
                    associatedSavedId = saved.id;
                }
                setOperationStage('layer');
                const imported = await onImport({ format, fileName, content: fileContent, strategy, selection: analysis === null ? undefined : { itemIds: selectedItemIds, categories: selectedCategories } });
                setOperationStage('complete');
                setResult(associatedSavedId === '' ? imported : {
                    ...imported,
                    details: [`Associated metamodel imported as ${associatedSavedId}.`, `The ${layerName.toLowerCase()} layer remains attached to active metamodel ${currentMetamodel?.id ?? 'current'} and is adapted against it.`, ...imported.details],
                    metrics: [{ id: 'associated-metamodel-imported', label: 'Associated metamodels imported', value: 1, tone: 'GOOD' }, ...(imported.metrics ?? [])]
                });
            } else {
                if (onExport === undefined) throw new Error('This layer does not provide an exporter.');
                setOperationStage('layer');
                const artifact = await onExport(format);
                if (transferLocation === 'REMOTE') {
                    if (remoteProfileId === '') throw new Error('Choose a configured remote profile before saving the layer.');
                    const targetPath = remotePath.trim() === '' ? artifact.fileName : remotePath.trim();
                    const saved = await saveRemoteLayerArtifact(remoteProfileId, targetPath, artifact, { mode: 'UPSERT', expectedVersion: remoteVersion, commitMessage: `Save HYDRA ${layerName} layer` });
                    setRemotePath(targetPath); setRemoteVersion(saved.version);
                    setResult({ summary: `Saved ${artifact.fileName} to ${saved.provider}`, details: [`Remote path: ${saved.path}`, `Format: ${selectedFormat?.label ?? format}`], warnings: [], metrics: [{ id: 'generated-files', label: 'Generated files', value: 1, tone: 'GOOD' }] });
                } else {
                    downloadTextArtifact(artifact);
                    setResult({ summary: `Exported ${artifact.fileName}`, details: [`Format: ${selectedFormat?.label ?? format}`], warnings: [], metrics: [{ id: 'generated-files', label: 'Generated files', value: 1, tone: 'GOOD' }] });
                }
                setOperationStage('complete');
            }
            return true;
        } catch (error) {
            setFailure(explainInputError(error, `Could not ${mode === 'IMPORT' ? 'import' : 'export'} ${layerName}.`, `${layerName} ${mode === 'IMPORT' ? 'import' : 'export'}`));
            return false;
        } finally {
            setCommitting(false);
            setBusy(false);
        }
    }

    function setCategorySelected(category: string, checked: boolean): void {
        const ids = previewItems.filter(item => item.category === category).map(item => item.id);
        setSelectedItemIds(current => checked ? [...new Set([...current, ...ids])] : current.filter(id => !ids.includes(id)));
    }

    function setItemSelected(id: string, checked: boolean): void {
        setSelectedItemIds(current => checked ? [...new Set([...current, id])] : current.filter(item => item !== id));
    }

    const formatContent = <div className="hydra-layer-io-format-grid">{formats.map(item => <label className={item.id === format ? 'selected' : ''} key={item.id}><input type="radio" name="hydra-layer-format" value={item.id} checked={item.id === format} onChange={() => chooseFormat(item.id)} /><span><strong>{item.label}</strong><small>{item.description}</small><code>{item.extensions.join(', ')}</code></span></label>)}</div>;
    const remoteFields = <RemoteStorageProfileFields profiles={remoteProfiles} profileId={remoteProfileId} path={remotePath} onProfileChange={value => { setRemoteProfileId(value); setRemoteVersion(''); }} onPathChange={value => { setRemotePath(value); setRemoteVersion(''); }} disabled={busy || remoteLoading} />;
    const locationChoice = <fieldset className="hydra-layer-io-location"><legend>{mode === 'IMPORT' ? 'Source location' : 'Export destination'}</legend><label><input type="radio" checked={transferLocation === 'LOCAL'} onChange={() => setTransferLocation('LOCAL')} /> {mode === 'IMPORT' ? 'Local file' : 'Download file'}</label><label><input type="radio" checked={transferLocation === 'REMOTE'} onChange={() => setTransferLocation('REMOTE')} /> Remote storage</label></fieldset>;
    const sourceContent = mode === 'IMPORT' ? <div className="hydra-layer-io-source">
        {locationChoice}
        {transferLocation === 'LOCAL' ? <label><strong>Source file</strong><input type="file" accept={accept} onChange={event => void readFile(event.target.files?.[0])} /></label> : <section className="hydra-layer-io-remote-source">{remoteFields}<HydraButton type="button" disabled={remoteLoading || remoteProfileId === '' || remotePath.trim() === ''} onClick={() => void loadRemoteSource()}>{remoteLoading ? 'Loading remote layer…' : 'Load remote layer'}</HydraButton></section>}
        {fileName !== '' && <p><strong>{fileName}</strong> · {fileContent.length.toLocaleString()} characters loaded.</p>}
        {currentMetamodel !== undefined && <section className="hydra-layer-io-associated-metamodel">
            <header><div><strong>Associated metamodel (optional)</strong><p>Attach the metamodel that the imported layer was authored against. HYDRA analyzes it automatically with the normal metamodel importer, compares it with the active metamodel and reports exactly what can be mapped before the layer is changed. You can detach it at any time before importing.</p></div><HydraTooltip label="Associated metamodel import" title="Associated metamodel import"><p>Use this when an EVL/OCL/ATL file, event catalogue, EventFlow, quick-fix file or gamification layer was delivered together with its structural model. The associated metamodel can be stored as another HYDRA metamodel, while the imported layer is still adapted safely to the currently active metamodel.</p><p>The source metamodel is never used to silently replace the active metamodel. Missing classes, enumerations, attributes, relations and operations are reported before import.</p></HydraTooltip></header>
            <label><strong>Associated metamodel file</strong><input ref={associatedMetamodelInputRef} type="file" onChange={event => void readAssociatedMetamodel(event.target.files?.[0])} /></label>
            {associatedMetamodelFile !== null && <div className="hydra-layer-io-associated-actions"><span><strong>{associatedMetamodelFile.name}</strong><small>{associatedMetamodelBusy ? 'Analyzing automatically…' : associatedMetamodelPreview === null ? 'Automatic analysis did not complete.' : 'Automatic analysis complete.'}</small></span><div><HydraButton type="button" disabled={associatedMetamodelBusy} onClick={() => void analyzeAssociatedMetamodel()}>{associatedMetamodelBusy ? 'Analyzing metamodel…' : 'Analyze again'}</HydraButton><HydraButton className="hydra-layer-io-detach-metamodel" type="button" onClick={detachAssociatedMetamodel}>Detach metamodel</HydraButton></div></div>}
            {associatedMetamodelFailure !== '' && <HydraActionableErrorNotice className="hydra-layer-io-error" context="associated metamodel analysis" message={associatedMetamodelFailure} />}
            {associatedMetamodelPreview !== null && <div className="hydra-layer-io-associated-summary"><div><strong>{associatedMetamodelPreview.metamodel.name || associatedMetamodelPreview.metamodel.id}</strong><span>{associatedMetamodelPreview.detectedFormat} · {associatedMetamodelPreview.supportLevel}</span></div><ImportMetrics metrics={[
                { id: 'meta-classes', label: 'Metamodel classes', value: associatedMetamodelPreview.statistics.classes, tone: 'NEUTRAL' },
                { id: 'meta-enums', label: 'Metamodel enumerations', value: associatedMetamodelPreview.statistics.enums, tone: 'NEUTRAL' },
                { id: 'meta-attributes', label: 'Metamodel attributes', value: associatedMetamodelPreview.statistics.attributes, tone: 'NEUTRAL' },
                { id: 'meta-relations', label: 'Metamodel relations', value: associatedMetamodelPreview.statistics.relations, tone: 'NEUTRAL' },
                { id: 'meta-warnings', label: 'Metamodel warnings', value: associatedMetamodelPreview.warnings.length, tone: associatedMetamodelPreview.warnings.length > 0 ? 'WARNING' : 'GOOD' }
            ]} />{currentMetamodel !== undefined && <><p className="hydra-layer-io-note"><strong>Compatibility with active metamodel:</strong> {compareAssociatedMetamodel(associatedMetamodelPreview.metamodel, currentMetamodel).summary}</p><ImportMetrics metrics={compareAssociatedMetamodel(associatedMetamodelPreview.metamodel, currentMetamodel).metrics} /></>}</div>}
            {associatedMetamodelFile !== null && <fieldset><legend>Keep the associated metamodel in HYDRA?</legend><label><input type="checkbox" checked={saveAssociatedMetamodel} onChange={event => setSaveAssociatedMetamodel(event.target.checked)} /> Import it as an additional main metamodel</label>{saveAssociatedMetamodel && <><label className="hydra-layer-io-associated-target">HYDRA target ID<input type="text" value={associatedMetamodelTargetId} placeholder="Uses the imported metamodel ID" onChange={event => { setAssociatedMetamodelTargetId(event.target.value); setAssociatedMetamodelAnalysisJobId(''); }} /></label><label><input type="checkbox" checked={overwriteAssociatedMetamodel} onChange={event => { setOverwriteAssociatedMetamodel(event.target.checked); setAssociatedMetamodelAnalysisJobId(''); }} /> Allow overwrite when that HYDRA ID already exists</label></>}</fieldset>}
        </section>}
        <fieldset><legend>How should imported definitions be applied?</legend><label><input type="radio" checked={strategy === 'MERGE'} onChange={() => setStrategy('MERGE')} /> Merge with the current layer</label><label><input type="radio" checked={strategy === 'REPLACE'} onChange={() => setStrategy('REPLACE')} /> Replace definitions from this layer</label></fieldset>
        <p className="hydra-layer-io-note"><strong>Metamodel adaptation:</strong> imported class, attribute, relation and operation names are resolved against the currently selected main metamodel. Unresolved references are reported instead of being silently invented.</p>
        {analysisRequired && <section className="hydra-layer-io-analysis-action"><div><strong>Analyze before importing</strong><p>HYDRA parses the file without executing imported JavaScript, identifies recoverable definitions, classifies diagnostics and lets you choose what will actually enter the HYDRA layer.</p></div><HydraButton type="button" disabled={busy || fileName === '' || fileContent.trim() === ''} onClick={() => void analyzeImport()}>{busy ? 'Analyzing…' : analysis === null ? 'Analyze source' : 'Analyze again'}</HydraButton></section>}
        {analysis !== null && <ImportMetrics metrics={analysis.metrics} />}
    </div> : <div className="hydra-layer-io-source">{locationChoice}{transferLocation === 'REMOTE' && remoteFields}<p>The current HYDRA {layerName.toLowerCase()} definitions will be projected to <strong>{selectedFormat?.label ?? format}</strong>. Editing continues in HYDRA after export.</p><p className="hydra-layer-io-note">HYDRA-specific information that the target standard cannot express directly is written as documented extension metadata where the format permits it, so a later re-import can recover the HYDRA semantics.</p></div>;
    const reviewContent = <div className="hydra-layer-io-review"><dl><div><dt>Layer</dt><dd>{layerName}</dd></div><div><dt>Format</dt><dd>{selectedFormat?.label ?? format}</dd></div>{mode === 'IMPORT' && <><div><dt>File</dt><dd>{fileName || 'Not selected'}</dd></div><div><dt>Strategy</dt><dd>{strategy === 'MERGE' ? 'Merge' : 'Replace'}</dd></div></>}</dl>
        {analysis !== null && <><section className="hydra-layer-io-analysis-summary"><header><div><strong>Pre-import analysis</strong><p>{analysis.summary}</p></div><HydraTooltip label="Import metrics" title="Import metrics"><p>These metrics are calculated before changing the HYDRA documents. Errors indicate constructs that cannot be imported safely; warnings identify partial mappings or unresolved references. Items you deselect are intentionally skipped, not treated as errors.</p></HydraTooltip></header><ImportMetrics metrics={analysis.metrics} />{(analysis.diagnostics?.length ?? 0) > 0 && <DiagnosticSummary diagnostics={analysis.diagnostics ?? []} />}</section>{previewItems.length > 0 && <ImportSelection categories={categories} items={previewItems} selectedIds={selectedItemIds} onAllChange={checked => setSelectedItemIds(checked ? previewItems.map(item => item.id) : [])} onCategoryChange={setCategorySelected} onItemChange={setItemSelected} />}</>}
        {failure !== '' && <HydraActionableErrorNotice className="hydra-layer-io-error" context={`${layerName} ${mode === 'IMPORT' ? 'import' : 'export'}`} message={failure} />}{result !== null && <div className="hydra-layer-io-result"><strong>{result.summary}</strong>{result.metrics !== undefined && <ImportMetrics metrics={result.metrics} />}{result.details.length > 0 && <ul>{result.details.map(item => <li key={item}>{item}</li>)}</ul>}{(result.diagnostics?.length ?? 0) > 0 && <DiagnosticSummary diagnostics={result.diagnostics ?? []} />}{result.warnings.length > 0 && <><h5>Warnings</h5><ul>{result.warnings.map((item, index) => <li key={`${index}-${item}`}>{item}</li>)}</ul></>}</div>}</div>;

    const associatedMetamodelReady = associatedMetamodelFile === null || associatedMetamodelPreview !== null;
    const sourceReady = fileName !== '' && fileContent.trim().length > 0 && associatedMetamodelReady && (!analysisRequired || (analysis !== null && analysis.canImport !== false));
    const reviewReady = sourceReady && (previewItems.length === 0 || selectedItemIds.length > 0);
    const steps = [
        { id: 'format', title: 'Format', description: 'Choose the external representation.', checks: [{ id: 'selected-format', label: 'Interchange format selected', complete: selectedFormat !== undefined, detail: selectedFormat?.description }], content: formatContent },
        { id: 'source', title: mode === 'IMPORT' ? 'Source, analysis and strategy' : 'Projection', description: mode === 'IMPORT' ? 'Load the file, analyze it safely and choose how it is merged.' : 'Review how the HYDRA layer will be projected.', checks: mode === 'IMPORT' ? [{ id: 'file', label: 'Source file loaded', complete: fileName !== '' && fileContent.trim().length > 0, detail: fileName === '' ? 'Choose a file before continuing.' : fileName }, ...(associatedMetamodelFile === null ? [] : [{ id: 'associated-metamodel', label: 'Associated metamodel analyzed', complete: associatedMetamodelPreview !== null, detail: associatedMetamodelPreview === null ? (associatedMetamodelBusy ? 'The associated metamodel is being analyzed automatically.' : 'Automatic metamodel analysis must complete before continuing.') : `${associatedMetamodelPreview.metamodel.name || associatedMetamodelPreview.metamodel.id} · ${associatedMetamodelPreview.detectedFormat}` }]), ...(analysisRequired ? [{ id: 'analysis', label: 'Pre-import analysis completed', complete: analysis !== null && analysis.canImport !== false, detail: analysis === null ? 'Analyze the source before continuing.' : analysis.summary }] : [])] : [{ id: 'HYDRA', label: 'HYDRA definitions will remain editable', complete: true, detail: 'Export never replaces the HYDRA document.' }], content: sourceContent },
        { id: 'review', title: mode === 'IMPORT' ? 'Select, adapt and import' : 'Generate', description: mode === 'IMPORT' ? 'Review diagnostics and choose exactly which definitions enter the current layer.' : 'Generate the selected external representation.', checks: [{ id: 'ready', label: mode === 'IMPORT' ? 'Import request is ready' : 'Export request is ready', complete: mode === 'IMPORT' ? reviewReady : true, detail: mode === 'IMPORT' ? (previewItems.length > 0 ? `${selectedItemIds.length} of ${previewItems.length} identified definition(s) selected.` : 'HYDRA will report every unresolved or partially converted construct.') : 'The generated file can be imported again later.' }], content: reviewContent }
    ];

    return <div className="hydra-layer-io-backdrop" role="presentation">
        <section className="hydra-layer-io-dialog" role="dialog" aria-modal="true" aria-label={`${mode === 'IMPORT' ? 'Import' : 'Export'} ${layerName}`}>
            <header className="hydra-window-header"><div><h3>{mode === 'IMPORT' ? 'Import' : 'Export'} {layerName}</h3><p>{description}</p></div><div className="hydra-layer-io-header-help"><HydraTooltip label="Layer import and export" title="Layer import and export"><p>Import and export operate on this layer independently from the metamodel importer. The HYDRA representation remains the editable source used by the designer after conversion.</p></HydraTooltip></div><HydraWindowControls closeLabel="Close layer import or export" onClose={onClose} /></header>
            <HydraOperationProgress active={committing} compact title={mode === 'IMPORT' ? 'Importing layer' : 'Generating export'} detail={operationStage === 'prepare' ? 'Preparing import request…' : operationStage === 'metamodel' ? 'Saving associated metamodel…' : operationStage === 'layer' ? (mode === 'IMPORT' ? 'Applying selected definitions…' : 'Generating the selected representation…') : mode === 'IMPORT' ? 'Import completed.' : 'Export completed.'} currentStageId={operationStage} completed={operationStage === 'complete'} stages={[{ id:'prepare', label:mode === 'IMPORT' ? 'Prepare import' : 'Prepare export' }, ...(mode === 'IMPORT' && associatedMetamodelFile !== null && saveAssociatedMetamodel ? [{ id:'metamodel', label:'Save associated metamodel' }] : []), { id:'layer', label:mode === 'IMPORT' ? 'Apply layer definitions' : 'Generate representation' }, { id:'complete', label:mode === 'IMPORT' ? 'Confirm import' : 'Confirm export' }]} />
            <HydraGuidedWorkflow title={`${mode === 'IMPORT' ? 'Import' : 'Export'} workflow`} description="Confirm each block explicitly. A valid default is blue until you review it; only confirmed blocks become green." steps={steps} currentStepId={step} onStepChange={setStep} finishLabel={busy ? 'Working…' : mode === 'IMPORT' ? 'Import selected definitions' : 'Generate file'} onFinish={() => busy ? false : finish()} />
            <footer><HydraButton type="button" onClick={onClose}>Close</HydraButton></footer>
        </section>
    </div>;
}

function ImportMetrics({ metrics }: { metrics: readonly { id: string; label: string; value: number; tone?: 'NEUTRAL'|'GOOD'|'WARNING'|'ERROR'; detail?: string }[] }) {
    if (metrics.length === 0) return null;
    return <div className="hydra-layer-io-metrics">{metrics.map(metric => <article className={(metric.tone ?? 'NEUTRAL').toLowerCase()} key={metric.id}><span>{metric.label}</span><strong>{metric.value.toLocaleString()}</strong>{metric.detail !== undefined && <small>{metric.detail}</small>}</article>)}</div>;
}

function DiagnosticSummary({ diagnostics }: { diagnostics: readonly { severity: 'ERROR'|'WARNING'|'INFO'; category: string; message: string; itemId?: string }[] }) {
    return <section className="hydra-layer-io-diagnostics"><h5>Diagnostics</h5><ul>{diagnostics.map((diagnostic, index) => <li className={diagnostic.severity.toLowerCase()} key={`${diagnostic.severity}-${diagnostic.category}-${diagnostic.itemId ?? ''}-${index}`}><strong>{diagnostic.severity} · {diagnostic.category}</strong><span data-hydra-runtime-message="true" data-hydra-message-context="layers" data-hydra-message-severity={diagnostic.severity}>{diagnostic.message}</span></li>)}</ul></section>;
}

function ImportSelection({ categories, items, selectedIds, onAllChange, onCategoryChange, onItemChange }: { categories: readonly string[]; items: readonly { id: string; label: string; category: string; description?: string; source?: string; warnings?: readonly string[] }[]; selectedIds: readonly string[]; onAllChange: (checked: boolean) => void; onCategoryChange: (category: string, checked: boolean) => void; onItemChange: (id: string, checked: boolean) => void }) {
    return <section className="hydra-layer-io-selection"><header><div><strong>Choose what to import</strong><p>Select whole categories or individual definitions. Deselected items are deliberately ignored and do not modify the HYDRA event catalogue.</p></div><div className="hydra-layer-io-selection-actions"><span>{selectedIds.length}/{items.length} selected</span><HydraButton type="button" onClick={() => onAllChange(true)}>Select all</HydraButton><HydraButton type="button" onClick={() => onAllChange(false)}>Select none</HydraButton></div></header><div className="hydra-layer-io-category-filter">{categories.map(category => { const ids = items.filter(item => item.category === category).map(item => item.id); const checked = ids.length > 0 && ids.every(id => selectedIds.includes(id)); return <label key={category}><input type="checkbox" checked={checked} onChange={event => onCategoryChange(category, event.target.checked)} /><span>{category}</span><small>{ids.length}</small></label>; })}</div><div className="hydra-layer-io-selection-list">{items.map(item => <label key={item.id} className={selectedIds.includes(item.id) ? 'selected' : ''}><input type="checkbox" checked={selectedIds.includes(item.id)} onChange={event => onItemChange(item.id, event.target.checked)} /><span><strong>{item.label}</strong><small>{item.category}{item.source === undefined ? '' : ` · ${item.source}`}</small>{item.description !== undefined && <p>{item.description}</p>}{(item.warnings?.length ?? 0) > 0 && <em>{item.warnings?.join(' · ')}</em>}</span></label>)}</div></section>;
}

function downloadTextArtifact(artifact: LayerExportArtifact): void {
    const blob = new Blob([artifact.content], { type: artifact.mimeType ?? 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = artifact.fileName;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
}
