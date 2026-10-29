import { HydraButton } from '../design-system/HydraButton';
import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { FileIcon, FolderIcon, FolderTreeIcon } from '../components/AppIcons';
import { FormFieldHelp, RequiredFieldsLegend } from '../components/FormFieldHelp';
import { loadRemoteStorageProfiles } from '../remote-storage/api';
import type { RemoteStorageProfileDescriptor } from '../remote-storage/types';
import { analyzeExternalProject, getProjectCreationCatalog, pickProjectPath } from './projectApi';
import type { ExternalProjectRecoveryReport } from './projectApi';
import type { ProjectCreationCatalog, ProjectExperienceMode, ProjectPreset, ProjectSummary } from './types';
import { HydraActionableErrorNotice, HydraWindowControls, HydraSelect, HydraTooltip, openFileInputPicker } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { projectCapabilityLabel, projectPresetDescription, projectPresetGuidance, projectPresetLabel } from './projectPresentation';

interface ProjectManagerDialogProps {
    open: boolean;
    initialMode?: DialogMode;
    initialAccessSource?: AccessSource;
    initialPreset?: ProjectPreset;
    initialEntryPoint?: ProjectManagerEntryPoint;
    recentProjects: ProjectSummary[];
    onClose: () => void;
    onCreate: (request: { parentDirectory: string; projectId: string; name: string; description: string; initializeFromCurrentWorkspace: boolean; preset: ProjectPreset; enabledCapabilities: string[] | null; experienceMode: ProjectExperienceMode }) => Promise<void>;
    onOpenProject: (directory: string, loadIntoWorkspace: boolean) => Promise<void>;
    onImport: (parent: string, name: string, file: File) => Promise<void>;
    onImportDirectory?: (source: string, parent: string, name: string) => Promise<void>;
    onImportBrowserDirectory?: (files: File[], relativePaths: string[], parent: string, name: string) => Promise<void>;
    onImportRemote?: (request: { profileId: string; remotePath: string; parentDirectory: string; projectName: string }) => Promise<void>;
    onImportExternal?: (parent: string, name: string, file: File, confirmedFingerprint: string) => Promise<void>;
}

type DialogMode = 'CREATE' | 'ACCESS';
type AccessSource = 'LOCAL' | 'ZIP' | 'DIRECTORY' | 'REPOSITORY' | 'EXTERNAL';
type ProjectManagerEntryPoint = 'GENERAL' | 'HOME_NEW_PROJECT' | 'HOME_OPEN_PROJECT' | 'HOME_IMPORT_PROJECT' | 'HOME_CONNECT_PROJECT' | 'HOME_QUICK_PRESET';

export function ProjectManagerDialog(props: ProjectManagerDialogProps) {
    return props.open ? <ProjectManagerDialogContent {...props} /> : null;
}

