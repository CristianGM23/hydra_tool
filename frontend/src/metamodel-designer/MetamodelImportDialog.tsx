import { HydraButton } from '../design-system/HydraButton';
import { createPortal } from 'react-dom';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { ChangeEvent, MouseEvent } from 'react';
import { FileIcon } from '../components/AppIcons';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { FormFieldHelp, RequiredFieldsLegend } from '../components/FormFieldHelp';
import { HydraActionableErrorNotice, HydraCreationModeSwitch, HydraGuidedWorkflow, HydraOperationProgress, HydraWindowControls, openFileInputPicker } from '../design-system';
import { explainInputError } from '../api/inputErrorExplanation';
import { loadRemoteFileArtifact, loadRemoteStorageProfiles, RemoteStorageProfileFields } from '../remote-storage';
import type { RemoteStorageProfileDescriptor } from '../remote-storage';

import { analyzeMetamodelImport, downloadMetamodelImportCombinedLog, downloadMetamodelImportStageLog, importAndSaveMetamodel, importPreparedMetamodel } from './importApi';
import type { MetamodelImportActivityMessage, MetamodelImportActivitySeverity, MetamodelImportAnalysisJob, MetamodelImportAnalysisStage, MetamodelImportFormatDescriptor, MetamodelImportOptions, MetamodelImportPreview, MetamodelImportSaveResult } from './importTypes';

import './metamodel-import-dialog.css';

interface MetamodelImportDialogProps {
    formats: MetamodelImportFormatDescriptor[];
    onClose: () => void;
    onLoad: (preview: MetamodelImportPreview) => void;
    onSaved: (result: MetamodelImportSaveResult) => void;
}

const DEFAULT_OPTIONS: MetamodelImportOptions = {
    formatId: 'auto',
    targetId: '',
    overwrite: false,
    preserveLayout: true,
    preserveStyles: true,
    strict: false
};

