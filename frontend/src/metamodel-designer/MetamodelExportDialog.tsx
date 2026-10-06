import { HydraButton } from '../design-system/HydraButton';
import { createPortal } from 'react-dom';
import { useEffect, useMemo, useState } from 'react';
import type { ChangeEvent, Dispatch, MouseEvent, SetStateAction } from 'react';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { HydraActionableErrorNotice, HydraCreationModeSwitch, HydraGuidedWorkflow, HydraOperationProgress, HydraTooltip, HydraWindowControls } from '../design-system';
import { explainInputError } from '../api/inputErrorExplanation';
import { loadRemoteStorageProfiles, RemoteStorageProfileFields, saveRemoteLayerArtifact } from '../remote-storage';
import type { RemoteStorageProfileDescriptor } from '../remote-storage';

import { downloadMetamodelExport, downloadMetamodelExportBundle } from './exportApi';
import { downloadMetamodelProjectExport, listMetamodelProjectExportFormats, loadMetamodelProjectExportArtifacts } from './projectExportApi';
import { loadMetamodelValidationStatus } from './validationApi';
import type { MetamodelExportFormatDescriptor, MetamodelProjectArtifactCatalog, MetamodelProjectArtifactDescriptor, MetamodelProjectArtifactRef, MetamodelProjectExportDescriptor, MetamodelValidationGateStatus } from './types';
import type { MetamodelExportOptions } from './exportTypes';

import './metamodel-export-dialog.css';

interface MetamodelExportDialogProps {
    formats: MetamodelExportFormatDescriptor[];
    metamodelId: string;
    onClose: () => void;
}

const DEFAULT_OPTIONS: MetamodelExportOptions = {
    includeLayout: true,
    includeStyles: true,
    includeWarnings: true,
    transparentBackground: false,
    imageScale: 2,
    codePackageName: ''
};

/**
 * Muestra formatos, opciones y descarga múltiple.
 */
