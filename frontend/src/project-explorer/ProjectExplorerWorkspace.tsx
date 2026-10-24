import { useEffect, useMemo, useRef, useState } from 'react';
import type { ChangeEvent, MouseEvent as ReactMouseEvent, ReactNode } from 'react';
import {
    closeLocalProject,
    configureActiveProject,
    createLocalProject,
    createProjectEntry,
    deleteProjectEntry,
    exportProjectSelection,
    duplicateProjectEntry,
    getProjectSession,
    importProjectArchive,
    importProjectBrowserDirectory,
    importProjectDirectory,
    importExternalProjectArchive,
    listRecentProjects,
    loadProjectFile,
    loadProjectTree,
    moveProjectEntry,
    openLocalProject,
    pollProjectChanges,
    previewProjectSynchronization,
    projectArchiveDownloadUrl,
    projectDescriptorDrawioUrl,
    projectTreeDrawioUrl,
    projectFileDownloadUrl,
    saveProjectFile,
    saveProjectTabs,
    searchProject,
    resolveProjectPath,
    synchronizeProject,
    uploadProjectFile,
} from './projectApi';
import { ProjectFileEditor } from './ProjectFileEditor';
import { ProjectManagerDialog } from './ProjectManagerDialog';
import { ProjectDeletionDialog } from './ProjectDeletionDialog';
import { ProjectCapabilitiesDialog } from './ProjectCapabilitiesDialog';
import { ProjectEntryPropertiesDialog } from './ProjectEntryPropertiesDialog';
import { ProjectTabs } from './ProjectTabs';
import { ProjectTree } from './ProjectTree';
import { ProjectSelectionRemoteExportDialog } from './ProjectSelectionRemoteExportDialog';
import { ProjectExplorerCollapsedTab, ProjectExplorerPanelHeader } from './ProjectExplorerVisibilityControls';
import { HydraActionableErrorNotice, HydraButton, openFileInputPicker } from '../design-system';
import { importRemoteProject, RemoteProjectStorageDialog } from '../remote-storage';
import { recordAuditActivity } from '../audit-log/api';
import { useHydraLanguage } from '../i18n';
import { useHydraDialogs } from '../hooks/useHydraDialogs';
import type { ProjectExperienceMode, ProjectFileChange, ProjectPreset, ProjectSearchMatch, ProjectSession, ProjectSummary, ProjectTab, ProjectTreeNode, ProjectTreeView } from './types';
import { emptyProjectSession, findProjectTreeNode, projectFileName, projectParentPath, projectSelectionRoots, uniqueProjectCopyTarget } from './projectExplorerWorkspaceModel';
import './project-explorer.css';
interface ProjectExplorerWorkspaceProps {
    children: ReactNode;
    onWorkspaceReload: () => Promise<void>;
    workbenchId?: string;
}