export function MetamodelImportDialog({ formats, onClose, onLoad, onSaved }: MetamodelImportDialogProps) {
    const [file, setFile] = useState<File | null>(null);
    const [sourceLocation, setSourceLocation] = useState<'LOCAL' | 'REMOTE'>('LOCAL');
    const [remoteProfiles, setRemoteProfiles] = useState<RemoteStorageProfileDescriptor[]>([]);
    const [remoteProfileId, setRemoteProfileId] = useState('');
    const [remotePath, setRemotePath] = useState('');
    const [options, setOptions] = useState<MetamodelImportOptions>(DEFAULT_OPTIONS);
    const [preview, setPreview] = useState<MetamodelImportPreview | null>(null);
    const [analysisJob, setAnalysisJob] = useState<MetamodelImportAnalysisJob | null>(null);
    const [busyOperation, setBusyOperation] = useState<'ANALYZE' | 'SAVE' | 'LOG' | ''>('');
    const [error, setError] = useState('');
    const [maximized, setMaximized] = useState(false);
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('ADVANCED');
    const [guidedStep, setGuidedStep] = useState('source');
    const analysisController = useRef<AbortController | null>(null);
    const fileInputRef = useRef<HTMLInputElement | null>(null);
    const acceptedExtensions = useMemo(() => formats.flatMap(format => format.extensions).join(','), [formats]);
    const busy = busyOperation === 'ANALYZE' || busyOperation === 'SAVE';

    useEffect(() => () => analysisController.current?.abort(), []);

    useEffect(() => {
        let active = true;
        void loadRemoteStorageProfiles().then(profiles => {
            if(!active) return;
            setRemoteProfiles(profiles);
            setRemoteProfileId(current => current || profiles.find(profile => profile.enabled && profile.configured)?.id || '');
        }).catch(() => { if(active) setRemoteProfiles([]); });
        return () => { active = false; };
    }, []);

    useEffect(() => {
        function closeWithEscape(event: KeyboardEvent): void {
            if(event.key === 'Escape') {
                analysisController.current?.abort();
                onClose();
            }
        }

        window.addEventListener('keydown', closeWithEscape);
        return () => window.removeEventListener('keydown', closeWithEscape);
    }, [onClose]);

    const updateBoolean = (name: keyof Pick<MetamodelImportOptions, 'overwrite' | 'preserveLayout' | 'preserveStyles' | 'strict'>, value: boolean) => {
        setOptions(current => ({ ...current, [name]: value }));
        resetAnalysis();
    };

    function resetAnalysis(): void {
        analysisController.current?.abort();
        analysisController.current = null;
        setPreview(null);
        setAnalysisJob(null);
        setError('');
    }

    async function analyze(selectedFile: File | null = file): Promise<void> {
        if(selectedFile === null) {
            setError(explainInputError(new Error('Metamodel file is required'), 'Select a metamodel file', 'metamodel import'));
            return;
        }

        analysisController.current?.abort();
        const controller = new AbortController();
        analysisController.current = controller;
        const now = new Date().toISOString();
        setBusyOperation('ANALYZE');
        setPreview(null);
        setError('');
        setAnalysisJob({ jobId: 'uploading', status: 'QUEUED', progress: 0, phase: 'UPLOAD', createdAt: now, updatedAt: now, activities: [], preview: null, errorMessage: '', completedStages: 0, totalStages: 11, stages: [], combinedLogAvailable: false });

        try {
            const result = await analyzeMetamodelImport(selectedFile, options, setAnalysisJob, controller.signal);
            setPreview(result);
        } catch(exception) {
            if(exception instanceof DOMException && exception.name === 'AbortError') {
                return;
            }

            setError(explainInputError(exception, 'Could not preview metamodel import', 'metamodel import'));
        } finally {
            if(analysisController.current === controller) {
                analysisController.current = null;
                setBusyOperation('');
            }
        }
    }

    async function loadRemoteAndAnalyze(): Promise<void> {
        if(remoteProfileId === '' || remotePath.trim() === '') {
            setError(explainInputError(new Error('Remote profile and remote path are required'), 'Choose a remote profile and path before loading the metamodel.', 'remote metamodel import'));
            return;
        }
        setBusyOperation('ANALYZE');
        setError('');
        try {
            const artifact = await loadRemoteFileArtifact(remoteProfileId, remotePath.trim());
            await selectFileAndAnalyze(artifact.file);
        } catch(exception) {
            setError(explainInputError(exception, 'Could not load the remote metamodel.', 'remote metamodel import'));
            setBusyOperation('');
        }
    }

    async function selectFileAndAnalyze(selectedFile: File | null): Promise<void> {
        resetAnalysis();
        setFile(selectedFile);
        if(selectedFile === null) return;
        if(creationMode === 'GUIDED') setGuidedStep('analysis');
        await analyze(selectedFile);
    }

    async function save(): Promise<boolean> {
        if(file === null) {
            setError(explainInputError(new Error('Metamodel file is required'), 'Select a metamodel file', 'metamodel import'));
            return false;
        }

        setBusyOperation('SAVE');
        setError('');

        try {
            const reusableJob = analysisJob !== null && analysisJob.status === 'COMPLETED' && analysisJob.preview !== null && analysisJob.jobId !== 'uploading';
            onSaved(reusableJob ? await importPreparedMetamodel(analysisJob.jobId) : await importAndSaveMetamodel(file, options));
            return true;
        } catch(exception) {
            setError(explainInputError(exception, 'Could not import metamodel', 'metamodel import'));
            return false;
        } finally {
            setBusyOperation('');
        }
    }

    async function downloadStageLog(stageId: string): Promise<void> {
        if(analysisJob === null || analysisJob.jobId === 'uploading') {
            return;
        }

        setBusyOperation('LOG');
        setError('');

        try {
            await downloadMetamodelImportStageLog(analysisJob.jobId, stageId);
        } catch(exception) {
            setError(explainInputError(exception, 'Could not download the stage log', 'metamodel import analysis'));
        } finally {
            setBusyOperation('');
        }
    }

    async function downloadAllLogs(): Promise<void> {
        if(analysisJob === null || analysisJob.jobId === 'uploading') {
            return;
        }

        setBusyOperation('LOG');
        setError('');

        try {
            await downloadMetamodelImportCombinedLog(analysisJob.jobId);
        } catch(exception) {
            setError(explainInputError(exception, 'Could not download the complete analysis log', 'metamodel import analysis'));
        } finally {
            setBusyOperation('');
        }
    }

    function close(): void {
        analysisController.current?.abort();
        onClose();
    }

    function closeFromBackdrop(event: MouseEvent<HTMLDivElement>): void {
        if(event.target === event.currentTarget) {
            close();
        }
    }

    function openUpdateSiteRecovery(): void {
        analysisController.current?.abort();
        onClose();
        window.location.hash = '#/update-site-imports';
    }

    const canDownloadAllLogs = analysisJob !== null && analysisJob.jobId !== 'uploading' && analysisJob.combinedLogAvailable;
    const blocklyImportSelected = options.formatId === 'blockly-json' || options.formatId === 'blockly-javascript' || preview?.detectedFormat === 'blockly-json' || preview?.detectedFormat === 'blockly-javascript';

    const sourceStepContent = <div className="metamodel-import-guided-block"><RequiredFieldsLegend /><div className="hydra-remote-location-switch" aria-label="Source location"><HydraButton className={sourceLocation === 'LOCAL' ? 'active' : ''} type="button" onClick={() => setSourceLocation('LOCAL')}>Local file</HydraButton><HydraButton className={sourceLocation === 'REMOTE' ? 'active' : ''} type="button" onClick={() => setSourceLocation('REMOTE')}>Remote storage</HydraButton></div>{sourceLocation === 'LOCAL' ? <FormFieldHelp label="Metamodel file" targetId="metamodel-import-guided-file-button" help="Choose the semantic, grammar or diagram document to import. HYDRA does not modify the source file; it first uploads it for analysis and builds a neutral preview." example="biblioteca.ecore, model.ts, model.js, model.go, language.xtext, blocks.blockly.js" required><input accept={acceptedExtensions} className="metamodel-import-hidden-file" disabled={busy} id="metamodel-import-guided-file-input" ref={fileInputRef} onChange={(event: ChangeEvent<HTMLInputElement>) => void selectFileAndAnalyze(event.target.files?.[0] ?? null)} type="file" /><HydraButton aria-label="File system — select metamodel file" className="metamodel-import-source-button" disabled={busy} id="metamodel-import-guided-file-button" type="button" onClick={() => openFileInputPicker(fileInputRef.current)}><FileIcon/><span><strong>File system</strong><small>{file === null ? 'Select a supported metamodel file from this computer.' : file.name}</small></span></HydraButton></FormFieldHelp> : <section className="hydra-remote-source-panel"><RemoteStorageProfileFields profiles={remoteProfiles} profileId={remoteProfileId} path={remotePath} disabled={busy} onProfileChange={setRemoteProfileId} onPathChange={setRemotePath} /><HydraButton disabled={busy || remoteProfileId === '' || remotePath.trim() === ''} type="button" onClick={() => void loadRemoteAndAnalyze()}>{busyOperation === 'ANALYZE' ? 'Loading remote metamodel…' : 'Load remote metamodel'}</HydraButton>{file !== null && <small>Loaded file: {file.name}</small>}</section>}<aside className="metamodel-update-site-shortcut"><div><strong>Is the source an Eclipse update site or p2 repository?</strong><span>Use the dedicated recovery workflow when Ecore, GenModel, GMF, Sirius, Xtext and bundle provenance must be recovered together.</span></div><HydraButton type="button" onClick={openUpdateSiteRecovery}>Open update-site recovery</HydraButton></aside></div>;

    const optionsStepContent = <div className="metamodel-import-guided-block"><div className="metamodel-import-options-grid"><FormFieldHelp label="Format" targetId="metamodel-import-guided-format" help="Automatic detection is recommended. Force a specific importer only when the file extension/content is ambiguous or you deliberately need a particular parser." example="Automatic detection" options={[{ value:'auto',label:'Automatic detection',description:'Chooses the importer from the file extension and content.' }, ...formats.map(format => ({ value:format.id,label:format.displayName,description:`${format.supportLevel.toLowerCase()} support for ${format.extensions.join(', ')} files.` }))]}><HydraAdaptiveSelect showOptionHelp={false} id="metamodel-import-guided-format" ariaLabel="Format" disabled={busy} value={options.formatId} options={[{ value:'auto',label:'Automatic detection',description:'Chooses the importer from the file extension and content.',category:'Recommended' }, ...formats.map(format => ({ value:format.id,label:format.displayName,description:`${format.supportLevel.toLowerCase()} support for ${format.extensions.join(', ')} files.`,category:formatCategory(format.category) }))]} onChange={formatId => { setOptions(current => ({ ...current, formatId })); resetAnalysis(); }} /></FormFieldHelp><FormFieldHelp label="Target identifier" targetId="metamodel-import-guided-target-id" help="Optional stable HYDRA identifier. Leave it empty to reuse the source identifier; provide one when importing several versions or when the source identifier is unsuitable." example="mi-metamodelo"><input id="metamodel-import-guided-target-id" disabled={busy} onChange={(event: ChangeEvent<HTMLInputElement>) => { setOptions(current => ({ ...current, targetId:event.target.value })); resetAnalysis(); }} placeholder="Uses the imported identifier" type="text" value={options.targetId} /></FormFieldHelp></div><div className="metamodel-import-checks"><FormFieldHelp label="Preserve layout" targetId="metamodel-import-guided-preserve-layout" help="Keeps node positions and edge routes when graphical layout is present. This affects presentation, not metamodel semantics." example="Enabled for Draw.io or graphical Ecore exports"><label className="metamodel-import-check"><input id="metamodel-import-guided-preserve-layout" checked={options.preserveLayout} disabled={busy} onChange={(event: ChangeEvent<HTMLInputElement>) => updateBoolean('preserveLayout', event.target.checked)} type="checkbox" /> Preserve layout when available</label></FormFieldHelp><FormFieldHelp label="Preserve visual styles" targetId="metamodel-import-guided-preserve-styles" help="Keeps supported colors, borders, fonts and visual metadata when the source format contains them." example="Node fill and edge line style"><label className="metamodel-import-check"><input id="metamodel-import-guided-preserve-styles" checked={options.preserveStyles} disabled={busy} onChange={(event: ChangeEvent<HTMLInputElement>) => updateBoolean('preserveStyles', event.target.checked)} type="checkbox" /> Preserve visual styles</label></FormFieldHelp><FormFieldHelp label="Strict mode" targetId="metamodel-import-guided-strict" help="When enabled, HYDRA rejects semantic loss instead of importing with warnings. Use this for migrations where preserving all supported meaning is more important than obtaining a partial result." example="Stop when an Xtext construct cannot be represented"><label className="metamodel-import-check"><input id="metamodel-import-guided-strict" checked={options.strict} disabled={busy} onChange={(event: ChangeEvent<HTMLInputElement>) => updateBoolean('strict', event.target.checked)} type="checkbox" /> Reject semantic loss</label></FormFieldHelp><FormFieldHelp label="Allow overwrite" targetId="metamodel-import-guided-overwrite" help="Allows the final save to replace an existing metamodel with the same target ID. Leave disabled unless replacing that document is intentional." example="Replace mi-metamodelo"><label className="metamodel-import-check"><input id="metamodel-import-guided-overwrite" checked={options.overwrite} disabled={busy} onChange={(event: ChangeEvent<HTMLInputElement>) => updateBoolean('overwrite', event.target.checked)} type="checkbox" /> Allow overwrite when saving</label></FormFieldHelp></div></div>;

    const analysisStepContent = <div className="metamodel-import-guided-block"><p>Analysis starts automatically as soon as a metamodel file is selected. HYDRA detects the format, resolves supported structures, records warnings/information loss and prepares a neutral preview before you decide to load or save anything.</p>{blocklyImportSelected && <section className="metamodel-blockly-import-guide"><header><div><strong>Blockly / JavaScript structural recovery</strong><span>HYDRA treats Blockly block definitions with the same explicit recovery philosophy used for grammar imports.</span></div></header><ol><li><strong>Block types → classes.</strong> Each static Blockly <code>type</code> becomes an editable neutral class and keeps the original block type as traceability metadata.</li><li><strong>Fields → attributes.</strong> Common <code>field_input</code>, <code>field_number</code>, <code>field_checkbox</code> and related fields become neutral attributes with their Blockly declaration preserved.</li><li><strong>Inputs → relations.</strong> <code>input_value</code> and <code>input_statement</code> entries become editable relations. Their <code>check</code> declarations are preserved as connection-compatibility metadata.</li><li><strong>Block connections → compatibility contracts.</strong> <code>output</code>, <code>previousStatement</code> and <code>nextStatement</code> checks are retained so they can later be projected to independent validation rules or back to Blockly.</li><li><strong>JavaScript is never executed.</strong> HYDRA only reads static <code>defineBlocksWithJsonArray(...)</code> and <code>jsonInit(...)</code> JSON literals. Dynamic initialization code is reported instead of being executed.</li><li><strong>Review before saving.</strong> Analyze first, inspect recovered classes/relations and warnings, then open the recovered preview or save it.</li></ol></section>}<HydraButton className="metamodel-import-preview-button" disabled={busy || file === null} onClick={() => void analyze()} type="button">{busyOperation === 'ANALYZE' ? 'Analyzing…' : preview === null ? 'Analyze file' : 'Analyze again'}</HydraButton>{analysisJob !== null && <ImportAnalysisProgress job={analysisJob} onDownloadStage={stageId => void downloadStageLog(stageId)} />}{error !== '' && <HydraActionableErrorNotice className="metamodel-import-error" context="metamodel import" message={error} />}</div>;

    const reviewStepContent = <div className="metamodel-import-guided-block">{preview === null ? <div className="hydra-empty-state"><strong>No analyzed preview</strong><span>Complete the analysis step first.</span></div> : <><ImportPreview preview={preview} /><section className="metamodel-import-guided-review"><h4>Choose what happens next</h4><p><strong>Open in designer</strong> loads the analyzed neutral preview into the current visual session without immediately persisting it. <strong>Import and save</strong> performs the final import and stores the resulting metamodel using the selected target/overwrite options.</p><div><HydraButton disabled={busy} type="button" onClick={() => onLoad(preview)}>Open in designer</HydraButton><HydraButton className="metamodel-save-button" disabled={busy || file === null} type="button" onClick={() => void save()}>{busyOperation === 'SAVE' ? 'Importing…' : 'Import and save'}</HydraButton></div></section></>}</div>;

    const guidedSteps = [
        { id:'source', title:'Source', description:'Choose the correct import source.', checks:[{ id:'file',label:'A supported source file is selected',complete:file !== null,detail:file?.name ?? 'Choose a file, or use Update Site recovery for p2 repositories.'}], content:sourceStepContent },
        { id:'options', title:'Import strategy', description:'Review format detection, preservation and safety options.', checks:[{ id:'format',label:'Importer strategy is defined',complete:options.formatId.trim() !== '',detail:options.formatId === 'auto' ? 'Automatic detection is selected.' : `Forced importer: ${options.formatId}.`},{ id:'safety',label:'Overwrite and strictness choices are explicit',complete:true,detail:`Overwrite ${options.overwrite ? 'allowed' : 'blocked'} · strict semantic preservation ${options.strict ? 'enabled' : 'disabled'}.`}], content:optionsStepContent },
        { id:'analysis', title:'Analyze', description:'Inspect the source before mutating the project.', checks:[{ id:'preview',label:'Analysis produced a neutral preview',complete:preview !== null,detail:preview === null ? (analysisJob === null ? 'Analysis starts automatically after file selection. Use Analyze again only when you want to repeat it.' : `Analysis status: ${analysisJob.status}.`) : `${preview.detectedFormat} · ${preview.supportLevel}.`},{ id:'analysis-error',label:'No blocking analysis error is active',complete:error === '',detail:error === '' ? 'No blocking client-side error.' : error}], content:analysisStepContent },
        { id:'review', title:'Review and import', description:'Confirm the recovered structure and choose load or save.', checks:[{ id:'preview-ready',label:'Preview is available for review',complete:preview !== null,detail:preview === null ? 'Analysis must finish first.' : `${preview.statistics.classes} classes · ${preview.statistics.relations} relations.`}], content:reviewStepContent }
    ];
    const dialog = (
        <div className="metamodel-import-backdrop" onMouseDown={closeFromBackdrop} role="presentation">
            <section aria-labelledby="import-metamodel-title" aria-modal="true" className={`metamodel-import-dialog${maximized ? ' maximized' : ''}`} role="dialog">
                <header className="metamodel-import-window-header hydra-window-header">
                    <div className="metamodel-import-window-title">
                        <h2 id="import-metamodel-title">Import metamodel</h2>
                        <p>Import semantic, grammar and diagram formats and continue editing them on the visual canvas.</p>
                    </div>
                    <HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} advancedLabel="Full import form" />
                    <HydraWindowControls closeLabel="Close import dialog" maximizeLabel="Maximize import dialog" maximized={maximized} onClose={close} onToggleMaximize={() => setMaximized(current => !current)} restoreLabel="Restore import dialog" />
                </header>
                <HydraOperationProgress
                    active={busyOperation === 'SAVE' || (busyOperation === 'ANALYZE' && analysisJob === null)}
                    compact
                    title={busyOperation === 'SAVE' ? 'Importing metamodel' : 'Loading import source'}
                    detail={busyOperation === 'SAVE'
                        ? 'HYDRA is materializing the analyzed neutral model and persisting the metamodel.'
                        : 'HYDRA is reading the selected source before the staged analysis progress becomes available.'}
                    currentStageId={busyOperation === 'SAVE' ? 'persist' : 'source'}
                    stages={busyOperation === 'SAVE' ? [
                        { id: 'materialize', label: 'Materialize neutral model' },
                        { id: 'persist', label: 'Persist metamodel' },
                        { id: 'complete', label: 'Return saved document' }
                    ] : [
                        { id: 'source', label: 'Read source bytes' },
                        { id: 'analysis', label: 'Start staged analysis' }
                    ]}
                />
                <div className="metamodel-import-content">
                    {creationMode === 'GUIDED' ? <HydraGuidedWorkflow title="Guided metamodel import" description="Select the source and HYDRA analyzes it automatically; then review preservation/safety options before loading or persisting the result. The complete import form remains available without changing the selected data." steps={guidedSteps} currentStepId={guidedStep} onStepChange={setGuidedStep} finishLabel={busyOperation === 'SAVE' ? 'Importing…' : 'Import and save'} onFinish={() => save()} /> : <>
                    <RequiredFieldsLegend />
                    <div className="hydra-remote-location-switch" aria-label="Source location"><HydraButton className={sourceLocation === 'LOCAL' ? 'active' : ''} type="button" onClick={() => setSourceLocation('LOCAL')}>Local file</HydraButton><HydraButton className={sourceLocation === 'REMOTE' ? 'active' : ''} type="button" onClick={() => setSourceLocation('REMOTE')}>Remote storage</HydraButton></div>
                    {sourceLocation === 'LOCAL' ? <FormFieldHelp label="Metamodel file" targetId="metamodel-import-file-button" help="Select the semantic, grammar or diagram file to analyze. The file is uploaded only when analysis or import starts." example="biblioteca.ecore, model.ts, model.js, model.go, language.xtext, blocks.blockly.js" required>
                        <input accept={acceptedExtensions} className="metamodel-import-hidden-file" disabled={busy} id="metamodel-import-file-input" ref={fileInputRef} onChange={(event: ChangeEvent<HTMLInputElement>) => void selectFileAndAnalyze(event.target.files?.[0] ?? null)} type="file" />
                        <HydraButton aria-label="File system — select metamodel file" className="metamodel-import-source-button" disabled={busy} id="metamodel-import-file-button" type="button" onClick={() => openFileInputPicker(fileInputRef.current)}><FileIcon/><span><strong>File system</strong><small>{file === null ? 'Select a supported metamodel file from this computer.' : file.name}</small></span></HydraButton>
                    </FormFieldHelp> : <section className="hydra-remote-source-panel"><RemoteStorageProfileFields profiles={remoteProfiles} profileId={remoteProfileId} path={remotePath} disabled={busy} onProfileChange={setRemoteProfileId} onPathChange={setRemotePath} /><HydraButton disabled={busy || remoteProfileId === '' || remotePath.trim() === ''} type="button" onClick={() => void loadRemoteAndAnalyze()}>{busyOperation === 'ANALYZE' ? 'Loading remote metamodel…' : 'Load remote metamodel'}</HydraButton>{file !== null && <small>Loaded file: {file.name}</small>}</section>}
                    <aside className="metamodel-update-site-shortcut">
                        <div><strong>Import an Eclipse update site or p2 repository</strong>
                            <span>Recover Ecore, GenModel, GMF, Sirius and Xtext artifacts together with provenance and confidence.</span></div>
                        <HydraButton type="button" onClick={openUpdateSiteRecovery}>Open update-site recovery</HydraButton>
                    </aside>
                    <div className="metamodel-import-options-grid">
                        <FormFieldHelp label="Format" targetId="metamodel-import-format" help="Choose automatic detection or force a specific importer. Forced formats are useful when extensions are ambiguous." example="Automatic detection" options={[{ value:'auto',label:'Automatic detection',description:'Chooses the importer from the file extension and content.' }, ...formats.map(format => ({ value:format.id,label:format.displayName,description:`${format.supportLevel.toLowerCase()} support for ${format.extensions.join(', ')} files.` }))]}>
                            <HydraAdaptiveSelect showOptionHelp={false} id="metamodel-import-format" ariaLabel="Format" disabled={busy} value={options.formatId} options={[{ value:'auto',label:'Automatic detection',description:'Chooses the importer from the file extension and content.',category:'Recommended' }, ...formats.map(format => ({ value:format.id,label:format.displayName,description:`${format.supportLevel.toLowerCase()} support for ${format.extensions.join(', ')} files.`,category:formatCategory(format.category) }))]} onChange={formatId => { setOptions(current => ({ ...current, formatId })); resetAnalysis(); }} />
                        </FormFieldHelp>
                        <FormFieldHelp label="Target identifier" targetId="metamodel-import-target-id" help="Optional stable identifier assigned to the imported metamodel. Leave it empty to reuse the identifier detected in the source." example="mi-metamodelo">
                            <input id="metamodel-import-target-id" disabled={busy} onChange={(event: ChangeEvent<HTMLInputElement>) => { setOptions(current => ({ ...current, targetId: event.target.value })); resetAnalysis(); }} placeholder="Uses the imported identifier" type="text" value={options.targetId} />
                        </FormFieldHelp>
                    </div>
                    <div className="metamodel-import-checks">
                        <FormFieldHelp label="Preserve layout" targetId="metamodel-import-preserve-layout" help="Keeps imported node positions and edge routes when the source format contains diagram information." example="Enabled for Draw.io or graphical Ecore exports"><label className="metamodel-import-check"><input id="metamodel-import-preserve-layout" checked={options.preserveLayout} disabled={busy} onChange={(event: ChangeEvent<HTMLInputElement>) => updateBoolean('preserveLayout', event.target.checked)} type="checkbox" /> Preserve layout when available</label></FormFieldHelp>
                        <FormFieldHelp label="Preserve visual styles" targetId="metamodel-import-preserve-styles" help="Keeps colors, borders, fonts and other visual information supported by the selected importer." example="Node fill and edge line style"><label className="metamodel-import-check"><input id="metamodel-import-preserve-styles" checked={options.preserveStyles} disabled={busy} onChange={(event: ChangeEvent<HTMLInputElement>) => updateBoolean('preserveStyles', event.target.checked)} type="checkbox" /> Preserve visual styles</label></FormFieldHelp>
                        <FormFieldHelp label="Allow overwrite" targetId="metamodel-import-overwrite" help="Allows the imported document to replace an existing metamodel with the same target identifier when it is finally saved." example="Replace mi-metamodelo"><label className="metamodel-import-check"><input id="metamodel-import-overwrite" checked={options.overwrite} disabled={busy} onChange={(event: ChangeEvent<HTMLInputElement>) => updateBoolean('overwrite', event.target.checked)} type="checkbox" /> Allow overwrite when saving</label></FormFieldHelp>
                        <FormFieldHelp label="Strict mode" targetId="metamodel-import-strict" help="Rejects imports that would lose unsupported semantics instead of converting them with warnings." example="Stop when an Xtext construct cannot be represented"><label className="metamodel-import-check"><input id="metamodel-import-strict" checked={options.strict} disabled={busy} onChange={(event: ChangeEvent<HTMLInputElement>) => updateBoolean('strict', event.target.checked)} type="checkbox" /> Reject semantic loss</label></FormFieldHelp>
                    </div>
                    <HydraButton className="metamodel-import-preview-button" disabled={busy || file === null} onClick={() => void analyze()} type="button">{busyOperation === 'ANALYZE' ? 'Analyzing…' : 'Analyze file'}</HydraButton>
                    {analysisJob !== null && <ImportAnalysisProgress job={analysisJob} onDownloadStage={stageId => void downloadStageLog(stageId)} />}
                    {preview !== null && <ImportPreview preview={preview} />}
                    {error !== '' && <HydraActionableErrorNotice className="metamodel-import-error" context="metamodel import" message={error} />}
                    </>}
                </div>
                <footer className="metamodel-import-actions">
                    <HydraButton disabled={busyOperation === 'SAVE'} onClick={close} type="button">Close</HydraButton>
                    <HydraButton aria-label="Download complete analysis log" className="metamodel-import-log-download" disabled={!canDownloadAllLogs || busyOperation === 'LOG'} onClick={() => void downloadAllLogs()} title="Download the unified log for all analysis stages" type="button">📄 All logs</HydraButton>
                    <HydraButton disabled={busy || preview === null} onClick={() => preview !== null && onLoad(preview)} type="button">Open in designer</HydraButton>
                    <HydraButton className="metamodel-save-button" disabled={busy || file === null} onClick={() => void save()} type="button">{busyOperation === 'SAVE' ? 'Importing…' : 'Import and save'}</HydraButton>
                </footer>
            </section>
        </div>
    );

    if(typeof document === 'undefined') {
        return dialog;
    }

    return createPortal(dialog, document.body);
}