function ProjectManagerDialogContent({ recentProjects, onClose, onCreate, onOpenProject, onImport, onImportDirectory, onImportBrowserDirectory, onImportRemote, onImportExternal, initialMode = 'CREATE', initialAccessSource = 'LOCAL', initialPreset = 'EMPTY', initialEntryPoint = 'GENERAL' }: ProjectManagerDialogProps) {
    const { t } = useHydraLanguage();
    const [mode, setMode] = useState<DialogMode>(initialMode);
    const [accessSource, setAccessSource] = useState<AccessSource>(initialAccessSource);
    const [parent, setParent] = useState('');
    const [directory, setDirectory] = useState('');
    const [name, setName] = useState('');
    const [projectId, setProjectId] = useState('');
    const [description, setDescription] = useState('');
    const [creationCatalog, setCreationCatalog] = useState<ProjectCreationCatalog | null>(null);
    const [catalogError, setCatalogError] = useState('');
    const [preset, setPreset] = useState<ProjectPreset>(initialPreset);
    const [experienceMode, setExperienceMode] = useState<ProjectExperienceMode>('GUIDED');
    const [customizeCapabilities, setCustomizeCapabilities] = useState(false);
    const [showPresetChooser, setShowPresetChooser] = useState(initialEntryPoint !== 'HOME_QUICK_PRESET');
    const [selectedCapabilities, setSelectedCapabilities] = useState<Set<string>>(new Set());
    const [loadWorkspace, setLoadWorkspace] = useState(true);
    const [initializeWorkspace, setInitializeWorkspace] = useState(false);
    const [file, setFile] = useState<File | null>(null);
    const [sourceDirectory, setSourceDirectory] = useState('');
    const [browserDirectoryFiles, setBrowserDirectoryFiles] = useState<File[]>([]);
    const [browserDirectoryPaths, setBrowserDirectoryPaths] = useState<string[]>([]);
    const [remoteProfiles, setRemoteProfiles] = useState<RemoteStorageProfileDescriptor[]>([]);
    const [remoteProfileId, setRemoteProfileId] = useState('');
    const [remotePath, setRemotePath] = useState('');
    const [externalRecovery, setExternalRecovery] = useState<ExternalProjectRecoveryReport | null>(null);
    const [externalAnalyzing, setExternalAnalyzing] = useState(false);
    const [busy, setBusy] = useState(false);
    const [pickerBusy, setPickerBusy] = useState(false);
    const [error, setError] = useState('');
    const [pickerMessage, setPickerMessage] = useState('');
    const zipInputRef = useRef<HTMLInputElement | null>(null);
    const directoryInputRef = useRef<HTMLInputElement | null>(null);
    const initialPresetRef = useRef<ProjectPreset>(initialPreset);

    useEffect(() => {
        let active = true;
        void getProjectCreationCatalog().then(catalog => {
            if (!active) return;
            if (!isProjectCreationCatalog(catalog)) {
                setCreationCatalog(null);
                setCatalogError(t('Could not load project templates'));
                return;
            }
            setCatalogError('');
            setCreationCatalog(catalog);
            const selected = catalog.presets.find(item => item.id === initialPresetRef.current) ?? catalog.presets[0];
            if (selected !== undefined) {
                setSelectedCapabilities(new Set(selected.capabilities));
                setExperienceMode(projectPresetGuidance(selected.id).recommendedMode);
            }
        }).catch(exception => {
            if (active) setCatalogError(exception instanceof Error ? exception.message : t('Could not load project templates'));
        });
        return () => { active = false; };
    }, [t]);

    useEffect(() => {
        if (mode !== 'ACCESS' || accessSource !== 'REPOSITORY') return;
        let active = true;
        void loadRemoteStorageProfiles().then(nextProfiles => {
            if (!active) return;
            setRemoteProfiles(nextProfiles);
            const preferred = nextProfiles.find(profile => profile.enabled && profile.configured) ?? nextProfiles[0];
            if (preferred !== undefined) setRemoteProfileId(current => current === '' ? preferred.id : current);
        }).catch(exception => {
            if (!active) return;
            setError(current => current !== '' ? current : exception instanceof Error ? exception.message : t('Could not load repository profiles'));
        });
        return () => { active = false; };
    }, [accessSource, mode, t]);


    async function submit(): Promise<void> {
        setError('');
        if (mode === 'ACCESS' && (accessSource === 'ZIP' || accessSource === 'EXTERNAL') && file === null) { setError(t('Select a ZIP file')); return; }
        if (mode === 'ACCESS' && accessSource === 'DIRECTORY' && sourceDirectory === '' && browserDirectoryFiles.length === 0) { setError(t('Select a project directory')); return; }
        if (mode === 'ACCESS' && accessSource === 'REPOSITORY' && remoteProfileId === '') { setError(t('Select a repository profile')); return; }
        if (mode === 'ACCESS' && accessSource === 'REPOSITORY' && remotePath.trim() === '') { setError(t('Enter the remote project path')); return; }
        setBusy(true);
        try {
            if (mode === 'CREATE') {
                await onCreate({ parentDirectory: parent, projectId, name, description, initializeFromCurrentWorkspace: initializeWorkspace, preset, enabledCapabilities: customizeCapabilities ? Array.from(selectedCapabilities) : null, experienceMode });
            } else if (accessSource === 'LOCAL') {
                await onOpenProject(directory, loadWorkspace);
            } else if (accessSource === 'ZIP') {
                await onImport(parent, name, file as File);
            } else if (accessSource === 'EXTERNAL') {
                if (onImportExternal === undefined) throw new Error(t('External project import is not available'));
                if (externalRecovery === null) throw new Error(t('Analyze the external project before importing it'));
                await onImportExternal(parent, name, file as File, externalRecovery.fingerprint);
            } else if (accessSource === 'DIRECTORY') {
                if (browserDirectoryFiles.length > 0) {
                    if (onImportBrowserDirectory === undefined) throw new Error(t('Directory import is not available'));
                    await onImportBrowserDirectory(browserDirectoryFiles, browserDirectoryPaths, parent, name);
                } else {
                    if (onImportDirectory === undefined) throw new Error(t('Directory import is not available'));
                    await onImportDirectory(sourceDirectory, parent, name);
                }
            } else {
                if (onImportRemote === undefined) throw new Error(t('Repository import is not available'));
                await onImportRemote({ profileId: remoteProfileId, remotePath: remotePath.trim(), parentDirectory: parent, projectName: name });
            }
            onClose();
        } catch (exception) {
            setError(exception instanceof Error ? exception.message : t('Project operation failed'));
        } finally {
            setBusy(false);
        }
    }

    async function browseDirectory(target: 'PARENT' | 'PROJECT' | 'SOURCE'): Promise<void> {
        if (target === 'SOURCE') {
            setError('');
            setPickerMessage(t('Choose the project folder from this computer. HYDRA imports its contents without receiving your local absolute path.'));
            openFileInputPicker(directoryInputRef.current);
            return;
        }
        setPickerMessage(t('Opening the operating-system directory selector…'));
        setError('');
        setPickerBusy(true);
        try {
            const initialDirectory = target === 'PARENT' ? parent : directory;
            const title = target === 'PARENT' ? t('Select parent directory') : t('Select project directory');
            const result = await pickProjectPath('DIRECTORY', initialDirectory, title);
            if (!result.available) {
                setPickerMessage(t(result.message));
                return;
            }
            if (result.cancelled) {
                setPickerMessage(t('Directory selection cancelled.'));
                return;
            }
            if (target === 'PARENT') setParent(result.selectedPath);
            else setDirectory(result.selectedPath);
            setPickerMessage(`${t('Selected directory')}: ${result.selectedPath}`);
        } catch (exception) {
            setPickerMessage(exception instanceof Error ? exception.message : t('Could not open the directory selector'));
        } finally {
            setPickerBusy(false);
        }
    }

    function browseZipArchive(): void {
        setPickerMessage(t('Opening the operating-system file selector…'));
        openFileInputPicker(zipInputRef.current);
    }

    function selectZipArchive(event: ChangeEvent<HTMLInputElement>): void {
        const selectedFile = event.target.files?.[0] ?? null;
        setFile(selectedFile);
        setExternalRecovery(null);
        setPickerMessage(selectedFile === null ? t('No ZIP file selected.') : `${t('Selected archive')}: ${selectedFile.name}`);
    }

    async function analyzeExternal(): Promise<void> {
        if (file === null) { setError(t('Select a ZIP file')); return; }
        setExternalAnalyzing(true); setError('');
        try {
            const report = await analyzeExternalProject(file);
            setExternalRecovery(report);
            setPickerMessage(`${t('Analysis complete')} · ${report.resourceCount} ${t('resources')} · ${report.technologies.length} ${t('technologies')}`);
        } catch (exception) {
            setExternalRecovery(null);
            setError(exception instanceof Error ? exception.message : t('Could not analyze the external project'));
        } finally { setExternalAnalyzing(false); }
    }

    function selectBrowserDirectory(event: ChangeEvent<HTMLInputElement>): void {
        const files = Array.from(event.target.files ?? []);
        const paths = files.map(file => ((file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name).replace(/\\/g, '/'));
        setBrowserDirectoryFiles(files);
        setBrowserDirectoryPaths(paths);
        const root = paths[0]?.split('/')[0] ?? '';
        setSourceDirectory(root === '' ? '' : root);
        if (name.trim() === '' && root !== '') setName(root);
        setPickerMessage(files.length === 0 ? t('Directory selection cancelled.') : `${t('Selected directory')}: ${root || t('Local folder')} · ${files.length} ${t('file(s)')}`);
        event.target.value = '';
    }

    async function browseProjectDescriptor(): Promise<void> {
        setPickerMessage(t('Opening the operating-system file selector…'));
        setError('');
        setPickerBusy(true);
        try {
            const result = await pickProjectPath('PROJECT_DESCRIPTOR', directory, t('Select modeling-project.json'));
            if (!result.available) {
                setPickerMessage(t(result.message));
                return;
            }
            if (result.cancelled) {
                setPickerMessage(t('File selection cancelled.'));
                return;
            }
            setDirectory(result.selectedDirectory);
            setPickerMessage(`${t('Selected project')}: ${result.selectedPath}`);
        } catch (exception) {
            setPickerMessage(exception instanceof Error ? exception.message : t('Could not open the file selector'));
        } finally {
            setPickerBusy(false);
        }
    }

    function selectPreset(nextPreset: ProjectPreset): void {
        setPreset(nextPreset);
        setCustomizeCapabilities(false);
        setExperienceMode(projectPresetGuidance(nextPreset).recommendedMode);
        const selected = creationCatalog?.presets?.find(item => item.id === nextPreset);
        if (selected !== undefined) setSelectedCapabilities(new Set(selected.capabilities));
    }

    function toggleCapabilityCustomization(): void {
        if (customizeCapabilities) {
            const selected = creationCatalog?.presets?.find(item => item.id === preset);
            if (selected !== undefined) setSelectedCapabilities(new Set(selected.capabilities));
            setCustomizeCapabilities(false);
            return;
        }
        setCustomizeCapabilities(true);
    }

    function toggleCapability(capability: string): void {
        setCustomizeCapabilities(true);
        setSelectedCapabilities(current => {
            const next = new Set(current);
            if (next.has(capability)) next.delete(capability); else next.add(capability);
            // Client-side dependency closure mirrors backend for immediate feedback.
            let changed = true;
            while (changed) {
                changed = false;
                for (const descriptor of creationCatalog?.capabilities ?? []) {
                    if (!next.has(descriptor.id)) continue;
                    for (const dependency of descriptor.requires) if (!next.has(dependency)) { next.add(dependency); changed = true; }
                }
            }
            return next;
        });
    }

    const selectedRemoteProfile = remoteProfiles.find(profile => profile.id === remoteProfileId);
    const repositoryProfilesAvailable = remoteProfiles.filter(profile => profile.enabled && profile.configured);
    const presetGuidance = projectPresetGuidance(preset);
    const selectedPresetDescriptor = creationCatalog?.presets?.find(item => item.id === preset);
    const submitLabel = mode === 'CREATE' ? t('Create project') : accessSource === 'LOCAL' ? t('Open project') : accessSource === 'EXTERNAL' ? t('Import analyzed project') : t('Import project');
    const allowModeSwitching = initialEntryPoint === 'GENERAL';
    const showAccessSourceSelector = initialEntryPoint === 'GENERAL' || initialEntryPoint === 'HOME_IMPORT_PROJECT';
    const createSummaryTitle = initialEntryPoint === 'HOME_QUICK_PRESET' ? t(presetGuidance.creationTitle) : t('Create a HYDRA Project');
    const createSummaryBody = initialEntryPoint === 'HOME_QUICK_PRESET'
        ? t(presetGuidance.summary)
        : t('Choose what you want to build. This only prepares the starting point: you can add or remove project areas later.');
    const accessSummaryTitle = accessSource === 'LOCAL'
        ? t('Open a local project')
        : accessSource === 'REPOSITORY'
            ? t('Connect to a repository or cloud space')
            : accessSource === 'EXTERNAL'
                ? t('Import and recover an external project')
                : accessSource === 'DIRECTORY'
                    ? t('Import a project directory')
                    : t('Import a project archive');
    const accessSummaryBody = accessSource === 'LOCAL'
        ? t('Open a project that already exists on this computer. HYDRA asks only for the path and the loading behavior.')
        : accessSource === 'REPOSITORY'
            ? t('Bring a portable HYDRA project from a repository or cloud profile already configured in HYDRA.')
            : accessSource === 'EXTERNAL'
                ? t('Analyze a project created with another modeling technology and recover the supported layers safely before importing it.')
                : accessSource === 'DIRECTORY'
                    ? t('Copy a complete local project folder into HYDRA without modifying the source folder.')
                    : t('Import a portable HYDRA ZIP archive into a local project directory.');

    return (
        <div className="project-dialog-backdrop" role="presentation">
            <section aria-modal="true" className="project-dialog project-manager-dialog" role="dialog">
                <header><div><h2>{t('Projects')}</h2><p>{t('Create a project or bring one into HYDRA from the local file system or a configured repository.')}</p></div><HydraWindowControls closeLabel={t('Close project dialog')} onClose={onClose} /></header>
                {allowModeSwitching && <nav>
                    <HydraButton className={mode === 'CREATE' ? 'active' : ''} type="button" onClick={() => setMode('CREATE')}>{t('New')}</HydraButton>
                    <HydraButton className={mode === 'ACCESS' ? 'active' : ''} type="button" onClick={() => setMode('ACCESS')}>{t('Open / import')}</HydraButton>
                </nav>}
                <div className="project-dialog-scroll">
                    {mode === 'CREATE' && <div className="project-dialog-form">
                        <RequiredFieldsLegend />
                        {pickerMessage !== '' && <div className="project-path-picker-feedback" role="status" aria-live="polite">{pickerMessage}</div>}
                        <ProjectDialogSummary title={createSummaryTitle} body={createSummaryBody} tooltipLabel={t('Information about project creation')} tooltipTitle={createSummaryTitle} tooltipBody={t('Project creation in HYDRA is project-first. Start from a recommended purpose, keep or adjust the proposed areas and then choose where the local project folder will be created.')} />
                        <section className="project-preset-section" aria-label={t('Project purpose')}>
                            <ProjectSectionHeading title={t('What do you want to create?')} body={t('Start from a recommended configuration or choose an empty project.')} tooltipLabel={t('Information about project types')} tooltipTitle={t('What do you want to create?')} tooltipBody={t('Each starting point enables the areas that are usually needed for that goal. You can keep the recommendation or change the areas manually without changing the project afterwards.')} />
                            {!showPresetChooser && <PresetFocusCard preset={preset} capabilityCount={selectedPresetDescriptor?.capabilities.length ?? selectedCapabilities.size} onChangePreset={() => setShowPresetChooser(true)} />}
                            {showPresetChooser && <div className="project-preset-grid">
                                {(creationCatalog?.presets ?? []).map(item => <PresetChoiceCard key={item.id} descriptor={item} selected={preset === item.id} onSelect={() => { selectPreset(item.id); if (initialEntryPoint === 'HOME_QUICK_PRESET') setShowPresetChooser(false); }} />)}
                            </div>}
                            {showPresetChooser && initialEntryPoint === 'HOME_QUICK_PRESET' && <HydraButton className="project-preset-confirm" type="button" onClick={() => setShowPresetChooser(false)}>{t('Use this starting point')}</HydraButton>}
                            <PresetGuidancePanel preset={preset} />
                            <div className="project-experience-row">
                                <div><strong>{t('Working mode')}</strong><span>{t('Both modes use the same project model; guided mode only simplifies the interface and proposes the next step.')}</span></div><HydraTooltip label={t('Information about the working mode')} title={t('Working mode')}><p>{t('Guided mode reduces the visible complexity and proposes the next step, while Free mode exposes the whole workbench. The stored project model is exactly the same in both modes, so you can switch later without losing anything.')}</p></HydraTooltip>
                                <div className="project-experience-buttons"><HydraButton className={experienceMode === 'GUIDED' ? 'active' : ''} type="button" onClick={() => setExperienceMode('GUIDED')}>{t('Guided')}</HydraButton><HydraButton className={experienceMode === 'FREE' ? 'active' : ''} type="button" onClick={() => setExperienceMode('FREE')}>{t('Free')}</HydraButton></div>
                            </div>
                            <div className="project-customize-row"><div><HydraButton className="project-customize-trigger" type="button" onClick={toggleCapabilityCustomization}>{customizeCapabilities ? t('Use recommended areas') : t('Choose project areas')}</HydraButton><small>{t(presetGuidance.customizationHint)}</small></div><HydraTooltip label={t('Information about project areas')} title={t('Project areas')}><p>{t('Use this option when you want to revise the recommended HYDRA areas manually. Dependencies are added automatically so that the selected configuration remains valid.')}</p></HydraTooltip></div>
                            {customizeCapabilities && <div className="project-capability-grid">{(creationCatalog?.capabilities ?? []).map(capability => <label key={capability.id} className="project-capability-option"><input checked={selectedCapabilities.has(capability.id)} type="checkbox" onChange={() => toggleCapability(capability.id)} /><span><strong>{t(projectCapabilityLabel(capability.id))}</strong>{capability.requires.length > 0 && <small>{t('Needs')}: {capability.requires.map(item => t(projectCapabilityLabel(item))).join(', ')}</small>}</span></label>)}</div>}
                        </section>
                        <FormFieldHelp label={t('Parent directory')} targetId="project-create-parent" help={t('Folder in which the new project directory will be created. Use the folder button to open the operating-system selector or enter an absolute path manually.')} example="C:\\Users\\Cristian\\Documents\\ModelingProjects" required>
                            <div className="project-path-control"><input id="project-create-parent" value={parent} onChange={(event: ChangeEvent<HTMLInputElement>) => setParent(event.target.value)} placeholder="C:\Users\...\ModelingProjects" /><HydraButton aria-label={t('Browse parent directory')} className="project-icon-action" disabled={pickerBusy} type="button" onClick={() => void browseDirectory('PARENT')}><FolderIcon/><span>{t('Browse')}</span></HydraButton></div>
                        </FormFieldHelp>
                        <FormFieldHelp label={t('Project name')} targetId="project-create-name" help={t('Human-readable name shown in the Project Explorer and recent-project list.')} example="Mi proyecto de modelado" required>
                            <input id="project-create-name" value={name} onChange={(event: ChangeEvent<HTMLInputElement>) => { setName(event.target.value); if (projectId === '') setProjectId(event.target.value.toLowerCase().replace(/[^a-z0-9]+/g, '-')); }} />
                        </FormFieldHelp>
                        <FormFieldHelp label={t('Project ID')} targetId="project-create-id" help={t('Stable identifier used by manifests and future workspace storage. It should not change when the visible project name changes.')} example="mi-proyecto" required>
                            <input id="project-create-id" value={projectId} onChange={(event: ChangeEvent<HTMLInputElement>) => setProjectId(event.target.value)} />
                        </FormFieldHelp>
                        <FormFieldHelp label={t('Description')} targetId="project-create-description" help={t("Optional explanation of the project's purpose. It is stored in modeling-project.json.")} example="Editor y reglas de calidad para mi lenguaje">
                            <textarea id="project-create-description" value={description} onChange={(event: ChangeEvent<HTMLTextAreaElement>) => setDescription(event.target.value)} />
                        </FormFieldHelp>
                        <label className="project-checkbox"><input checked={initializeWorkspace} type="checkbox" onChange={(event: ChangeEvent<HTMLInputElement>) => setInitializeWorkspace(event.target.checked)} />{t('Copy the current HYDRA workspace into the new project')}</label>
                        <section className="project-structure-preview" aria-label={t('Project structure')}>
                            <header><div><strong>{t('Initial project areas')}</strong><span>{t('Only the folders needed by these areas are created. You can add more areas later.')}</span></div><HydraTooltip label={t('Information about initial project areas')} title={t('Initial project areas')}><p>{t('This preview shows the areas that will be initialized when the project is created. HYDRA only materializes the folders needed by the selected areas, which keeps the project smaller and clearer at the beginning.')}</p></HydraTooltip></header>
                            <div className="project-selected-capabilities">{Array.from(selectedCapabilities).sort().map(capability => <span key={capability}>{t(projectCapabilityLabel(capability))}</span>)}</div>
                            {selectedCapabilities.size === 0 && <p>{t('The project starts empty. Add areas later from the project menu.')}</p>}
                        </section>
                    </div>}
                    {mode === 'ACCESS' && <div className="project-dialog-form">
                        <RequiredFieldsLegend />
                        {pickerMessage !== '' && <div className="project-path-picker-feedback" role="status" aria-live="polite">{pickerMessage}</div>}
                        <ProjectDialogSummary title={accessSummaryTitle} body={accessSummaryBody} tooltipLabel={t('Information about project opening and import')} tooltipTitle={accessSummaryTitle} tooltipBody={t('HYDRA adapts this dialog to the source you selected on the home screen. When the source is already known, only the relevant fields are shown. Use the general project manager entry point if you want to compare all source types at once.')} />
                        {showAccessSourceSelector && <><ProjectSectionHeading title={t('Source')} body={t('Open a local project directly, import a ZIP, copy a project folder or retrieve a project from a repository.')} tooltipLabel={t('Information about project sources')} tooltipTitle={t('Source')} tooltipBody={t('Choose the origin of the project you want to open or import. HYDRA then narrows the remaining form so that you only see the inputs required for that source type.')} />
                        <div className="project-source-options project-source-options-wide">
                            <HydraButton aria-label={t('Local project')} aria-pressed={accessSource === 'LOCAL'} className={`project-source-option${accessSource === 'LOCAL' ? ' selected' : ''}`} disabled={pickerBusy} type="button" onClick={() => setAccessSource('LOCAL')}><FolderTreeIcon/><span><strong>{t('Local project')}</strong><small>{t('Open an existing project folder from this computer.')}</small></span></HydraButton>
                            <HydraButton aria-label={t('ZIP file')} aria-pressed={accessSource === 'ZIP'} className={`project-source-option${accessSource === 'ZIP' ? ' selected' : ''}`} type="button" onClick={() => setAccessSource('ZIP')}><FileIcon/><span><strong>{t('ZIP file')}</strong><small>{t('Import a portable HYDRA project archive.')}</small></span></HydraButton>
                            <HydraButton aria-label={t('External project')} aria-pressed={accessSource === 'EXTERNAL'} className={`project-source-option${accessSource === 'EXTERNAL' ? ' selected' : ''}`} type="button" onClick={() => { setAccessSource('EXTERNAL'); setExternalRecovery(null); }}><FolderTreeIcon/><span><strong>{t('External project')}</strong><small>{t('Analyze and recover a project from Xtext, EMF, Sirius, GMF, GLSP, Langium, BESSER or mixed technologies.')}</small></span></HydraButton>
                            <HydraButton aria-label={t('Project folder')} aria-pressed={accessSource === 'DIRECTORY'} className={`project-source-option${accessSource === 'DIRECTORY' ? ' selected' : ''}`} type="button" onClick={() => setAccessSource('DIRECTORY')}><FolderIcon/><span><strong>{t('Project folder')}</strong><small>{t('Copy a complete local project directory into HYDRA.')}</small></span></HydraButton>
                            <HydraButton aria-label={t('Repository')} aria-pressed={accessSource === 'REPOSITORY'} className={`project-source-option${accessSource === 'REPOSITORY' ? ' selected' : ''}`} type="button" onClick={() => setAccessSource('REPOSITORY')}><FileIcon/><span><strong>{t('Repository')}</strong><small>{t('Import a project ZIP from a configured repository or cloud profile.')}</small></span></HydraButton>
                        </div></>}

                        {accessSource === 'LOCAL' && <>
                            <ProjectSectionHeading title={t('Choose the local project')} body={t('You can pick the descriptor file, the project folder or choose a recent project.')} tooltipLabel={t('Information about local projects')} tooltipTitle={t('Choose the local project')} tooltipBody={t('Use this path when the project already exists on this computer. You can select the folder directly, point to the descriptor file or reuse one of the recent projects listed below.')} />
                            <div className="project-source-options">
                                <HydraButton className="project-source-option" disabled={pickerBusy} type="button" onClick={() => void browseProjectDescriptor()}><FileIcon/><span><strong>{t('Project file')}</strong><small>{t("Select the project's modeling-project.json file.")}</small></span></HydraButton>
                                <HydraButton className="project-source-option" disabled={pickerBusy} type="button" onClick={() => void browseDirectory('PROJECT')}><FolderTreeIcon/><span><strong>{t('Project folder')}</strong><small>{t('Select the complete local project folder.')}</small></span></HydraButton>
                            </div>
                            <FormFieldHelp label={t('Project directory')} targetId="project-open-directory" help={t('Absolute path to a folder containing modeling-project.json. It can be selected using either button above.')} example="C:\\Proyectos\\MiProyecto" required>
                                <div className="project-path-control"><input id="project-open-directory" value={directory} onChange={(event: ChangeEvent<HTMLInputElement>) => setDirectory(event.target.value)} placeholder={t('Example project directory')} /><HydraButton aria-label={t('Browse project directory')} className="project-icon-action" disabled={pickerBusy} type="button" onClick={() => void browseDirectory('PROJECT')}><FolderIcon/><span>{t('Browse')}</span></HydraButton></div>
                            </FormFieldHelp>
                            {recentProjects.length > 0 && <FormFieldHelp label={t('Recent projects')} targetId="project-open-recent" help={t('Quickly reuse a project recently opened in this HYDRA installation.')} example="Mi proyecto" >
                                <HydraSelect id="project-open-recent" ariaLabel="Recent projects" value="" placeholder="Choose a recent project…" options={recentProjects.map(project => ({ value:project.directory,label:`${project.name} — ${project.directory}`,description:project.available?t('Open this recently used project.'):t('This recent project is no longer available at its saved location.'),disabled:!project.available }))} onChange={value => { if (value !== '') setDirectory(value); }} />
                            </FormFieldHelp>}
                            <label className="project-checkbox"><input checked={loadWorkspace} type="checkbox" onChange={(event: ChangeEvent<HTMLInputElement>) => setLoadWorkspace(event.target.checked)} />{t('Load the project files and models into the current HYDRA workspace')}</label>
                        </>}

                        {accessSource !== 'LOCAL' && <>
                            <FormFieldHelp label={t('Destination directory')} targetId="project-import-parent" help={t('Parent folder where the imported project will be created as a local HYDRA project.')} example="C:\\Users\\Cristian\\Documents\\ModelingProjects" required>
                                <div className="project-path-control"><input id="project-import-parent" value={parent} onChange={(event: ChangeEvent<HTMLInputElement>) => setParent(event.target.value)} /><HydraButton aria-label={t('Browse import destination directory')} className="project-icon-action" disabled={pickerBusy} type="button" onClick={() => void browseDirectory('PARENT')}><FolderIcon/><span>{t('Browse')}</span></HydraButton></div>
                            </FormFieldHelp>
                            <FormFieldHelp label={t('Local project folder name')} targetId="project-import-name" help={t('Name of the local project directory created below the destination folder. The source project content is preserved.')} example="MiProyectoImportado" required>
                                <input id="project-import-name" value={name} onChange={(event: ChangeEvent<HTMLInputElement>) => setName(event.target.value)} />
                            </FormFieldHelp>
                        </>}

                        {accessSource === 'ZIP' && <FormFieldHelp label={t('Project ZIP')} targetId="project-import-file-button" help={t('Portable project archive exported by HYDRA. The archive is validated before being expanded.')} example="mi-proyecto.zip" required>
                            <input accept=".zip,application/zip" className="project-hidden-file-input" id="project-import-file" ref={zipInputRef} type="file" onChange={selectZipArchive} />
                            <HydraButton aria-label={t('Select ZIP file')} className="project-source-option project-file-picker" id="project-import-file-button" type="button" onClick={browseZipArchive}><FileIcon/><span><strong>{t('Select ZIP file')}</strong><small>{file === null ? t('Choose a ZIP archive from this computer.') : file.name}</small></span></HydraButton>
                        </FormFieldHelp>}

                        {accessSource === 'EXTERNAL' && <section className="project-external-recovery">
                            <FormFieldHelp label={t('External project archive')} targetId="external-project-file-button" help={t('HYDRA inventories the complete archive and nested ZIP/JAR/WAR/EAR containers, identifies the primary metamodel and invokes the matching safe adapters for editors/representations, validations and quick fixes. Imported source code is inspected statically and is never executed.')} example="my-language.zip" required>
                                <input accept=".zip,application/zip" className="project-hidden-file-input" id="external-project-file" ref={zipInputRef} type="file" onChange={selectZipArchive} />
                                <HydraButton aria-label={t('Select external project')} className="project-source-option project-file-picker" id="external-project-file-button" type="button" onClick={browseZipArchive}><FolderTreeIcon/><span><strong>{t('Select external project')}</strong><small>{file === null ? t('Choose a project ZIP from this computer.') : file.name}</small></span></HydraButton>
                            </FormFieldHelp>
                            <HydraButton className="project-primary-action" disabled={file === null || externalAnalyzing} type="button" onClick={() => void analyzeExternal()}>{externalAnalyzing ? t('Analyzing project…') : t('Analyze project')}</HydraButton>
                            {externalRecovery !== null && <div className="project-recovery-summary" role="status">
                                <strong>{t('Recovery workspace ready')}</strong>
                                <span>{externalRecovery.resourceCount} {t('resources inventoried')} · {externalRecovery.totalBytes} bytes</span>
                                <span>{t('Technologies')}: {externalRecovery.technologies.join(', ') || t('No known technology detected')}</span>
                                <span>{externalRecovery.mappingEvidence.filter(item => item.autoSelectable).length} {t('safe automatic mappings')} · {externalRecovery.mappingEvidence.filter(item => !item.autoSelectable).length} {t('mappings to review')}</span>
                                <span>{externalRecovery.integrationSuggestions.length} {t('integration profiles can be proposed')}</span>
                                {externalRecovery.grammarInspections.map(grammar => <details key={grammar.sourcePath} className="project-grammar-inspection">
                                    <summary>{grammar.technology} · {grammar.sourcePath} · {t('Entry rule')}: {grammar.inferredEntryRule || t('not inferred')} ({Math.round(grammar.inferenceConfidence*100)}%)</summary>
                                    <small>{t('Parser rules')}: {grammar.parserRules.join(', ') || '—'}</small>
                                    {grammar.recursiveRules.length > 0 && <small>{t('Recursive rules')}: {grammar.recursiveRules.join(', ')}</small>}
                                    {grammar.leftRecursiveRules.length > 0 && <small>{t('Left-recursive rules')}: {grammar.leftRecursiveRules.join(', ')}</small>}
                                    {grammar.automaticResolutionRules.length > 0 && <small>{t('Automatic rewrite available')}: {grammar.automaticResolutionRules.join(', ')}</small>}
                                    {grammar.explanations.map(explanation => <small key={explanation}>{t(explanation)}</small>)}
                                </details>)}
                                <small>{t('When you import, exact declarative evidence is materialized automatically. Ambiguous semantics remain preserved for review instead of being guessed.')}</small>
                                {externalRecovery.diagnostics.slice(0, 3).map(message => <small key={message}>{message}</small>)}
                            </div>}
                        </section>}

                        {accessSource === 'DIRECTORY' && <FormFieldHelp label={t('Source project directory')} targetId="project-import-source-directory" help={t('Existing folder containing modeling-project.json. The source is copied; it is not modified.')} example="C:\\Proyectos\\MiProyecto" required>
                            <div className="project-path-control"><input id="project-import-source-directory" value={sourceDirectory} onChange={(event: ChangeEvent<HTMLInputElement>) => { setSourceDirectory(event.target.value); setBrowserDirectoryFiles([]); setBrowserDirectoryPaths([]); }} placeholder={t('Choose a folder or enter a server-side path')} /><HydraButton aria-label={t('Browse source project directory')} className="project-icon-action" disabled={pickerBusy} type="button" onClick={() => void browseDirectory('SOURCE')}><FolderTreeIcon/><span>{t('Browse')}</span></HydraButton><input aria-hidden="true" className="project-hidden-file-input" multiple ref={node => { directoryInputRef.current = node; node?.setAttribute('webkitdirectory', ''); }} type="file" onChange={selectBrowserDirectory} /></div>
                        </FormFieldHelp>}

                        {accessSource === 'REPOSITORY' && <>
                            <FormFieldHelp label={t('Repository profile')} targetId="project-import-remote-profile" help={t('Configured repository or cloud profile that contains the portable HYDRA project ZIP.')} example="Main source repository" required>
                                <HydraSelect id="project-import-remote-profile" ariaLabel="Repository profile" value={remoteProfileId} placeholder="Choose a repository profile…" options={remoteProfiles.map(profile => ({ value:profile.id,label:`${profile.displayName} — ${profile.provider}${!profile.enabled ? ` (${t('disabled')})` : !profile.configured ? ` (${t('credentials pending')})` : ''}`,description:profile.configured?t('Repository profile ready for project import.'):t('Complete this repository profile before importing a project.'),disabled:!profile.enabled || !profile.configured }))} onChange={setRemoteProfileId} />
                            </FormFieldHelp>
                            <FormFieldHelp label={t('Remote project path')} targetId="project-import-remote-path" help={t('Path of the portable HYDRA project ZIP inside the selected repository or cloud space.')} example="proyectos/mi-proyecto.zip" required>
                                <input id="project-import-remote-path" value={remotePath} onChange={(event: ChangeEvent<HTMLInputElement>) => setRemotePath(event.target.value)} />
                            </FormFieldHelp>
                            <div className="project-dialog-notice project-inline-note">{selectedRemoteProfile === undefined ? t('Choose a repository profile to import from repository or cloud storage.') : `${selectedRemoteProfile.displayName} · ${selectedRemoteProfile.provider}${selectedRemoteProfile.repository !== '' ? ` · ${selectedRemoteProfile.repository}` : ''}`}</div>
                            {repositoryProfilesAvailable.length === 0 && <div className="project-dialog-notice project-inline-note">{t('No enabled repository profile is ready yet. Configure one in Repository connections first.')}</div>}
                        </>}
                    </div>}
                    {mode === 'CREATE' && catalogError !== '' && error === '' && <HydraActionableErrorNotice message={catalogError} context="project templates" onDismiss={() => setCatalogError('')} />}
                    {error !== '' && <HydraActionableErrorNotice message={error} context="project explorer" onDismiss={() => setError('')} />}
                </div>
                <footer><HydraButton type="button" onClick={onClose}>{t('Cancel')}</HydraButton><HydraButton className="project-primary-action" disabled={busy || pickerBusy || (mode === 'ACCESS' && accessSource === 'EXTERNAL' && externalRecovery === null)} type="button" onClick={() => void submit()}>{busy ? t('Working…') : submitLabel}</HydraButton></footer>
            </section>
        </div>
    );
}


function isProjectCreationCatalog(value: ProjectCreationCatalog): boolean {
    return Array.isArray(value?.presets) && Array.isArray(value?.capabilities);
}

function PresetChoiceCard({ descriptor, selected, onSelect }: { descriptor: { id: ProjectPreset; capabilities: string[] }; selected: boolean; onSelect: () => void }) {
    const { t } = useHydraLanguage();
    const guidance = projectPresetGuidance(descriptor.id);
    return <div className={`project-preset-card-shell${selected ? ' selected' : ''}`}>
        <HydraButton aria-pressed={selected} className={`project-preset-card${selected ? ' selected' : ''}`} type="button" onClick={onSelect}>
            <strong>{t(projectPresetLabel(descriptor.id))}</strong>
            <small>{t(projectPresetDescription(descriptor.id))}</small>
            <span>{descriptor.capabilities.length} {t('project areas')}</span>
        </HydraButton>
        <HydraTooltip label={t('More information about this starting point')} title={t(projectPresetLabel(descriptor.id))}>
            <p>{t(guidance.summary)}</p>
            <p><strong>{t('Recommended next step')}:</strong> {t(guidance.nextSteps[0] ?? '')}</p>
        </HydraTooltip>
    </div>;
}

function PresetFocusCard({ preset, capabilityCount, onChangePreset }: { preset: ProjectPreset; capabilityCount: number; onChangePreset: () => void }) {
    const { t } = useHydraLanguage();
    const guidance = projectPresetGuidance(preset);
    return <article className="project-preset-focus-card">
        <div className="project-preset-focus-copy">
            <span className="project-preset-focus-eyebrow">{t('Selected starting point')}</span>
            <h3>{t(projectPresetLabel(preset))}</h3>
            <p>{t(guidance.summary)}</p>
            <small>{capabilityCount} {t('project areas')} · {t(guidance.recommendedMode === 'GUIDED' ? 'Guided mode' : 'Free mode')}</small>
        </div>
        <div className="project-preset-focus-actions">
            <HydraTooltip label={t('Information about the selected starting point')} title={t(projectPresetLabel(preset))}><p>{t(guidance.customizationHint)}</p></HydraTooltip>
            <HydraButton type="button" onClick={onChangePreset}>{t('Change starting point')}</HydraButton>
        </div>
    </article>;
}

function PresetGuidancePanel({ preset }: { preset: ProjectPreset }) {
    const { t } = useHydraLanguage();
    const guidance = projectPresetGuidance(preset);
    return <section className="project-preset-guidance" aria-label={t('Starting point guidance')}>
        <article>
            <header><div><strong>{t('What HYDRA prepares')}</strong><span>{t('The areas and project structure initialized for this purpose.')}</span></div><HydraTooltip label={t('Information about what HYDRA prepares')} title={t('What HYDRA prepares')}><p>{t('These items describe the initial support that HYDRA enables for the selected purpose. They do not prevent you from adding other areas later.')}</p></HydraTooltip></header>
            <ol>{guidance.preparation.map(item => <li key={item}>{t(item)}</li>)}</ol>
        </article>
        <article>
            <header><div><strong>{t('Recommended path')}</strong><span>{t('A practical order for starting the project after creation.')}</span></div><HydraTooltip label={t('Information about the recommended path')} title={t('Recommended path')}><p>{t('This is guidance only. In Free mode you can work in any order, while Guided mode can use this sequence to propose the next action.')}</p></HydraTooltip></header>
            <ol>{guidance.nextSteps.map(item => <li key={item}>{t(item)}</li>)}</ol>
        </article>
    </section>;
}


function ProjectDialogSummary({ title, body, tooltipLabel, tooltipTitle, tooltipBody }: { title: string; body: string; tooltipLabel: string; tooltipTitle: string; tooltipBody: string; }) {
    return <div className="project-flow-summary"><div><strong>{title}</strong><span>{body}</span></div><HydraTooltip label={tooltipLabel} title={tooltipTitle}><p>{tooltipBody}</p></HydraTooltip></div>;
}

function ProjectSectionHeading({ title, body, tooltipLabel, tooltipTitle, tooltipBody }: { title: string; body: string; tooltipLabel: string; tooltipTitle: string; tooltipBody: string; }) {
    return <div className="project-source-heading project-source-heading-with-help"><div><strong>{title}</strong><span>{body}</span></div><HydraTooltip label={tooltipLabel} title={tooltipTitle}><p>{tooltipBody}</p></HydraTooltip></div>;
}