export function MetamodelExportDialog({ formats, metamodelId, onClose }: MetamodelExportDialogProps) {
    const selectableFormats = useMemo(() => formats.filter(format => format.category !== 'BUNDLE'), [formats]);
    const [selectedFormats, setSelectedFormats] = useState<string[]>(() => recommendedFormatSelection(selectableFormats));
    const [options, setOptions] = useState<MetamodelExportOptions>(DEFAULT_OPTIONS);
    const [busyFormat, setBusyFormat] = useState('');
    const [error, setError] = useState('');
    const [maximized, setMaximized] = useState(false);
    const [creationMode, setCreationMode] = useState<'GUIDED' | 'ADVANCED'>('ADVANCED');
    const [exportScope, setExportScope] = useState<'FILES' | 'PROJECTS'>('FILES');
    const [guidedStep, setGuidedStep] = useState('formats');
    const [projectFormats, setProjectFormats] = useState<MetamodelProjectExportDescriptor[]>([]);
    const [projectArtifacts, setProjectArtifacts] = useState<MetamodelProjectArtifactCatalog | null>(null);
    const [validationStatus, setValidationStatus] = useState<MetamodelValidationGateStatus | null>(null);
    const [projectOptionsLoaded, setProjectOptionsLoaded] = useState(false);
    const [projectOptionsLoading, setProjectOptionsLoading] = useState(false);
    const [includeExistingLayers, setIncludeExistingLayers] = useState(true);
    const [artifactSelectionMode, setArtifactSelectionMode] = useState<'ALL_COMPATIBLE' | 'EXPLICIT'>('ALL_COMPATIBLE');
    const [selectedArtifactKeys, setSelectedArtifactKeys] = useState<string[]>([]);
    const [editorId, setEditorId] = useState('');
    const [glspServerLanguage, setGlspServerLanguage] = useState<'java' | 'typescript'>('java');
    const [projectBusy, setProjectBusy] = useState('');
    const [destination, setDestination] = useState<'LOCAL' | 'REMOTE'>('LOCAL');
    const [remoteProfiles, setRemoteProfiles] = useState<RemoteStorageProfileDescriptor[]>([]);
    const [remoteProfileId, setRemoteProfileId] = useState('');
    const [remotePath, setRemotePath] = useState('');
    const [remoteVersion, setRemoteVersion] = useState('');
    const [lastRemotePath, setLastRemotePath] = useState('');

    useEffect(() => {
        if (exportScope !== 'PROJECTS' || projectOptionsLoaded) return undefined;
        let active = true;
        queueMicrotask(() => {
            if (!active) return;
            setProjectOptionsLoading(true);
            void Promise.all([listMetamodelProjectExportFormats(metamodelId), loadMetamodelProjectExportArtifacts(metamodelId), loadMetamodelValidationStatus(metamodelId)])
                .then(([descriptors, catalog, status]) => {
                    if (!active) return;
                    setProjectFormats(descriptors);
                    setProjectArtifacts(catalog);
                    setValidationStatus(status);
                    setArtifactSelectionMode('ALL_COMPATIBLE');
                    setSelectedArtifactKeys(catalog.artifacts.filter(item => !item.editorDefinition).map(artifactKey));
                    const editors = catalog.artifacts.filter(item => item.editorDefinition);
                    setEditorId(editors.length === 1 ? editors[0].documentId : '');
                    setProjectOptionsLoaded(true);
                })
                .catch(exception => {
                    if (!active) return;
                    setError(explainInputError(exception, 'Could not load complete project export options', 'metamodel export'));
                })
                .finally(() => {
                    if (active) setProjectOptionsLoading(false);
                });
        });
        return () => { active = false; };
    }, [exportScope, metamodelId, projectOptionsLoaded]);

    useEffect(() => {
        if(destination !== 'REMOTE' || remoteProfiles.length > 0) return;
        let active = true;
        void loadRemoteStorageProfiles().then(profiles => {
            if(!active) return;
            setRemoteProfiles(profiles);
            setRemoteProfileId(current => current || profiles.find(profile => profile.enabled && profile.configured)?.id || '');
        }).catch(() => { if(active) setRemoteProfiles([]); });
        return () => { active = false; };
    }, [destination, remoteProfiles.length]);

    useEffect(() => {
        function closeWithEscape(event: KeyboardEvent): void {
            if(event.key === 'Escape' && busyFormat === '' && projectBusy === '') {
                onClose();
            }
        }

        window.addEventListener('keydown', closeWithEscape);
        return () => window.removeEventListener('keydown', closeWithEscape);
    }, [busyFormat, projectBusy, onClose]);

    const updateBooleanOption = (name: keyof Pick<MetamodelExportOptions, 'includeLayout' | 'includeStyles' | 'includeWarnings' | 'transparentBackground'>, value: boolean) => {
        setOptions(current => ({ ...current, [name]: value }));
    };

    const toggleFormat = (formatId: string) => {
        setSelectedFormats(current => current.includes(formatId) ? current.filter(item => item !== formatId) : [...current, formatId]);
    };

    const downloadOne = async (formatId: string) => {
        setBusyFormat(formatId);
        setError('');

        try {
            if(destination === 'LOCAL') {
                await downloadMetamodelExport(metamodelId, { format: formatId, options });
            } else {
                requireRemoteDestination();
                const { generateMetamodelExport } = await import('./exportApi');
                const artifact = await generateMetamodelExport(metamodelId, { format: formatId, options });
                const path = resolvedRemotePath(artifact.fileName);
                const stored = await saveRemoteLayerArtifact(remoteProfileId, path, { fileName: artifact.fileName, content: artifact.blob, mimeType: artifact.contentType }, { expectedVersion: path === lastRemotePath ? remoteVersion : '', commitMessage: `Export HYDRA metamodel ${metamodelId} (${formatId})` });
                setLastRemotePath(path);
                setRemoteVersion(stored.version);
            }
        } catch(exception) {
            setError(explainInputError(exception, 'Export failed', 'metamodel export'));
        } finally {
            setBusyFormat('');
        }
    };

    const downloadSelected = async (): Promise<boolean> => {
        if(selectedFormats.length === 0) {
            setError(explainInputError(new Error('At least one export format is required'), 'Select at least one format', 'metamodel export'));
            return false;
        }

        setBusyFormat('bundle');
        setError('');

        try {
            if(destination === 'LOCAL') {
                await downloadMetamodelExportBundle(metamodelId, { formats: selectedFormats, options });
            } else {
                requireRemoteDestination();
                const { generateMetamodelExportBundle } = await import('./exportApi');
                const artifact = await generateMetamodelExportBundle(metamodelId, { formats: selectedFormats, options });
                const path = resolvedRemotePath(artifact.fileName);
                const stored = await saveRemoteLayerArtifact(remoteProfileId, path, { fileName: artifact.fileName, content: artifact.blob, mimeType: artifact.contentType }, { expectedVersion: path === lastRemotePath ? remoteVersion : '', commitMessage: `Export HYDRA metamodel bundle ${metamodelId}` });
                setLastRemotePath(path);
                setRemoteVersion(stored.version);
            }
            return true;
        } catch(exception) {
            setError(explainInputError(exception, 'Export failed', 'metamodel export'));
            return false;
        } finally {
            setBusyFormat('');
        }
    };

    function requireRemoteDestination(): void {
        if(remoteProfileId === '') throw new Error('Choose a configured remote profile before exporting the metamodel.');
    }

    function resolvedRemotePath(fileName: string): string {
        const requested = remotePath.trim();
        if(requested === '') return `metamodels/${metamodelId}/${fileName}`;
        if(requested.endsWith('/')) return `${requested}${fileName}`;
        return requested;
    }

    const downloadProject = async (descriptor: MetamodelProjectExportDescriptor) => {
        setProjectBusy(descriptor.id);setError('');
        try{
            const settings={...descriptor.defaultSettings}; if(descriptor.id==='eclipse-glsp')settings.serverLanguage=glspServerLanguage;
            const selectedArtifacts:MetamodelProjectArtifactRef[]=(projectArtifacts?.artifacts??[]).filter(item=>!item.editorDefinition&&selectedArtifactKeys.includes(artifactKey(item))).map(item=>({kind:item.kind,documentId:item.documentId}));
            await downloadMetamodelProjectExport(metamodelId,descriptor.id,{includeExistingLayers,selectionMode:artifactSelectionMode,selectedArtifacts,editorId,settings});
        }catch(exception){setError(explainInputError(exception, 'Complete project export failed', 'complete project export'));}
        finally{setProjectBusy('');}
    };

    function closeFromBackdrop(event: MouseEvent<HTMLDivElement>): void {
        if(event.target === event.currentTarget && busyFormat === '' && projectBusy === '') {
            onClose();
        }
    }

    const selectedDescriptors = selectableFormats.filter(format => selectedFormats.includes(format.id));
    const selectedCategories = [...new Set(selectedDescriptors.map(format => format.category))];
    const guidedSteps = [
        {
            id: 'formats',
            title: 'Choose outputs',
            description: 'Select the representations of the metamodel that you need.',
            checks: [{ id: 'format-selected', label: 'At least one export format selected', complete: selectedFormats.length > 0, detail: `${selectedFormats.length} selected` }],
            content: <div className="metamodel-export-guided-formats">
                <div className="metamodel-export-guided-intro"><strong>What do you want to use the export for?</strong><span>Select one or more formats. The same options remain available in Advanced / manual mode.</span></div>
                <ExportGroup category="SEMANTIC" formats={selectableFormats} busyFormat={busyFormat} metamodelId={metamodelId} onDownload={downloadOne} onToggle={toggleFormat} selectedFormats={selectedFormats} title="Semantic formats" actionLabel={destination === 'LOCAL' ? 'Download' : 'Save remotely'} />
                <ExportGroup category="GRAMMAR" formats={selectableFormats} busyFormat={busyFormat} metamodelId={metamodelId} onDownload={downloadOne} onToggle={toggleFormat} selectedFormats={selectedFormats} title="Grammar formats" actionLabel={destination === 'LOCAL' ? 'Download' : 'Save remotely'} />
                <ExportGroup category="DOCUMENTATION" formats={selectableFormats} busyFormat={busyFormat} metamodelId={metamodelId} onDownload={downloadOne} onToggle={toggleFormat} selectedFormats={selectedFormats} title="Documentation and AI" actionLabel={destination === 'LOCAL' ? 'Download' : 'Save remotely'} />
                <ExportGroup category="DIAGRAM" formats={selectableFormats} busyFormat={busyFormat} metamodelId={metamodelId} onDownload={downloadOne} onToggle={toggleFormat} selectedFormats={selectedFormats} title="Diagram formats" actionLabel={destination === 'LOCAL' ? 'Download' : 'Save remotely'} />
                <ExportGroup category="CODE" formats={selectableFormats} busyFormat={busyFormat} metamodelId={metamodelId} onDownload={downloadOne} onToggle={toggleFormat} selectedFormats={selectedFormats} title="Code generation" actionLabel={destination === 'LOCAL' ? 'Download' : 'Save remotely'} />
            </div>
        },
        {
            id: 'options',
            title: 'Configure output',
            description: 'Choose layout, style, image and source-code options.',
            checks: [
                { id: 'scale-valid', label: 'Image scale is valid', complete: [1, 2, 4].includes(options.imageScale), detail: `${options.imageScale}×` },
                { id: 'package-valid', label: 'Java package is optional or syntactically safe', complete: options.codePackageName.trim() === '' || /^[A-Za-z_]\w*(\.[A-Za-z_]\w*)*$/.test(options.codePackageName.trim()), detail: options.codePackageName.trim() || 'Namespace prefix will be used' }
            ],
            content: <ExportOptionsPanel options={options} setOptions={setOptions} updateBooleanOption={updateBooleanOption} selectedFormats={selectedFormats} selectableFormats={selectableFormats} busyFormat={busyFormat} onDownloadSelected={async () => { await downloadSelected(); }} onSelectAll={() => setSelectedFormats(selectableFormats.map(format => format.id))} onClear={() => setSelectedFormats([])} showDownload={false} downloadLabel={destination === 'LOCAL' ? 'Download selected' : 'Save selected remotely'} />
        },
        {
            id: 'review',
            title: 'Review package',
            description: 'Verify exactly what HYDRA will generate before creating the ZIP.',
            checks: [
                { id: 'review-formats', label: 'Export selection is ready', complete: selectedFormats.length > 0, detail: selectedDescriptors.map(format => format.displayName).join(', ') || 'No formats' },
                { id: 'review-options', label: 'Options are internally consistent', complete: [1, 2, 4].includes(options.imageScale) && (options.codePackageName.trim() === '' || /^[A-Za-z_]\w*(\.[A-Za-z_]\w*)*$/.test(options.codePackageName.trim())), detail: `${selectedCategories.length} format categor${selectedCategories.length === 1 ? 'y' : 'ies'}` }
            ],
            content: <section className="metamodel-export-guided-review">
                <header><div><h4>Export summary</h4><p>Nothing is persisted or changed in the metamodel. HYDRA only generates files from the current saved state.</p></div><HydraTooltip label="About export review" title="Export review"><p>The bundle contains only the formats selected in step 1. Diagram options affect graphical outputs; code formats generate independent Java, TypeScript, JavaScript, Go and Python source packages from the same neutral metamodel. You can return to Advanced / manual at any time without losing this selection.</p></HydraTooltip></header>
                <dl><div><dt>Metamodel</dt><dd>{metamodelId}</dd></div><div><dt>Formats</dt><dd>{selectedFormats.length}</dd></div><div><dt>Categories</dt><dd>{selectedCategories.join(', ') || 'None'}</dd></div><div><dt>Image scale</dt><dd>{options.imageScale}×</dd></div><div><dt>Layout / styles</dt><dd>{options.includeLayout ? 'Layout included' : 'Layout omitted'} · {options.includeStyles ? 'Styles included' : 'Styles omitted'}</dd></div></dl>
            </section>
        },
        {
            id: 'generate',
            title: 'Generate',
            description: 'Create the selected exports as one traceable ZIP package.',
            checks: [{ id: 'generation-ready', label: 'Package can be generated', complete: selectedFormats.length > 0 && busyFormat === '', detail: busyFormat === '' ? 'Ready' : 'Generation in progress' }],
            content: <section className="metamodel-export-guided-generate"><strong>{selectedFormats.length} format(s) ready</strong><p>Use Finish export below. Individual downloads remain available from Advanced / manual mode.</p></section>
        }
    ];

    const dialog = (
        <div className="metamodel-export-backdrop" onMouseDown={closeFromBackdrop} role="presentation">
            <section aria-labelledby="export-metamodel-title" aria-modal="true" className={`metamodel-export-dialog-window${maximized ? ' maximized' : ''}`} role="dialog">
                <header className="metamodel-export-window-header hydra-window-header">
                    <div className="metamodel-export-window-title">
                        <h2 id="export-metamodel-title">Export metamodel</h2>
                        <p>Exports are generated from the neutral metamodel and the last saved diagram for <strong>{metamodelId}</strong>.</p>
                    </div>
                    <HydraWindowControls closeDisabled={busyFormat !== '' || projectBusy !== ''} closeLabel="Close export dialog" maximizeLabel="Maximize export dialog" maximized={maximized} onClose={onClose} onToggleMaximize={() => setMaximized(current => !current)} restoreLabel="Restore export dialog" />
                </header>
                <div className="metamodel-export-scope-row">
                    <div className="hydra-remote-location-switch" aria-label="Export type">
                        <HydraButton className={exportScope === 'FILES' ? 'active' : ''} type="button" onClick={() => setExportScope('FILES')}>Formats and files</HydraButton>
                        <HydraButton className={exportScope === 'PROJECTS' ? 'active' : ''} type="button" onClick={() => setExportScope('PROJECTS')}>Complete projects</HydraButton>
                    </div>
                    <span>{exportScope === 'FILES' ? 'Export only the representations you need.' : 'Generate a complete technology workspace only when you need it.'}</span>
                </div>
                {exportScope === 'FILES' && <div className="metamodel-export-mode-row"><HydraCreationModeSwitch mode={creationMode} onChange={setCreationMode} guidedLabel="Guided export" advancedLabel="Advanced / individual downloads" /><span>Both modes use the same selection and options.</span></div>}
                <HydraOperationProgress active={busyFormat !== '' || projectBusy !== '' || projectOptionsLoading} compact title={projectOptionsLoading ? 'Loading export options' : 'Generating export'} detail={projectOptionsLoading ? 'Discovering compatible complete-project targets and their required layers…' : destination === 'REMOTE' ? 'Generating the artifact and transferring it to the configured remote destination…' : 'Generating the artifact. The browser download starts as soon as the bytes are ready.'} />
                <section className="metamodel-export-destination"><div className="hydra-remote-location-switch" aria-label="Export destination"><HydraButton className={destination === 'LOCAL' ? 'active' : ''} type="button" onClick={() => setDestination('LOCAL')}>Local file</HydraButton><HydraButton className={destination === 'REMOTE' ? 'active' : ''} type="button" onClick={() => setDestination('REMOTE')}>Remote storage</HydraButton></div>{destination === 'REMOTE' && <><RemoteStorageProfileFields profiles={remoteProfiles} profileId={remoteProfileId} path={remotePath} disabled={busyFormat !== '' || projectBusy !== ''} onProfileChange={value => { setRemoteProfileId(value); setRemoteVersion(''); setLastRemotePath(''); }} onPathChange={value => { setRemotePath(value); setRemoteVersion(''); setLastRemotePath(''); }} />{lastRemotePath !== '' && <small>Last remote artifact: {lastRemotePath}{remoteVersion === '' ? '' : ` · ${remoteVersion}`}</small>}<small>Leave the remote path empty to use metamodels/{metamodelId}/&lt;generated file&gt;.</small></>}</section>
                {exportScope === 'PROJECTS' ? <div className="metamodel-export-content metamodel-export-project-content">
                    {projectOptionsLoading && <div className="metamodel-export-loading" role="status">Loading complete-project options…</div>}
                    {!projectOptionsLoading && <CompleteProjectExports formats={projectFormats} artifacts={projectArtifacts} status={validationStatus} busy={projectBusy} includeExistingLayers={includeExistingLayers} selectionMode={artifactSelectionMode} selectedArtifactKeys={selectedArtifactKeys} editorId={editorId} glspServerLanguage={glspServerLanguage} onIncludeExistingLayersChange={setIncludeExistingLayers} onSelectionModeChange={setArtifactSelectionMode} onSelectedArtifactKeysChange={setSelectedArtifactKeys} onEditorIdChange={setEditorId} onGlspServerLanguageChange={setGlspServerLanguage} onDownload={downloadProject} />}
                </div> : <div className={`metamodel-export-content${creationMode === 'ADVANCED' ? ' metamodel-export-layout' : ' metamodel-export-guided-content'}`}>
                    {creationMode === 'GUIDED' ? <HydraGuidedWorkflow title="Export metamodel step by step" description="Choose the outputs, validate their options and confirm each required step before generating the package." steps={guidedSteps} currentStepId={guidedStep} onStepChange={setGuidedStep} onFinish={downloadSelected} finishLabel={busyFormat === 'bundle' ? 'Generating ZIP…' : 'Finish export'} /> : <>
                        <div className="metamodel-export-formats">
                            <ExportGroup category="SEMANTIC" formats={selectableFormats} busyFormat={busyFormat} metamodelId={metamodelId} onDownload={downloadOne} onToggle={toggleFormat} selectedFormats={selectedFormats} title="Semantic formats" actionLabel={destination === 'LOCAL' ? 'Download' : 'Save remotely'} />
                            <ExportGroup category="GRAMMAR" formats={selectableFormats} busyFormat={busyFormat} metamodelId={metamodelId} onDownload={downloadOne} onToggle={toggleFormat} selectedFormats={selectedFormats} title="Grammar formats" actionLabel={destination === 'LOCAL' ? 'Download' : 'Save remotely'} />
                            <ExportGroup category="DOCUMENTATION" formats={selectableFormats} busyFormat={busyFormat} metamodelId={metamodelId} onDownload={downloadOne} onToggle={toggleFormat} selectedFormats={selectedFormats} title="Documentation and AI" actionLabel={destination === 'LOCAL' ? 'Download' : 'Save remotely'} />
                            <ExportGroup category="DIAGRAM" formats={selectableFormats} busyFormat={busyFormat} metamodelId={metamodelId} onDownload={downloadOne} onToggle={toggleFormat} selectedFormats={selectedFormats} title="Diagram formats" actionLabel={destination === 'LOCAL' ? 'Download' : 'Save remotely'} />
                            <ExportGroup category="CODE" formats={selectableFormats} busyFormat={busyFormat} metamodelId={metamodelId} onDownload={downloadOne} onToggle={toggleFormat} selectedFormats={selectedFormats} title="Code generation" actionLabel={destination === 'LOCAL' ? 'Download' : 'Save remotely'} />
                        </div>
                        <ExportOptionsPanel options={options} setOptions={setOptions} updateBooleanOption={updateBooleanOption} selectedFormats={selectedFormats} selectableFormats={selectableFormats} busyFormat={busyFormat} onDownloadSelected={async () => { await downloadSelected(); }} onSelectAll={() => setSelectedFormats(selectableFormats.map(format => format.id))} onClear={() => setSelectedFormats([])} showDownload downloadLabel={destination === 'LOCAL' ? 'Download selected' : 'Save selected remotely'} />
                    </>}
                </div>}
                {error !== '' && <HydraActionableErrorNotice className="metamodel-export-error" context="metamodel export" message={error} />}
                <footer className="metamodel-export-actions">
                    <HydraButton disabled={busyFormat !== ''} type="button" onClick={onClose}>Close</HydraButton>
                </footer>
            </section>
        </div>
    );

    if(typeof document === 'undefined') {
        return dialog;
    }

    return createPortal(dialog, document.body);

}