function formatCategory(category: MetamodelImportFormatDescriptor['category']): string {
    if (category === 'SEMANTIC') return 'Semantic models';
    if (category === 'GRAMMAR') return 'Grammar languages';
    if (category === 'DIAGRAM') return 'Diagrams';
    if (category === 'SOURCE_CODE') return 'Source code / block definitions';
    if (category === 'PROJECT') return 'Projects';
    return 'Documentation';
}

interface ImportAnalysisProgressProps {
    job: MetamodelImportAnalysisJob;
    onDownloadStage: (stageId: string) => void;
}

function ImportAnalysisProgress({ job, onDownloadStage }: ImportAnalysisProgressProps) {
    const [now, setNow] = useState(() => Date.now());

    useEffect(() => {
        if(job.status !== 'RUNNING' && job.status !== 'QUEUED') {
            return;
        }

        const interval = window.setInterval(() => setNow(Date.now()), 500);
        return () => window.clearInterval(interval);
    }, [job.status]);

    const stages = effectiveStages(job);
    const runningStage = stages.find(stage => stage.status === 'RUNNING');
    const segmentWidth = job.totalStages > 0 ? 100 / job.totalStages : 0;
    const completedWidth = job.totalStages > 0 ? job.completedStages * 100 / job.totalStages : 0;
    const elapsed = runningStage === undefined ? '' : formatDuration(Math.max(runningStage.durationMillis, now - Date.parse(runningStage.startedAt ?? job.updatedAt)));

    return (
        <section aria-live="polite" className={`metamodel-import-analysis metamodel-import-analysis-${job.status.toLowerCase()}`}>
            <div className="metamodel-import-analysis-heading">
                <div>
                    <h3>Analysis activity</h3>
                    <p>{job.jobId === 'uploading' ? 'Uploading file' : `${job.completedStages} of ${job.totalStages} stages completed`}{elapsed === '' ? '' : ` · current stage ${elapsed}`}</p>
                </div>
                <strong>{job.progress}%</strong>
            </div>
            <div aria-label="Metamodel import analysis progress" aria-valuemax={100} aria-valuemin={0} aria-valuenow={job.progress} className="metamodel-import-stage-progress" role="progressbar">
                <div className="metamodel-import-stage-progress-completed" style={{ width: `${completedWidth}%` }} />
                {(job.status === 'RUNNING' || job.status === 'QUEUED') && <div className="metamodel-import-stage-progress-running" style={{ left: `${completedWidth}%`, width: `${segmentWidth}%` }} />}
            </div>
            {stages.length === 0 && <div className="metamodel-import-uploading"><span /> Uploading the selected file and creating the analysis job…</div>}
            <div className="metamodel-import-stage-list" role="log">
                {stages.map(stage => <ImportStage jobId={job.jobId} key={stage.stageId} onDownload={onDownloadStage} stage={stage} />)}
            </div>
        </section>
    );
}