export function ProjectExplorerWorkspace({ children, onWorkspaceReload, workbenchId = 'WORKBENCH' }: ProjectExplorerWorkspaceProps) {
    const { t } = useHydraLanguage();
    const { requestDecision, requestText, dialogs } = useHydraDialogs();
    const [session, setSession] = useState<ProjectSession>(emptyProjectSession());
    const [recentProjects, setRecentProjects] = useState<ProjectSummary[]>([]);
    const [tree, setTree] = useState<ProjectTreeNode | null>(null);
    const [view, setView] = useState<ProjectTreeView>('LOGICAL');
    const [selectedPath, setSelectedPath] = useState('');
    const [selectedPaths, setSelectedPaths] = useState<Set<string>>(new Set());
    const [clipboardPaths, setClipboardPaths] = useState<string[]>([]);
    const [clipboardMode, setClipboardMode] = useState<'COPY' | 'CUT'>('COPY');
    const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null);
    const [remoteSelectionExportOpen, setRemoteSelectionExportOpen] = useState(false);
    const [tabs, setTabs] = useState<ProjectTab[]>([]);
    // Pestañas del último render: abrir varios ficheros seguidos no debe persistir la lista obsoleta del render que inició el bucle.
    const tabsRef = useRef<ProjectTab[]>([]);
    useEffect(() => { tabsRef.current = tabs; }, [tabs]);
    const [activeLocation, setActiveLocation] = useState<{ workbenchId: string; path: string }>({ workbenchId, path: '' });
    const activePath = activeLocation.workbenchId === workbenchId ? activeLocation.path : '';
    const setActivePath = (path: string): void => setActiveLocation({ workbenchId, path });
    const [managerOpen, setManagerOpen] = useState(false);
    const [managerDefaults, setManagerDefaults] = useState<{ mode: 'CREATE' | 'ACCESS'; accessSource: 'LOCAL' | 'ZIP' | 'DIRECTORY' | 'REPOSITORY' | 'EXTERNAL'; preset: ProjectPreset; entryPoint: 'GENERAL' | 'HOME_NEW_PROJECT' | 'HOME_OPEN_PROJECT' | 'HOME_IMPORT_PROJECT' | 'HOME_CONNECT_PROJECT' | 'HOME_QUICK_PRESET' }>({ mode: 'CREATE', accessSource: 'LOCAL', preset: 'EMPTY', entryPoint: 'GENERAL' });
    const [projectDeletionOpen, setProjectDeletionOpen] = useState(false);
    const [projectCapabilitiesOpen, setProjectCapabilitiesOpen] = useState(false);
    const [projectPropertiesOpen, setProjectPropertiesOpen] = useState(false);
    const [remoteStorageOpen, setRemoteStorageOpen] = useState(false);
    const [explorerVisible, setExplorerVisible] = useState(true);
    const [changes, setChanges] = useState<ProjectFileChange[]>([]);
    const [error, setError] = useState('');
    const [status, setStatus] = useState('');
    const [searchQuery, setSearchQuery] = useState('');
    const [searchMatches, setSearchMatches] = useState<ProjectSearchMatch[]>([]);
    const uploadRef = useRef<HTMLInputElement | null>(null);
    const handleOpenRef = useRef<(directory: string, loadIntoWorkspace: boolean) => Promise<void>>(async () => undefined);
    const selectedNode = useMemo(() => findProjectTreeNode(tree, selectedPath), [tree, selectedPath]);
    const selectedNodes = useMemo(() => Array.from(selectedPaths).map(path => findProjectTreeNode(tree, path)).filter((node): node is ProjectTreeNode => node !== null), [selectedPaths, tree]);
    const actionableSelectionPaths = useMemo(() => projectSelectionRoots(selectedNodes.map(node => node.relativePath)), [selectedNodes]);
    const projectRootSelected = useMemo(() => selectedNodes.some(node => node.relativePath === ''), [selectedNodes]);
    const activeTab = tabs.find(tab => tab.path === activePath) ?? null;
    const changedPaths = useMemo(() => new Set(changes.map(change => change.relativePath)), [changes]);

    async function refreshTree(selectedView: ProjectTreeView = view): Promise<void> {
        if (!session.active) {
            setTree(null);
            return;
        }
        const loaded = await loadProjectTree(selectedView);
        setTree(loaded);
    }

    async function loadRecent(): Promise<void> {
        setRecentProjects(await listRecentProjects());
    }

    async function openFile(path: string, persist = true): Promise<void> {
        const existing = tabsRef.current.find(tab => tab.path === path);
        if (existing !== undefined) {
            setActivePath(path);
            return;
        }
        const pending: ProjectTab = { path, name: projectFileName(path), content: null, draft: '', dirty: false, loading: true, error: '' };
        setTabs(current => [...current, pending]);
        setActivePath(path);
        try {
            const content = await loadProjectFile(path);
            setTabs(current => current.map(tab => tab.path === path ? { ...tab, content, draft: content.content, loading: false } : tab));
            if (persist) await saveProjectTabs([...new Set([...tabsRef.current.map(tab => tab.path), path])]);
        } catch (exception) {
            const message = exception instanceof Error ? exception.message : 'Could not open project file';
            setTabs(current => current.map(tab => tab.path === path ? { ...tab, loading: false, error: message } : tab));
        }
    }

    async function reloadFile(path: string): Promise<void> {
        const content = await loadProjectFile(path);
        setTabs(current => current.map(tab => tab.path === path ? { ...tab, content, draft: content.content, dirty: false, loading: false, error: '' } : tab));
        setChanges(current => current.filter(change => change.relativePath !== path));
    }

    async function saveActiveFile(): Promise<void> {
        if (activeTab?.content === null || activeTab === null) return;
        try {
            const result = await saveProjectFile(activeTab.path, activeTab.draft, activeTab.content.etag, activeTab.content.binary);
            const reloaded = await loadProjectFile(activeTab.path);
            setTabs(current => current.map(tab => tab.path === activeTab.path ? { ...tab, content: reloaded, draft: reloaded.content, dirty: false } : tab));
            setTree(result.tree);
            setStatus(`Saved ${activeTab.path}`);
            setError('');
        } catch (exception) {
            setError(exception instanceof Error ? exception.message : 'Could not save project file');
        }
    }

    async function closeTab(path: string): Promise<void> {
        const tab = tabs.find(item => item.path === path);
        if (tab?.dirty && !await requestDecision({ title: t('Discard unsaved changes?'), description: `${t('Unsaved changes will be lost in')} ${tab.name}.`, confirmLabel: t('Discard changes'), cancelLabel: t('Cancel'), danger: true })) return;
        const remaining = tabs.filter(item => item.path !== path);
        setTabs(remaining);
        if (activePath === path) setActivePath(remaining.at(-1)?.path ?? '');
        if (session.active) await saveProjectTabs(remaining.map(item => item.path));
    }

    async function handleCreate(request: { parentDirectory: string; projectId: string; name: string; description: string; initializeFromCurrentWorkspace: boolean; preset: ProjectPreset; enabledCapabilities: string[] | null; experienceMode: ProjectExperienceMode }): Promise<void> {
        const created = await createLocalProject(request); const [createdTree, recents] = await Promise.all([loadProjectTree(view), listRecentProjects()]);
        setSession(created); setTabs([]); setActivePath(''); setSelectedPath(''); setSelectedPaths(new Set()); setChanges([]);
        setTree(createdTree); setRecentProjects(recents); setStatus(`Created ${created.project?.name ?? 'project'}`);
        window.dispatchEvent(new CustomEvent('hydra:project-session-changed'));
    }

    async function handleOpen(directory: string, loadIntoWorkspace: boolean): Promise<void> {
        const opened = await openLocalProject(directory, loadIntoWorkspace, 'MERGE');
        setSession(opened); setTabs([]); setActivePath(''); setSelectedPath(''); setSelectedPaths(new Set()); setChanges([]);
        setTree(await loadProjectTree(view)); await loadRecent();
        if (loadIntoWorkspace) await onWorkspaceReload();
        for (const path of opened.restoredTabs) await openFile(path, false);
        setStatus(`Opened ${opened.project?.name ?? 'project'}`);
        window.dispatchEvent(new CustomEvent('hydra:project-session-changed'));
    }

    useEffect(() => { handleOpenRef.current = handleOpen; });

    async function handleImport(parent: string, name: string, file: File): Promise<void> { await applyImportedSession(await importProjectArchive(parent, name, file)); }

    async function handleImportDirectory(source: string, parent: string, name: string): Promise<void> { await applyImportedSession(await importProjectDirectory(source, parent, name)); }

    async function handleImportBrowserDirectory(files: File[], relativePaths: string[], parent: string, name: string): Promise<void> { await applyImportedSession(await importProjectBrowserDirectory(files, relativePaths, parent, name)); }

    async function handleImportRepository(request: { profileId: string; remotePath: string; parentDirectory: string; projectName: string }): Promise<void> { await applyImportedSession(await importRemoteProject(request)); }

    async function handleImportExternal(parent: string, name: string, file: File, confirmedFingerprint: string): Promise<void> {
        const result = await importExternalProjectArchive(parent, name, file, confirmedFingerprint);
        await applyImportedSession(result.session);
        setStatus(`External project imported · ${result.recovery.resourceCount} resources · metamodel ${result.materialization.metamodelId || 'pending review'} · ${result.materialization.editorIds.length} editor(s) · ${result.materialization.visualValidationsImported + result.materialization.validationRulesImported} validation(s) · ${result.materialization.quickFixesImported} quick fix(es)`);
    }

    async function applyImportedSession(imported: ProjectSession): Promise<void> {
        setSession(imported); setTabs([]); setActivePath(''); setSelectedPath(''); setSelectedPaths(new Set()); setChanges([]);
        setTree(await loadProjectTree(view)); await loadRecent(); setStatus(`Imported ${imported.project?.name ?? 'project'}`);
        window.dispatchEvent(new CustomEvent('hydra:project-session-changed'));
    }

    async function handleCloseProject(): Promise<void> {
        if (tabs.some(tab => tab.dirty) && !await requestDecision({ title: t('Close project?'), description: t('Unsaved file changes will be discarded before closing the project.'), confirmLabel: t('Close project'), cancelLabel: t('Cancel'), danger: true })) return;
        await closeLocalProject();
        setSession(emptyProjectSession()); setTree(null); setTabs([]); setActivePath(''); setSelectedPath(''); setSelectedPaths(new Set()); setClipboardPaths([]); setClipboardMode('COPY'); setChanges([]); await loadRecent();
        window.dispatchEvent(new CustomEvent('hydra:project-session-changed'));
    }

    async function createEntry(directory: boolean): Promise<void> {
        if (!session.active) return;
        const base = selectedNode?.nodeType === 'DIRECTORY' || selectedNode?.nodeType === 'GROUP' ? selectedNode.relativePath : projectParentPath(selectedPath);
        const name = await requestText({ title: directory ? t('Create directory') : t('Create file'), description: t('Choose a clear name inside the selected project location.'), label: directory ? t('Directory name') : t('File name'), confirmLabel: t('Create'), cancelLabel: t('Cancel') });
        if (name === null || name.trim() === '') return;
        const relativePath = base === '' ? name.trim() : `${base}/${name.trim()}`;
        try {
            const result = await createProjectEntry(relativePath, directory, directory ? '' : '{}\n');
            setTree(result.tree); setSelectedPath(relativePath); setSelectedPaths(new Set([relativePath])); setStatus(`Created ${relativePath}`);
            if (!directory) await openFile(relativePath);
        } catch (exception) { setError(exception instanceof Error ? exception.message : 'Could not create project entry'); }
    }

    async function renameEntry(): Promise<void> {
        if (selectedNode === null || selectedNode.relativePath === '') return;
        const targetName = await requestText({ title: t('Rename project entry'), description: t('Enter the new name. The parent directory remains unchanged.'), label: t('New name'), initialValue: selectedNode.name, confirmLabel: t('Rename'), cancelLabel: t('Cancel') });
        if (targetName === null || targetName.trim() === '' || targetName === selectedNode.name) return;
        const parent = projectParentPath(selectedNode.relativePath);
        const target = parent === '' ? targetName.trim() : `${parent}/${targetName.trim()}`;
        try {
            const source = selectedNode.relativePath;
            const sourcePrefix = `${source}/`;
            const result = await moveProjectEntry(source, target, selectedNode.etag);
            setTree(result.tree); setSelectedPath(target); setSelectedPaths(new Set([target]));
            const renamedTabs = tabs.map(tab => {
                if (tab.path === source) return { ...tab, path: target, name: projectFileName(target) };
                if (tab.path.startsWith(sourcePrefix)) {
                    const renamedPath = `${target}/${tab.path.substring(sourcePrefix.length)}`;
                    return { ...tab, path: renamedPath, name: projectFileName(renamedPath) };
                }
                return tab;
            });
            setTabs(renamedTabs);
            if (activePath === source) setActivePath(target);
            else if (activePath.startsWith(sourcePrefix)) setActivePath(`${target}/${activePath.substring(sourcePrefix.length)}`);
            await saveProjectTabs(renamedTabs.map(tab => tab.path));
        } catch (exception) { setError(exception instanceof Error ? exception.message : 'Could not rename project entry'); }
    }

    async function upload(file: File): Promise<void> {
        const directory = selectedNode?.nodeType === 'DIRECTORY' || selectedNode?.nodeType === 'GROUP' ? selectedNode.relativePath : projectParentPath(selectedPath);
        try { const result = await uploadProjectFile(directory, file); setTree(result.tree); setSelectedPath(result.relativePath); setSelectedPaths(new Set([result.relativePath])); setStatus(`Uploaded ${result.relativePath}`); }
        catch (exception) { setError(exception instanceof Error ? exception.message : 'Could not upload project file'); }
    }

    function selectTreeNode(node: ProjectTreeNode, additive = false): void {
        setSelectedPath(node.relativePath);
        setSelectedPaths(current => {
            if (!additive) return new Set([node.relativePath]);
            const next=new Set(current);
            if(next.has(node.relativePath)) next.delete(node.relativePath); else next.add(node.relativePath);
            return next;
        });
        setContextMenu(null);
    }

    function openContextMenu(node: ProjectTreeNode, event: ReactMouseEvent<HTMLButtonElement>): void {
        if(!selectedPaths.has(node.relativePath)) {
            setSelectedPath(node.relativePath);
            setSelectedPaths(new Set([node.relativePath]));
        } else setSelectedPath(node.relativePath);
        setContextMenu({
            x: Math.min(event.clientX, Math.max(8, window.innerWidth - 236)),
            y: Math.min(event.clientY, Math.max(8, window.innerHeight - 460)),
        });
    }

    async function openSelectedEntries(): Promise<void> {
        setContextMenu(null);
        for(const node of selectedNodes) if(node.nodeType==='FILE') await openFile(node.relativePath);
    }

    function copySelectedEntries(): void {
        setContextMenu(null);
        const roots=projectSelectionRoots(selectedNodes.map(node=>node.relativePath));
        if(roots.length===0)return;
        setClipboardPaths(roots);
        setClipboardMode('COPY');
        setStatus(`${roots.length} ${roots.length===1?'item':'items'} copied to project clipboard.`);
        void recordAuditActivity({ activityType:'project.selection.copied', moduleId:'project', action:'COPY', resourceType:'project-entry-selection', resourceId:roots.join('|').slice(0,160), displayName:`${roots.length} selected project item(s)`, metadata:{count:roots.length,paths:roots.slice(0,25)}, dedupeKey:`copy:${roots.join('|')}`.slice(0,160) });
    }

    function cutSelectedEntries(): void {
        setContextMenu(null);
        const roots=projectSelectionRoots(selectedNodes.map(node=>node.relativePath)).filter(path => path !== '');
        if(roots.length===0)return;
        setClipboardPaths(roots); setClipboardMode('CUT');
        setStatus(`${roots.length} ${roots.length===1?'item':'items'} ready to move.`);
    }

    async function duplicateSelectedEntries(): Promise<void> {
        setContextMenu(null);
        const roots=actionableSelectionPaths;if(roots.length===0)return;
        let currentTree=tree;const created:string[]=[];
        try {
            for(const source of roots){
                const target=uniqueProjectCopyTarget(currentTree,projectParentPath(source),source);
                const result=await duplicateProjectEntry(source,target); currentTree=result.tree; created.push(target);
            }
            setTree(currentTree);setSelectedPath(created.at(-1)??'');setSelectedPaths(new Set(created));setStatus(`Duplicated ${created.length} item(s).`);
        } catch(exception){setError(exception instanceof Error?exception.message:t('Could not duplicate project entries'));}
    }

    async function pasteClipboardIntoSelection(): Promise<void> {
        setContextMenu(null);
        if(clipboardPaths.length===0){setStatus('Nothing has been copied yet.');return;}
        const destination=selectedNode?.nodeType==='DIRECTORY'||selectedNode?.nodeType==='GROUP'||selectedNode?.nodeType==='PROJECT'?selectedNode.relativePath:projectParentPath(selectedPath);
        let currentTree=tree;
        const created:string[]=[];
        try{
            for(const source of clipboardPaths){
                let targetDirectory=destination;
                if(source===targetDirectory||targetDirectory.startsWith(`${source}/`)) targetDirectory=projectParentPath(source);
                const target=uniqueProjectCopyTarget(currentTree,targetDirectory,source);
                const result=clipboardMode==='CUT' ? await moveProjectEntry(source,target,findProjectTreeNode(currentTree,source)?.etag ?? '') : await duplicateProjectEntry(source,target);
                currentTree=result.tree;created.push(target);
            }
            if(clipboardMode==='CUT'){setClipboardPaths([]);setClipboardMode('COPY');}
            setTree(currentTree);setSelectedPath(created.at(-1)??'');setSelectedPaths(new Set(created));setStatus(`${clipboardMode==='CUT'?'Moved':'Pasted'} ${created.length} item(s).`);setError('');
        }catch(exception){setError(exception instanceof Error?exception.message:'Could not copy project entries');}
    }

    function requestDeleteSelection(): void {
        setContextMenu(null);
        if (projectRootSelected) {
            setProjectDeletionOpen(true);
            return;
        }
        void deleteSelectedEntries();
    }

    async function handleProjectDeleted(result: { projectId: string; projectName: string; directory: string; deletedEntries: number; deletedBytes: number }): Promise<void> {
        setSession(emptyProjectSession());
        setTree(null);
        setSelectedPath('');
        setSelectedPaths(new Set());
        setTabs([]);
        setActivePath('');
        setChanges([]);
        setSearchMatches([]);
        setRecentProjects(await listRecentProjects());
        setStatus(`Project ${result.projectName || result.projectId} deleted completely.`);
        setError('');
        window.dispatchEvent(new CustomEvent('hydra:project-session-changed'));
        void recordAuditActivity({ activityType:'project.deleted', moduleId:'project', action:'DELETE', resourceType:'project', resourceId:result.projectId, displayName:result.projectName || result.projectId, metadata:{directory:result.directory,deletedEntries:result.deletedEntries,deletedBytes:result.deletedBytes}, dedupeKey:`project-delete:${result.projectId}:${result.directory}`.slice(0,160) });
    }

    async function deleteSelectedEntries(): Promise<void> {
        setContextMenu(null);
        const paths=actionableSelectionPaths;
        if(paths.length===0)return;
        if(!await requestDecision({ title: paths.length===1?t('Delete project entry?'):t('Delete selected project entries?'), description: paths.length===1?`${t('This will permanently delete')} ${paths[0]}.`:`${t('This will permanently delete')} ${paths.length} ${t('selected project entries')}.`, confirmLabel:t('Delete'), cancelLabel:t('Cancel'), danger:true }))return;
        let currentTree=tree;
        try{
            for(const path of [...paths].sort((left,right)=>right.length-left.length)){
                const node=findProjectTreeNode(currentTree,path);
                if(node===null)continue;
                const result=await deleteProjectEntry(path,node.etag);currentTree=result.tree;
            }
            const remaining=tabs.filter(tab=>!paths.some(path=>tab.path===path||tab.path.startsWith(`${path}/`)));
            setTree(currentTree);setSelectedPath('');setSelectedPaths(new Set());setTabs(remaining);
            if(paths.some(path=>activePath===path||activePath.startsWith(`${path}/`)))setActivePath('');
            await saveProjectTabs(remaining.map(tab=>tab.path));setStatus(`Deleted ${paths.length} item(s).`);setError('');
        }catch(exception){setError(exception instanceof Error?exception.message:'Could not delete selected project entries');}
    }

    async function exportSelectedLocally(): Promise<void> {
        setContextMenu(null);
        const paths=actionableSelectionPaths;if(paths.length===0)return;
        try{
            const archive=await exportProjectSelection(paths);
            const url=URL.createObjectURL(archive);const anchor=document.createElement('a');
            anchor.href=url;anchor.download=`${session.project?.id??'project'}-selection.zip`;document.body.appendChild(anchor);anchor.click();anchor.remove();
            window.setTimeout(()=>URL.revokeObjectURL(url),0);setStatus(`Exported ${paths.length} selected item(s).`);setError('');
        }catch(exception){setError(exception instanceof Error?exception.message:'Could not export selected project entries');}
    }

    function exportSelectedRemotely(): void {
        setContextMenu(null);
        if(actionableSelectionPaths.length>0)setRemoteSelectionExportOpen(true);
    }

    function downloadSelectedFile(): void {
        setContextMenu(null);
        if(selectedNodes.length!==1||selectedNodes[0]?.nodeType!=='FILE')return;
        const anchor=document.createElement('a');anchor.href=projectFileDownloadUrl(selectedNodes[0].relativePath);anchor.download=selectedNodes[0].name;document.body.appendChild(anchor);anchor.click();anchor.remove();
    }

    async function refreshFromContextMenu(): Promise<void> { setContextMenu(null); try { await refreshTree(); setStatus(t('Project explorer refreshed.')); } catch(exception){setError(exception instanceof Error?exception.message:t('Could not refresh project explorer'));} }

    async function handleCapabilitiesConfigured(updated: ProjectSession): Promise<void> {
        setSession(updated); await refreshTree(); setStatus(t('Project areas updated.'));
        window.dispatchEvent(new CustomEvent('hydra:project-session-changed'));
    }

    async function saveAllTabs(): Promise<void> {
        const dirtyTabs = tabs.filter(tab => tab.dirty && tab.content !== null);
        try {
            for (const tab of dirtyTabs) {
                await saveProjectFile(tab.path, tab.draft, tab.content!.etag, tab.content!.binary);
                const reloaded = await loadProjectFile(tab.path);
                setTabs(current => current.map(item => item.path === tab.path ? { ...item, content: reloaded, draft: reloaded.content, dirty: false } : item));
            }
            await refreshTree();
            setStatus(`Saved ${dirtyTabs.length} file(s)`);
        } catch (exception) { setError(exception instanceof Error ? exception.message : 'Could not save all files'); }
    }

    async function runSearch(): Promise<void> {
        if (searchQuery.trim() === '') { setSearchMatches([]); return; }
        try { const result = await searchProject(searchQuery.trim()); setSearchMatches(result.matches); }
        catch (exception) { setError(exception instanceof Error ? exception.message : 'Could not search project'); }
    }

    async function copySelectedRelativePath(): Promise<void> {
        setContextMenu(null);
        if(selectedNodes.length!==1)return;
        try { await navigator.clipboard.writeText(selectedNodes[0].relativePath); setStatus(`Copied ${selectedNodes[0].relativePath || '.'}`); }
        catch(exception){setError(exception instanceof Error?exception.message:t('Could not copy relative path'));}
    }

    async function copySelectedLocalPath(): Promise<void> {
        setContextMenu(null);
        try {
            const resolved = await resolveProjectPath(selectedPath);
            await navigator.clipboard.writeText(resolved.absolutePath);
            setStatus(`Copied ${resolved.absolutePath}`);
        } catch (exception) { setError(exception instanceof Error ? exception.message : 'Could not copy local path'); }
    }

    async function setSelectedAsPrimaryArtifact(): Promise<void> {
        setContextMenu(null);
        if(selectedNodes.length!==1||selectedNodes[0]?.nodeType!=='FILE'||session.project===null)return;
        const node=selectedNodes[0];
        try {
            const updated=await configureActiveProject({ enabledCapabilities:session.project.enabledCapabilities, experienceMode:session.project.experienceMode, primaryArtifact:{kind:node.artifactKind,id:node.relativePath,relativePath:node.relativePath} });
            setSession(updated);setStatus(`${node.name} is now the main project component.`);setError('');
            window.dispatchEvent(new CustomEvent('hydra:project-session-changed'));
        } catch(exception){setError(exception instanceof Error?exception.message:t('Could not set the main project component'));}
    }

    async function sync(direction: 'WORKSPACE_TO_PROJECT' | 'PROJECT_TO_WORKSPACE'): Promise<void> {
        try {
            const plan = await previewProjectSynchronization(direction, 'REPLACE');
            const actionable = plan.changes.filter(change => change.operation !== 'SKIP').length;
            if (!await requestDecision({ title: t('Apply project synchronization?'), description: `${actionable} ${t('files will be created or replaced. Review the direction before continuing.')}`, confirmLabel: t('Synchronize'), cancelLabel: t('Cancel') })) return;
            const result = await synchronizeProject(direction, 'REPLACE');
            await refreshTree();
            if (direction === 'PROJECT_TO_WORKSPACE') await onWorkspaceReload();
            setStatus(`Synchronization completed: ${result.appliedChanges.filter(change => change.operation !== 'SKIP').length} files`);
        } catch (exception) { setError(exception instanceof Error ? exception.message : 'Could not synchronize project'); }
    }

    useEffect(() => {
        let active = true;
        void Promise.all([getProjectSession(), listRecentProjects()]).then(async ([loadedSession, loadedRecent]) => {
            const restoredTabs = loadedSession.active
                ? await Promise.all(loadedSession.restoredTabs.map(async path => {
                    try {
                        const content = await loadProjectFile(path);
                        return { path, name: projectFileName(path), content, draft: content.content, dirty: false, loading: false, error: '' } satisfies ProjectTab;
                    } catch (exception) {
                        return { path, name: projectFileName(path), content: null, draft: '', dirty: false, loading: false, error: exception instanceof Error ? exception.message : 'Could not restore project file' } satisfies ProjectTab;
                    }
                }))
                : [];
            const loadedTree = loadedSession.active ? await loadProjectTree('LOGICAL') : null;
            if (!active) return;
            setSession(loadedSession);
            setRecentProjects(loadedRecent);
            setTree(loadedTree);
            setTabs(restoredTabs);
            if (restoredTabs.length > 0) setActiveLocation(current => ({ ...current, path: restoredTabs.at(-1)?.path ?? '' }));
        }).catch(exception => {
            if (active) setError(exception instanceof Error ? exception.message : 'Could not initialize local projects');
        });
        return () => { active = false; };
    }, []);

    useEffect(() => {
        const openManager = (event: Event): void => {
            const detail = (event as CustomEvent<{ mode?: 'CREATE' | 'ACCESS'; accessSource?: 'LOCAL' | 'ZIP' | 'DIRECTORY' | 'REPOSITORY' | 'EXTERNAL'; preset?: ProjectPreset; entryPoint?: 'GENERAL' | 'HOME_NEW_PROJECT' | 'HOME_OPEN_PROJECT' | 'HOME_IMPORT_PROJECT' | 'HOME_CONNECT_PROJECT' | 'HOME_QUICK_PRESET' }>).detail ?? {};
            setManagerDefaults({ mode: detail.mode ?? 'CREATE', accessSource: detail.accessSource ?? 'LOCAL', preset: detail.preset ?? 'EMPTY', entryPoint: detail.entryPoint ?? 'GENERAL' });
            setManagerOpen(true);
        };
        const openProject = (event: Event): void => {
            const detail = (event as CustomEvent<{ directory?: string }>).detail;
            if (detail?.directory) void handleOpenRef.current(detail.directory, false);
        };
        const openCapabilities = (): void => { if (session.active) setProjectCapabilitiesOpen(true); };
        window.addEventListener('hydra:project-manager-open', openManager);
        window.addEventListener('hydra:project-open', openProject);
        window.addEventListener('hydra:project-capabilities-open', openCapabilities);
        return () => {
            window.removeEventListener('hydra:project-manager-open', openManager);
            window.removeEventListener('hydra:project-open', openProject);
            window.removeEventListener('hydra:project-capabilities-open', openCapabilities);
        };
    }, [session.active, view]);

    useEffect(() => {
        if (!session.active) return undefined;
        let disposed = false;
        let timer: number | undefined;
        let consecutiveFailures = 0;
        const baseDelayMs = 5000;
        const maximumDelayMs = 60000;

        const schedule = (delayMs: number): void => {
            if (disposed) return;
            if (timer !== undefined) window.clearTimeout(timer);
            timer = window.setTimeout(() => void run(), delayMs);
        };
        const run = async (): Promise<void> => {
            if (disposed) return;
            if (document.visibilityState === 'hidden') {
                schedule(baseDelayMs);
                return;
            }
            try {
                const changeSet = await pollProjectChanges();
                if (changeSet.changes.length > 0) setChanges(current => [...current, ...changeSet.changes].slice(-200));
                if (consecutiveFailures >= 3) setStatus(t('Project change monitoring resumed.'));
                consecutiveFailures = 0;
                schedule(baseDelayMs);
            } catch {
                consecutiveFailures += 1;
                if (consecutiveFailures === 3) setStatus(t('Project change monitoring is temporarily unavailable. HYDRA will retry automatically.'));
                schedule(Math.min(maximumDelayMs, baseDelayMs * 2 ** Math.min(consecutiveFailures, 4)));
            }
        };
        const onVisibilityChange = (): void => {
            if (document.visibilityState === 'visible') schedule(250);
        };
        document.addEventListener('visibilitychange', onVisibilityChange);
        schedule(baseDelayMs);
        return () => {
            disposed = true;
            if (timer !== undefined) window.clearTimeout(timer);
            document.removeEventListener('visibilitychange', onVisibilityChange);
        };
    }, [session.active, t]);

    useEffect(() => {
        if (contextMenu === null) return undefined;
        const close = () => setContextMenu(null);
        const keydown = (event: KeyboardEvent) => { if (event.key === 'Escape') close(); };
        window.addEventListener('blur', close);
        window.addEventListener('resize', close);
        document.addEventListener('scroll', close, true);
        document.addEventListener('pointerdown', close);
        document.addEventListener('keydown', keydown);
        return () => {
            window.removeEventListener('blur', close); window.removeEventListener('resize', close);
            document.removeEventListener('scroll', close, true); document.removeEventListener('pointerdown', close); document.removeEventListener('keydown', keydown);
        };
    }, [contextMenu]);

    return (
        <section className="project-workbench-shell">
            <div className="project-command-bar">
                <div className="project-command-primary">
                    <HydraButton type="button" onClick={() => setManagerOpen(true)}>{t('Projects…')}</HydraButton>
                    <HydraButton disabled={!session.active || !tabs.some(tab => tab.dirty)} type="button" onClick={() => void saveAllTabs()}>{t('Save all files')}</HydraButton>
                    <HydraButton disabled={!session.active} type="button" onClick={() => void sync('WORKSPACE_TO_PROJECT')}>{t('Send app to project')}</HydraButton>
                    <HydraButton disabled={!session.active} type="button" onClick={() => void sync('PROJECT_TO_WORKSPACE')}>{t('Load project into app')}</HydraButton>
                    {session.active && <a className="project-command-link" href={projectArchiveDownloadUrl()}>{t('Download project ZIP')}</a>}
                    <HydraButton disabled={!session.active} type="button" onClick={() => setProjectCapabilitiesOpen(true)}>{t('Project areas…')}</HydraButton>
                    <HydraButton disabled={!session.active} type="button" onClick={() => setRemoteStorageOpen(true)}>{t('Save to repository')}</HydraButton>
                    {session.active && <a className="project-command-link" download={`${session.project?.id ?? 'project'}-descriptor.drawio`} href={projectDescriptorDrawioUrl()}>{t('Draw.io descriptor')}</a>}
                    {session.active && <a className="project-command-link" download={`${session.project?.id ?? 'project'}-tree.drawio`} href={projectTreeDrawioUrl(view)}>{t('Draw.io tree')}</a>}
                </div>
                <div className="project-command-context">
                    {session.active && <span className="project-active-path" title={session.directory}>{session.project?.name} · {session.directory}</span>}
                    <HydraButton disabled={!session.active} type="button" onClick={() => void handleCloseProject()}>{t('Close project')}</HydraButton>
                </div>
            </div>
            {error !== '' && <HydraActionableErrorNotice message={error} context="project explorer" onDismiss={() => setError('')} />}
            {status !== '' && error === '' && <div className="project-inline-status">{status}<HydraButton aria-label={t('Close status')} className="hydra-window-close" type="button" onClick={() => setStatus('')}>×</HydraButton></div>}
            <div className={`project-workbench-body${explorerVisible ? '' : ' explorer-hidden'}`}>
                {!explorerVisible && <ProjectExplorerCollapsedTab onExpand={() => setExplorerVisible(true)} />}
                {explorerVisible && <aside className="project-explorer-panel">
                    <ProjectExplorerPanelHeader active={session.active} projectName={session.project?.name} onRefresh={() => void refreshTree()} onCollapse={() => setExplorerVisible(false)} />
                    {session.active && <>
                        <div className="project-view-switch">
                            <HydraButton className={view === 'LOGICAL' ? 'active' : ''} type="button" onClick={() => { setView('LOGICAL'); void refreshTree('LOGICAL'); }}>{t('Logical')}</HydraButton>
                            <HydraButton className={view === 'PHYSICAL' ? 'active' : ''} type="button" onClick={() => { setView('PHYSICAL'); void refreshTree('PHYSICAL'); }}>{t('Files')}</HydraButton>
                        </div>
                        <div className="project-explorer-actions">
                            <HydraButton title={t('New file')} type="button" onClick={() => void createEntry(false)}>+F</HydraButton>
                            <HydraButton title={t('New directory')} type="button" onClick={() => void createEntry(true)}>+D</HydraButton>
                            <HydraButton disabled={selectedNode === null || selectedPath === ''} title={t('Rename')} type="button" onClick={() => void renameEntry()}>{t('Rename')}</HydraButton>
                            <HydraButton disabled={actionableSelectionPaths.length === 0} title={t('Copy')} type="button" onClick={copySelectedEntries}>{t('Copy')}</HydraButton>
                            <HydraButton disabled={!projectRootSelected && actionableSelectionPaths.length === 0} title={projectRootSelected ? t('Delete complete project') : t('Delete')} type="button" onClick={requestDeleteSelection}>{projectRootSelected ? t('Delete project') : t('Delete')}</HydraButton>
                            <HydraButton title={t('Upload')} type="button" onClick={() => openFileInputPicker(uploadRef.current)}>{t('Upload')}</HydraButton>
                            <HydraButton disabled={!session.active} title={t('Copy local path')} type="button" onClick={() => void copySelectedLocalPath()}>{t('Path')}</HydraButton>
                            <input hidden ref={uploadRef} type="file" onChange={(event: ChangeEvent<HTMLInputElement>) => { const file = event.target.files?.[0]; if (file !== undefined) void upload(file); event.target.value = ''; }} />
                        </div>
                    </>}
                    {session.active && <div className="project-search-box">
                        <input aria-label={t('Search project')} placeholder={t('Search files and references')} value={searchQuery} onChange={(event: ChangeEvent<HTMLInputElement>) => setSearchQuery(event.target.value)} onKeyDown={(event: { key: string }) => { if (event.key === 'Enter') void runSearch(); }} />
                        <HydraButton type="button" onClick={() => void runSearch()}>{t('Search')}</HydraButton>
                        {searchMatches.length > 0 && <HydraButton type="button" onClick={() => { setSearchMatches([]); setSearchQuery(''); }}>{t('Clear')}</HydraButton>}
                    </div>}
                    {searchMatches.length > 0 && <div className="project-search-results">
                        {searchMatches.map((match, index) => <HydraButton key={`${match.relativePath}:${match.line}:${index}`} type="button" onClick={() => { setSelectedPath(match.relativePath); setSelectedPaths(new Set([match.relativePath])); void openFile(match.relativePath); }}>
                            <strong>{match.relativePath}{match.line > 0 ? `:${match.line}:${match.column}` : ''}</strong><span>{match.preview}</span>
                        </HydraButton>)}
                    </div>}
                    <div className="project-tree-container">
                        {tree !== null ? <ProjectTree changedPaths={changedPaths} root={tree} selectedPath={selectedPath} selectedPaths={selectedPaths} onOpen={node => void openFile(node.relativePath)} onSelect={selectTreeNode} onContextMenu={openContextMenu} />
                            : <div className="project-empty-state"><p>{t('Create a new project or open/import one to see its files and project elements here.')}</p><HydraButton type="button" onClick={() => setManagerOpen(true)}>{t('Manage projects')}</HydraButton></div>}
                    </div>
                    {selectedPaths.size > 1 && <footer className="project-selection-details project-selection-count"><strong>{selectedPaths.size} selected items</strong><span>Ctrl/Cmd + click keeps the current selection.</span></footer>}
                    {selectedNode?.nodeType === 'FILE' && <footer className="project-selection-details">
                        <strong>{selectedNode.artifactKind}</strong><span>{selectedNode.relativePath}</span><span>{selectedNode.size} bytes</span>
                        <a href={projectFileDownloadUrl(selectedNode.relativePath)}>{t('Download')}</a>
                    </footer>}
                </aside>}
                <div className="project-editor-column">
                    <ProjectTabs activePath={activePath} tabs={tabs} onActivate={setActivePath} onClose={path => void closeTab(path)} />
                    <div className="project-editor-content">
                        {activeTab === null ? children : <ProjectFileEditor tab={activeTab}
                            onChange={value => setTabs(current => current.map(tab => tab.path === activeTab.path ? { ...tab, draft: value, dirty: value !== tab.content?.content } : tab))}
                            onReload={() => void reloadFile(activeTab.path)} onSave={() => void saveActiveFile()} />}
                    </div>
                </div>
            </div>
            {contextMenu !== null && <div className="project-context-menu" role="menu" style={{left: contextMenu.x, top: contextMenu.y}} onPointerDown={event => event.stopPropagation()}>
                {projectRootSelected && <><HydraButton role="menuitem" type="button" onClick={() => { setContextMenu(null); void createEntry(false); }}>{t('New file…')}</HydraButton><HydraButton role="menuitem" type="button" onClick={() => { setContextMenu(null); void createEntry(true); }}>{t('New folder…')}</HydraButton><HydraButton disabled={clipboardPaths.length===0} role="menuitem" type="button" onClick={() => void pasteClipboardIntoSelection()}>{t('Paste')}</HydraButton><div className="project-context-separator"/></>}
                {!projectRootSelected && (selectedNode?.nodeType==='DIRECTORY'||selectedNode?.nodeType==='GROUP') && <><HydraButton role="menuitem" type="button" onClick={() => { setContextMenu(null); void createEntry(false); }}>{t('New file…')}</HydraButton><HydraButton role="menuitem" type="button" onClick={() => { setContextMenu(null); void createEntry(true); }}>{t('New folder…')}</HydraButton><HydraButton role="menuitem" type="button" onClick={() => { setContextMenu(null); openFileInputPicker(uploadRef.current); }}>{t('Upload file…')}</HydraButton><HydraButton disabled={clipboardPaths.length===0} role="menuitem" type="button" onClick={() => void pasteClipboardIntoSelection()}>{t('Paste')}</HydraButton><div className="project-context-separator"/></>}
                <HydraButton disabled={!selectedNodes.some(node=>node.nodeType==='FILE')} role="menuitem" type="button" onClick={() => void openSelectedEntries()}>{t('Open')}</HydraButton>
                <HydraButton disabled={actionableSelectionPaths.length===0} role="menuitem" type="button" onClick={cutSelectedEntries}>{t('Cut')}</HydraButton>
                <HydraButton disabled={actionableSelectionPaths.length===0} role="menuitem" type="button" onClick={copySelectedEntries}>{t('Copy')}</HydraButton>
                <HydraButton disabled={actionableSelectionPaths.length===0} role="menuitem" type="button" onClick={() => void duplicateSelectedEntries()}>{t('Duplicate')}</HydraButton>
                <HydraButton disabled={actionableSelectionPaths.length!==1} role="menuitem" type="button" onClick={() => { setContextMenu(null); void renameEntry(); }}>{t('Rename…')}</HydraButton>
                {selectedNodes.length===1 && selectedNodes[0]?.nodeType==='FILE' && <HydraButton role="menuitem" type="button" onClick={downloadSelectedFile}>{t('Download file')}</HydraButton>}
                <div className="project-context-separator" />
                <HydraButton disabled={selectedNodes.length!==1} role="menuitem" type="button" onClick={() => void copySelectedRelativePath()}>{t('Copy relative path')}</HydraButton>
                <HydraButton disabled={selectedNodes.length!==1} role="menuitem" type="button" onClick={() => void copySelectedLocalPath()}>{t('Copy local path')}</HydraButton>
                {selectedNodes.length===1 && selectedNodes[0]?.nodeType==='FILE' && <HydraButton role="menuitem" type="button" onClick={() => void setSelectedAsPrimaryArtifact()}>{t('Set as main component')}</HydraButton>}
                <HydraButton disabled={actionableSelectionPaths.length===0} role="menuitem" type="button" onClick={() => void exportSelectedLocally()}>{t('Export selection as ZIP')}</HydraButton>
                <HydraButton disabled={actionableSelectionPaths.length===0} role="menuitem" type="button" onClick={exportSelectedRemotely}>{t('Export to repository…')}</HydraButton>
                <HydraButton role="menuitem" type="button" onClick={() => void refreshFromContextMenu()}>{t('Refresh')}</HydraButton>
                <HydraButton disabled={selectedNodes.length!==1} role="menuitem" type="button" onClick={() => { setContextMenu(null); setProjectPropertiesOpen(true); }}>{t('Properties…')}</HydraButton>
                {projectRootSelected && <><div className="project-context-separator"/><a className="project-context-link" role="menuitem" href={projectArchiveDownloadUrl()}>{t('Download project ZIP')}</a><HydraButton role="menuitem" type="button" onClick={() => { setContextMenu(null); setProjectCapabilitiesOpen(true); }}>{t('Project areas…')}</HydraButton><HydraButton role="menuitem" type="button" onClick={() => { setContextMenu(null); setRemoteStorageOpen(true); }}>{t('Repository / cloud…')}</HydraButton><HydraButton role="menuitem" type="button" onClick={() => { setContextMenu(null); void sync('WORKSPACE_TO_PROJECT'); }}>{t('Send workspace to project')}</HydraButton><HydraButton role="menuitem" type="button" onClick={() => { setContextMenu(null); void sync('PROJECT_TO_WORKSPACE'); }}>{t('Load project into workspace')}</HydraButton><HydraButton role="menuitem" type="button" onClick={() => { setContextMenu(null); void handleCloseProject(); }}>{t('Close project')}</HydraButton></>}
                <div className="project-context-separator" />
                <HydraButton className="danger" disabled={!projectRootSelected && actionableSelectionPaths.length===0} role="menuitem" type="button" onClick={requestDeleteSelection}>{projectRootSelected ? t('Delete complete project…') : t('Delete')}</HydraButton>
            </div>}
            <ProjectSelectionRemoteExportDialog open={remoteSelectionExportOpen} paths={actionableSelectionPaths} projectId={session.project?.id ?? ''} projectName={session.project?.name ?? ''} onClose={() => setRemoteSelectionExportOpen(false)} onExported={() => setStatus('Selection exported to the repository.')} />
            <RemoteProjectStorageDialog key={`${session.project?.id ?? ''}:${remoteStorageOpen}`} open={remoteStorageOpen} projectId={session.project?.id ?? ''} projectName={session.project?.name ?? ''} onClose={() => setRemoteStorageOpen(false)} />
            <ProjectEntryPropertiesDialog open={projectPropertiesOpen} node={selectedNode} session={session} onClose={() => setProjectPropertiesOpen(false)} />
            <ProjectCapabilitiesDialog open={projectCapabilitiesOpen} session={session} onClose={() => setProjectCapabilitiesOpen(false)} onConfigured={updated => void handleCapabilitiesConfigured(updated)} />
            <ProjectDeletionDialog key={projectDeletionOpen ? 'project-delete-open' : 'project-delete-closed'} open={projectDeletionOpen} onClose={() => setProjectDeletionOpen(false)} onDeleted={handleProjectDeleted} />
            {dialogs}
            <ProjectManagerDialog key={`${managerDefaults.mode}:${managerDefaults.accessSource}:${managerDefaults.preset}:${managerDefaults.entryPoint}:${managerOpen ? 'open' : 'closed'}`} open={managerOpen} initialMode={managerDefaults.mode} initialAccessSource={managerDefaults.accessSource} initialPreset={managerDefaults.preset} initialEntryPoint={managerDefaults.entryPoint} recentProjects={recentProjects} onClose={() => setManagerOpen(false)} onCreate={handleCreate} onImport={handleImport} onImportDirectory={handleImportDirectory} onImportBrowserDirectory={handleImportBrowserDirectory} onImportRemote={handleImportRepository} onImportExternal={handleImportExternal} onOpenProject={handleOpen} />
        </section>
    );
}