function recommendedFormatSelection(formats: readonly MetamodelExportFormatDescriptor[]): string[] {
    const preferred = ['ecore', 'json', 'drawio'];
    const available = new Set(formats.map(format => format.id));
    const selected = preferred.filter(id => available.has(id));
    return selected.length > 0 ? selected : formats.slice(0, 1).map(format => format.id);
}

interface CompleteProjectExportsProps { formats: MetamodelProjectExportDescriptor[]; artifacts: MetamodelProjectArtifactCatalog | null; status: MetamodelValidationGateStatus | null; busy: string; includeExistingLayers: boolean; selectionMode:'ALL_COMPATIBLE'|'EXPLICIT'; selectedArtifactKeys:string[]; editorId:string; glspServerLanguage: 'java' | 'typescript'; onIncludeExistingLayersChange: (value:boolean)=>void; onSelectionModeChange:(value:'ALL_COMPATIBLE'|'EXPLICIT')=>void; onSelectedArtifactKeysChange:(value:string[])=>void; onEditorIdChange:(value:string)=>void; onGlspServerLanguageChange:(value:'java'|'typescript')=>void; onDownload:(descriptor:MetamodelProjectExportDescriptor)=>Promise<void>; }
function CompleteProjectExports({formats,artifacts,status,busy,includeExistingLayers,selectionMode,selectedArtifactKeys,editorId,glspServerLanguage,onIncludeExistingLayersChange,onSelectionModeChange,onSelectedArtifactKeysChange,onEditorIdChange,onGlspServerLanguageChange,onDownload}:CompleteProjectExportsProps){
    const ready=status?.generationReady===true; const available=artifacts?.artifacts??[]; const layerArtifacts=available.filter(item=>!item.editorDefinition); const editorArtifacts=available.filter(item=>item.editorDefinition);
    const toggleArtifact=(artifact:MetamodelProjectArtifactDescriptor)=>{const key=artifactKey(artifact);const current=selectionMode==='ALL_COMPATIBLE'?layerArtifacts.map(artifactKey):selectedArtifactKeys;onSelectionModeChange('EXPLICIT');onSelectedArtifactKeysChange(current.includes(key)?current.filter(item=>item!==key):[...current,key]);};
    const editorRequiredButAmbiguous=(format:MetamodelProjectExportDescriptor)=>format.requiresEditorDefinition&&editorArtifacts.length>1&&editorId==='';
    return <section className="metamodel-complete-projects"><header><div><h3>Complete generated projects</h3><p>Generate full, technology-specific ZIP workspaces after validating the current saved metamodel.</p></div><span className={`metamodel-generation-gate ${ready?'ready':'blocked'}`}>{ready?'VALIDATED · READY':'VALIDATION REQUIRED'}</span></header>
        {!ready&&<div className="metamodel-generation-warning" role="status"><strong>Metamodel validation is required before complete project generation.</strong><span>{status?.message ?? 'Run Validate metamodel in the Metamodel Designer. A successful validation is tied to the current saved metamodel state.'}</span>{status?.stale&&<small>The metamodel changed after its last successful validation. Validate it again.</small>}</div>}
        {ready&&<div className="metamodel-generation-ready"><strong>Generation gate passed.</strong><span>{status?.summary.errors ?? 0} error(s), {status?.summary.warnings ?? 0} warning(s). Structural validation of the generated project will run again before HYDRA creates the ZIP.</span></div>}
        <div className="metamodel-project-options"><label><input type="checkbox" checked={includeExistingLayers} onChange={event=>onIncludeExistingLayersChange(event.target.checked)}/> Include existing HYDRA layers</label><label>GLSP server<HydraAdaptiveSelect ariaLabel="GLSP server language" value={glspServerLanguage} options={[{value:'java',label:'Java',description:'Generate the GLSP server as a Maven/Java project.',category:'Server'},{value:'typescript',label:'TypeScript',description:'Generate the GLSP server as a Node/TypeScript package.',category:'Server'}]} onChange={value=>onGlspServerLanguageChange(value as 'java'|'typescript')}/></label></div>
        {includeExistingLayers&&<section className="metamodel-project-artifact-selection"><header><div><strong>HYDRA layers included in the workspace</strong><span>{selectionMode==='ALL_COMPATIBLE'?'All compatible documents are included.':'Only the checked documents are included.'}</span></div><HydraButton type="button" onClick={()=>{onSelectionModeChange('ALL_COMPATIBLE');onSelectedArtifactKeysChange(layerArtifacts.map(artifactKey));}}>Include all compatible</HydraButton></header>{layerArtifacts.length===0?<small>No additional HYDRA layers are stored for this metamodel.</small>:<div className="metamodel-project-artifact-grid">{layerArtifacts.map(item=><label key={artifactKey(item)}><input type="checkbox" checked={selectionMode==='ALL_COMPATIBLE'||selectedArtifactKeys.includes(artifactKey(item))} onChange={()=>toggleArtifact(item)}/><span><strong>{item.displayName}</strong><small>{item.kind} · {item.documentId}{item.etag?` · ${item.etag.slice(0,12)}`:''}</small></span></label>)}</div>}</section>}
        {editorArtifacts.length>0&&<label className="metamodel-project-editor-selection">Editor definition<HydraAdaptiveSelect ariaLabel="Editor definition for complete project" value={editorId} options={[...(editorArtifacts.length>1?[{value:'',label:'Choose an editor…',description:'Required for graphical targets when more than one editor is stored.',category:'Selection'}]:[]),...editorArtifacts.map(item=>({value:item.documentId,label:item.displayName,description:item.documentId,category:'Stored editors'}))]} onChange={onEditorIdChange}/><small>{editorArtifacts.length>1?'GLSP and BESSER Web require an explicit editor selection; HYDRA will not silently choose the first one.':'The stored editor will be used by graphical targets.'}</small></label>}
        {editorArtifacts.length===0&&<div className="metamodel-project-derived-editor"><strong>No stored editor definition.</strong><span>Targets that require one will receive HYDRA's safe derived diagram definition.</span></div>}
        <div className="metamodel-project-export-grid">{formats.map(format=>{const ambiguous=editorRequiredButAmbiguous(format);return <article key={format.id}><div><strong>{format.displayName}</strong><small>{format.technology} · {format.extension}</small><p>{format.description}</p>{format.requiresEditorDefinition&&<span>{ambiguous?'Choose the editor definition before generating this graphical target.':'Uses the selected editor definition; HYDRA derives a safe default only when none is stored.'}</span>}</div><HydraButton type="button" disabled={!ready||busy!==''||ambiguous} onClick={()=>void onDownload(format)}>{busy===format.id?'Generating and validating…':'Generate complete ZIP'}</HydraButton></article>;})}</div>
    </section>;
}

function artifactKey(artifact:Pick<MetamodelProjectArtifactDescriptor,'kind'|'documentId'>):string{return `${artifact.kind}:${artifact.documentId}`;}

interface ExportOptionsPanelProps {
    options: MetamodelExportOptions;
    setOptions: Dispatch<SetStateAction<MetamodelExportOptions>>;
    updateBooleanOption: (name: keyof Pick<MetamodelExportOptions, 'includeLayout' | 'includeStyles' | 'includeWarnings' | 'transparentBackground'>, value: boolean) => void;
    selectedFormats: string[];
    selectableFormats: MetamodelExportFormatDescriptor[];
    busyFormat: string;
    onDownloadSelected: () => Promise<void>;
    onSelectAll: () => void;
    onClear: () => void;
    showDownload: boolean;
    downloadLabel?: string;
}

function ExportOptionsPanel({ options, setOptions, updateBooleanOption, selectedFormats, selectableFormats, busyFormat, onDownloadSelected, onSelectAll, onClear, showDownload, downloadLabel = 'Download selected' }: ExportOptionsPanelProps) {
    return <aside className="metamodel-export-options">
        <h3>Export options</h3>
        <label><input checked={options.includeLayout} onChange={(event: ChangeEvent<HTMLInputElement>) => updateBooleanOption('includeLayout', event.target.checked)} type="checkbox" /> Include saved layout</label>
        <label><input checked={options.includeStyles} onChange={(event: ChangeEvent<HTMLInputElement>) => updateBooleanOption('includeStyles', event.target.checked)} type="checkbox" /> Include visual styles</label>
        <label><input checked={options.includeWarnings} onChange={(event: ChangeEvent<HTMLInputElement>) => updateBooleanOption('includeWarnings', event.target.checked)} type="checkbox" /> Include warnings</label>
        <label><input checked={options.transparentBackground} onChange={(event: ChangeEvent<HTMLInputElement>) => updateBooleanOption('transparentBackground', event.target.checked)} type="checkbox" /> Transparent image background</label>
        <label>Image scale
            <HydraAdaptiveSelect ariaLabel="Image scale" value={String(options.imageScale)} options={[
                { value: '1', label: '1×', description: "Exports at the diagram's logical size for compact files.", category: 'Standard' },
                { value: '2', label: '2×', description: 'Exports at double resolution and is the recommended balance for documents and slides.', category: 'Recommended' },
                { value: '4', label: '4×', description: 'Exports at very high resolution for large-format or detailed output.', category: 'High resolution' }
            ]} onChange={next => setOptions(current => ({ ...current, imageScale: Number(next) }))} />
        </label>
        <label>Code package / module<input onChange={(event: ChangeEvent<HTMLInputElement>) => setOptions(current => ({ ...current, codePackageName: event.target.value }))} placeholder="Uses namespace prefix" type="text" value={options.codePackageName} /></label>
        <div className="metamodel-export-selection-actions"><HydraButton onClick={onSelectAll} type="button">Select all</HydraButton><HydraButton onClick={onClear} type="button">Clear</HydraButton><span>{selectedFormats.length} of {selectableFormats.length} formats selected</span></div>
        {showDownload && <HydraButton className="metamodel-export-bundle-button" disabled={busyFormat !== '' || selectedFormats.length === 0} onClick={() => void onDownloadSelected()} type="button">{busyFormat === 'bundle' ? 'Generating ZIP…' : `${downloadLabel} (${selectedFormats.length})`}</HydraButton>}
        <p className="metamodel-export-note">PlantUML arranges the diagram automatically. draw.io, SVG and PNG can preserve the saved canvas layout.</p>
    </aside>;
}

interface ExportGroupProps {
    actionLabel: string;
    busyFormat: string;
    category: MetamodelExportFormatDescriptor['category'];
    formats: MetamodelExportFormatDescriptor[];
    metamodelId: string;
    onDownload: (formatId: string) => Promise<void>;
    onToggle: (formatId: string) => void;
    selectedFormats: string[];
    title: string;
}

/**
 * Agrupa formatos relacionados.
 */
function ExportGroup({ actionLabel, busyFormat, category, formats, metamodelId, onDownload, onToggle, selectedFormats, title }: ExportGroupProps) {
    const groupFormats = formats.filter(format => format.category === category);

    if(groupFormats.length === 0) {
        return null;
    }

    return (
        <section className="metamodel-export-group">
            <h3>{title}</h3>
            <div className="metamodel-export-grid">
                {groupFormats.map(format => {
                    const directExportUrl = `/api/metamodels/${encodeURIComponent(metamodelId)}/exports/${encodeURIComponent(format.id)}`;

                    return (
                        <article className="metamodel-export-option metamodel-export-option-selectable" data-export-url={directExportUrl} key={format.id}>
                            <label>
                                <input checked={selectedFormats.includes(format.id)} onChange={() => onToggle(format.id)} type="checkbox" />
                                <span><strong>{format.displayName}</strong><small>{format.extension}</small></span>
                            </label>
                            <HydraButton data-export-url={directExportUrl} disabled={busyFormat !== ''} onClick={() => void onDownload(format.id)} type="button">{busyFormat === format.id ? 'Generating…' : actionLabel}</HydraButton>
                        </article>
                    );
                })}
            </div>
        </section>
    );
}