interface ImportStageProps {
    jobId: string;
    stage: MetamodelImportAnalysisStage;
    onDownload: (stageId: string) => void;
}

function ImportStage({ jobId, stage, onDownload }: ImportStageProps) {
    const severity = highestSeverity(stage.activities);

    return (
        <article className={`metamodel-import-stage metamodel-import-stage-${stage.status.toLowerCase()}`}>
            <div className="metamodel-import-stage-marker">
                <span className={`metamodel-import-stage-severity metamodel-import-stage-severity-${severity.toLowerCase().replace('_', '-')}`}>{severityLabel(severity)}</span>
                <HydraButton aria-label={`Download ${stage.label} log`} disabled={!stage.logAvailable || jobId === 'uploading'} onClick={() => onDownload(stage.stageId)} title={`Download log for ${stage.label}`} type="button">📄⇩</HydraButton>
            </div>
            <div className="metamodel-import-stage-body">
                <header>
                    <div><strong>{stage.sequence}. {stage.label}</strong><span>{stageStatusLabel(stage.status)}</span></div>
                    <small>{formatDuration(stage.durationMillis)}</small>
                </header>
                {stage.activities.map((activity, index) => <ImportActivity activity={activity} key={`${activity.timestamp}-${activity.code}-${index}`} />)}
            </div>
        </article>
    );
}

function ImportActivity({ activity }: { activity: MetamodelImportActivityMessage }) {
    return (
        <div className={`metamodel-import-activity metamodel-import-activity-${activity.severity.toLowerCase().replace('_', '-')}`}>
            <strong>{activity.code}</strong>
            <p data-hydra-runtime-message="true" data-hydra-message-context="import">{activity.message}</p>
        </div>
    );
}

function ImportPreview({ preview }: { preview: MetamodelImportPreview }) {
    const stats = preview.statistics;
    const lossCount = preview.warnings.filter(warning => warning.code.startsWith('IMPORT_LOSSY') || warning.code.startsWith('IMPORT_UNRESOLVED')).length;

    return (
        <section className="metamodel-import-preview">
            <h3>Import preview</h3>
            <p><strong>{preview.metamodel.name}</strong> · {preview.detectedFormat} · {preview.supportLevel}</p>
            <div className="metamodel-import-statistics">
                <span>{stats.classes} classes</span>
                <span>{stats.enums} enums</span>
                <span>{stats.dataTypes} data types</span>
                <span>{stats.attributes} attributes</span>
                <span>{stats.relations} relations</span>
                {lossCount > 0 && <span className="metamodel-import-loss-stat">{lossCount} information-loss message(s)</span>}
            </div>
            {(preview.detectedFormat === 'blockly-json' || preview.detectedFormat === 'blockly-javascript') && <div className="metamodel-blockly-preview-details"><strong>Blockly recovery details</strong><span>{preview.metamodel.annotations['hydra:blockly:blockCount'] ?? stats.classes} block type(s) recovered</span><span>{preview.metamodel.annotations['hydra:blockly:fieldCount'] ?? stats.attributes} field(s) mapped to attributes</span><span>{preview.metamodel.annotations['hydra:blockly:inputCount'] ?? stats.relations} input(s) mapped to relations</span><span>{preview.metamodel.annotations['hydra:blockly:connectionConstraintCount'] ?? '0'} connection check(s) preserved</span><small>JavaScript executed: {preview.metamodel.annotations['hydra:blockly:javascriptExecuted'] ?? 'false'}. Open the preview to edit the neutral classes, attributes and relations before saving.</small></div>}
        </section>
    );
}

function effectiveStages(job: MetamodelImportAnalysisJob): MetamodelImportAnalysisStage[] {
    if(job.stages.length > 0) {
        return [...job.stages].sort((first, second) => first.sequence - second.sequence);
    }

    if(job.activities.length === 0) {
        return [];
    }

    return [{ stageId: job.phase, sequence: 1, label: formatPhase(job.phase), parallelGroup: 'ANALYSIS', status: job.status === 'FAILED' ? 'FAILED' : job.status === 'COMPLETED' ? 'COMPLETED' : 'RUNNING', startedAt: job.createdAt, completedAt: job.status === 'COMPLETED' || job.status === 'FAILED' ? job.updatedAt : null, durationMillis: Math.max(0, Date.parse(job.updatedAt) - Date.parse(job.createdAt)), activities: job.activities, logAvailable: false }];
}

function highestSeverity(activities: MetamodelImportActivityMessage[]): MetamodelImportActivitySeverity {
    if(activities.some(activity => activity.severity === 'ERROR')) {
        return 'ERROR';
    }

    if(activities.some(activity => activity.severity === 'INFORMATION_LOSS')) {
        return 'INFORMATION_LOSS';
    }

    if(activities.some(activity => activity.severity === 'WARNING')) {
        return 'WARNING';
    }

    return 'INFO';
}

function stageStatusLabel(status: MetamodelImportAnalysisStage['status']): string {
    if(status === 'RUNNING') {
        return 'Running';
    }

    if(status === 'COMPLETED') {
        return 'Completed';
    }

    if(status === 'FAILED') {
        return 'Failed';
    }

    if(status === 'SKIPPED') {
        return 'Skipped';
    }

    return 'Pending';
}

function formatPhase(phase: string): string {
    return phase.toLowerCase().split('_').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join(' ');
}

function severityLabel(severity: MetamodelImportActivitySeverity): string {
    if(severity === 'INFORMATION_LOSS') {
        return 'Loss';
    }

    if(severity === 'WARNING') {
        return 'Caution';
    }

    if(severity === 'ERROR') {
        return 'Error';
    }

    return 'Info';
}

function formatDuration(milliseconds: number): string {
    const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}
