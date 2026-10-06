import { backendFetch } from '../api/backendFetch';
import { resolveBackendUrl } from '../api/backendConfig';
import { readActionableApiError } from '../api/actionableError';
import type {
    ProjectChangeSet,
    ProjectFileContent,
    ProjectOperationResult,
    ProjectSession,
    ProjectSummary,
    ProjectSynchronizationPlan,
    ProjectSynchronizationResult,
    ProjectSearchResult,
    ProjectResolvedPath,
    ProjectTreeNode,
    ProjectTreeView,
    ProjectPathPickerMode,
    ProjectPathPickerResult,
    ProjectCreationCatalog,
    ProjectExperienceMode,
    ProjectPreset,
    ProjectPrimaryArtifact,
} from './types';



export type ExternalProjectResourceKind =
    | 'METAMODEL' | 'GENMODEL' | 'XTEXT_GRAMMAR' | 'LANGIUM_GRAMMAR' | 'ANTLR_GRAMMAR'
    | 'SIRIUS_DESIGN' | 'GMF_MODEL' | 'EMF_EDIT_SOURCE' | 'EMF_EDIT_BYTECODE' | 'PLUGIN_DESCRIPTOR'
    | 'OSGI_MANIFEST' | 'BUILD_DESCRIPTOR' | 'GLSP_SOURCE' | 'BESSER_SOURCE' | 'VALIDATION_SOURCE'
    | 'QUICK_FIX_SOURCE' | 'SOURCE_CODE' | 'TEST_SOURCE' | 'IMAGE_ASSET' | 'PROJECT_RESOURCE';

export interface ExternalProjectResource { path:string; kind:ExternalProjectResourceKind; size:number; sha256:string; technologies:string[]; purpose:string; }
export interface ExternalProjectMappingEvidence { id:string; sourcePath:string; sourceLocator:string; sourceElement:string; targetKind:string; targetId:string; matchType:string; score:number; confidence:'EXACT'|'RECOVERED'|'DERIVED'|'INFERRED'; fidelity:'FULL'|'PARTIAL'|'UNSUPPORTED'; recoveredFields:string[]; lostFields:string[]; alternatives:string[]; reasons:string[]; autoSelectable:boolean; }
export interface ExternalProjectTopologyNode { id:string; label:string; type:string; path:string; parentId:string; technologies:string[]; }
export interface ExternalProjectIntegrationSuggestion { technology:string; projectPath:string; moduleName:string; buildSystem:string; sourceRoots:string[]; generatedFolder:string; evidencePaths:string[]; confidenceScore:number; rationale:string; }
export interface ExternalProjectGrammarInspection { sourcePath:string; technology:string; inferredEntryRule:string; inferenceMethod:string; inferenceConfidence:number; parserRules:string[]; recursiveRules:string[]; leftRecursiveRules:string[]; automaticResolutionRules:string[]; explanations:string[]; }
export interface ExternalProjectRecoveryReport { analysisId:string; analyzedAt:string; fingerprint:string; resourceCount:number; totalBytes:number; technologies:string[]; resources:ExternalProjectResource[]; topology:ExternalProjectTopologyNode[]; mappingEvidence:ExternalProjectMappingEvidence[]; integrationSuggestions:ExternalProjectIntegrationSuggestion[]; grammarInspections:ExternalProjectGrammarInspection[]; diagnostics:string[]; }
export interface ExternalProjectMaterializationResult { metamodelMaterialized:boolean; metamodelId:string; editorIds:string[]; visualValidationsImported:number; validationRulesImported:number; quickFixesImported:number; reviewOnlyQuickFixes:number; materializedSources:string[]; diagnostics:string[]; }
export interface ExternalProjectImportResult { session:ProjectSession; recovery:ExternalProjectRecoveryReport; materialization:ExternalProjectMaterializationResult; }

export interface ProjectDeletionDependency {
    path: string;
    artifactKind: string;
    entries: number;
    bytes: number;
}

export interface ProjectDeletionImpact {
    projectId: string;
    projectName: string;
    directory: string;
    fileCount: number;
    directoryCount: number;
    totalBytes: number;
    dependencies: ProjectDeletionDependency[];
}

export interface ProjectDeletionResult {
    projectId: string;
    projectName: string;
    directory: string;
    deletedEntries: number;
    deletedBytes: number;
}

async function errorMessage(response: Response): Promise<string> {
    return (await readActionableApiError(response)).message;
}

async function readJson<T>(response: Response): Promise<T> {
    if (!response.ok) {
        throw new Error(await errorMessage(response));
    }
    return response.json() as Promise<T>;
}

export async function getProjectSession(): Promise<ProjectSession> {
    return readJson<ProjectSession>(await backendFetch('/api/projects/session'));
}

export async function listRecentProjects(): Promise<ProjectSummary[]> {
    return readJson<ProjectSummary[]>(await backendFetch('/api/projects/recent'));
}

export async function getProjectCreationCatalog(): Promise<ProjectCreationCatalog> {
    return readJson<ProjectCreationCatalog>(await backendFetch('/api/projects/catalog'));
}

export async function createLocalProject(request: { parentDirectory: string; projectId: string; name: string; description: string; initializeFromCurrentWorkspace: boolean; preset: ProjectPreset; enabledCapabilities: string[] | null; experienceMode: ProjectExperienceMode }): Promise<ProjectSession> {
    return readJson<ProjectSession>(await backendFetch('/api/projects', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
    }));
}

export async function openLocalProject(projectDirectory: string, loadIntoCurrentWorkspace: boolean, transferMode: 'MERGE' | 'REPLACE'): Promise<ProjectSession> {
    return readJson<ProjectSession>(await backendFetch('/api/projects/open', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ projectDirectory, loadIntoCurrentWorkspace, transferMode }),
    }));
}

export async function closeLocalProject(): Promise<void> {
    const response = await backendFetch('/api/projects/close', { method: 'POST' });
    if (!response.ok) throw new Error(await errorMessage(response));
}

export async function getProjectDeletionImpact(): Promise<ProjectDeletionImpact> {
    return readJson<ProjectDeletionImpact>(await backendFetch('/api/projects/active/deletion-impact'));
}

export async function deleteActiveProject(deleteDependencies: boolean): Promise<ProjectDeletionResult> {
    return readJson<ProjectDeletionResult>(await backendFetch(`/api/projects/active?deleteDependencies=${deleteDependencies ? 'true' : 'false'}`, { method: 'DELETE' }));
}


export async function configureActiveProject(request: { enabledCapabilities: string[]; experienceMode: ProjectExperienceMode; primaryArtifact?: ProjectPrimaryArtifact }): Promise<ProjectSession> {
    return readJson<ProjectSession>(await backendFetch('/api/projects/active/configuration', {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
    }));
}

export async function loadProjectTree(view: ProjectTreeView): Promise<ProjectTreeNode> {
    return readJson<ProjectTreeNode>(await backendFetch(`/api/projects/active/tree?view=${encodeURIComponent(view)}`));
}

export async function loadProjectFile(path: string): Promise<ProjectFileContent> {
    return readJson<ProjectFileContent>(await backendFetch(`/api/projects/active/file?path=${encodeURIComponent(path)}`));
}

export async function saveProjectFile(path: string, content: string, expectedEtag: string, binary = false): Promise<ProjectOperationResult> {
    return readJson<ProjectOperationResult>(await backendFetch(`/api/projects/active/file?path=${encodeURIComponent(path)}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content, expectedEtag, binary }),
    }));
}

export async function createProjectEntry(relativePath: string, directory: boolean, content = ''): Promise<ProjectOperationResult> {
    return readJson<ProjectOperationResult>(await backendFetch('/api/projects/active/entries', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ relativePath, directory, content }),
    }));
}

export async function moveProjectEntry(sourcePath: string, targetPath: string, expectedEtag = ''): Promise<ProjectOperationResult> {
    return readJson<ProjectOperationResult>(await backendFetch('/api/projects/active/move', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sourcePath, targetPath, expectedEtag }),
    }));
}

export async function duplicateProjectEntry(sourcePath: string, targetPath: string): Promise<ProjectOperationResult> {
    return readJson<ProjectOperationResult>(await backendFetch('/api/projects/active/duplicate', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sourcePath, targetPath }),
    }));
}

export async function deleteProjectEntry(path: string, etag = ''): Promise<ProjectOperationResult> {
    const query = new URLSearchParams({ path });
    if (etag !== '') query.set('etag', etag);
    return readJson<ProjectOperationResult>(await backendFetch(`/api/projects/active/entries?${query.toString()}`, { method: 'DELETE' }));
}

export async function uploadProjectFile(directory: string, file: File): Promise<ProjectOperationResult> {
    const data = new FormData();
    data.append('file', file);
    return readJson<ProjectOperationResult>(await backendFetch(`/api/projects/active/upload?directory=${encodeURIComponent(directory)}`, { method: 'POST', body: data }));
}

function encodeSafeProjectBrowserPath(path: string): string {
    const normalized = String(path ?? '').trim().replace(/\\/g, '/');
    if (normalized === '' || normalized.startsWith('/') || /^[A-Za-z]:/.test(normalized)) {
        throw new Error('HYDRA_UNSAFE_PROJECT_BROWSER_PATH');
    }
    const segments = normalized.split('/');
    if (segments.some((segment) => segment === '' || segment === '.' || segment === '..')) {
        throw new Error('HYDRA_UNSAFE_PROJECT_BROWSER_PATH');
    }
    return encodeURIComponent(normalized);
}

export function projectFileDownloadUrl(path: string): string {
    return resolveBackendUrl(`/api/projects/active/download?path=${encodeSafeProjectBrowserPath(path)}`);
}

export function projectFileVisualUrl(path: string): string {
    return resolveBackendUrl(`/api/projects/active/visual?path=${encodeSafeProjectBrowserPath(path)}`);
}

export function projectArchiveDownloadUrl(): string {
    return resolveBackendUrl('/api/projects/active/export');
}

export async function exportProjectSelection(paths: string[]): Promise<Blob> {
    const response = await backendFetch('/api/projects/active/export-selection', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ paths }),
    });
    if (!response.ok) throw new Error(await errorMessage(response));
    return response.blob();
}

export async function importProjectArchive(parentDirectory: string, projectName: string, file: File): Promise<ProjectSession> {
    const data = new FormData();
    data.append('file', file);
    const query = new URLSearchParams({ parentDirectory, projectName });
    return readJson<ProjectSession>(await backendFetch(`/api/projects/import?${query.toString()}`, { method: 'POST', body: data }));
}

export async function pollProjectChanges(): Promise<ProjectChangeSet> {
    return readJson<ProjectChangeSet>(await backendFetch('/api/projects/active/changes'));
}

export async function previewProjectSynchronization(direction: 'WORKSPACE_TO_PROJECT' | 'PROJECT_TO_WORKSPACE', mode: 'MERGE' | 'REPLACE'): Promise<ProjectSynchronizationPlan> {
    return readJson<ProjectSynchronizationPlan>(await backendFetch('/api/projects/active/sync/preview', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ direction, mode }),
    }));
}

export async function synchronizeProject(direction: 'WORKSPACE_TO_PROJECT' | 'PROJECT_TO_WORKSPACE', mode: 'MERGE' | 'REPLACE'): Promise<ProjectSynchronizationResult> {
    return readJson<ProjectSynchronizationResult>(await backendFetch('/api/projects/active/sync', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ direction, mode }),
    }));
}

export async function saveProjectTabs(paths: string[]): Promise<ProjectSession> {
    return readJson<ProjectSession>(await backendFetch('/api/projects/active/tabs', {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ paths }),
    }));
}

export function projectDescriptorDrawioUrl(): string {
    return resolveBackendUrl('/api/projects/active/drawio/descriptor');
}

export function projectTreeDrawioUrl(view: ProjectTreeView): string {
    return resolveBackendUrl(`/api/projects/active/drawio/tree?view=${encodeURIComponent(view)}`);
}

export async function searchProject(query: string): Promise<ProjectSearchResult> {
    return readJson<ProjectSearchResult>(await backendFetch(`/api/projects/active/search?query=${encodeURIComponent(query)}`));
}

export async function resolveProjectPath(path: string): Promise<ProjectResolvedPath> {
    return readJson<ProjectResolvedPath>(await backendFetch(`/api/projects/active/local-path?path=${encodeURIComponent(path)}`));
}

export async function pickProjectPath(mode: ProjectPathPickerMode, initialDirectory = '', title = ''): Promise<ProjectPathPickerResult> {
    return readJson<ProjectPathPickerResult>(await backendFetch('/api/projects/path-picker', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mode, initialDirectory, title }),
    }));
}

export async function importProjectDirectory(sourceDirectory: string, parentDirectory: string, projectName: string): Promise<ProjectSession> {
    return readJson<ProjectSession>(await backendFetch('/api/projects/import-directory', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sourceDirectory, parentDirectory, projectName }),
    }));
}

export async function importProjectBrowserDirectory(files: File[], relativePaths: string[], parentDirectory: string, projectName: string): Promise<ProjectSession> {
    if (files.length === 0 || files.length !== relativePaths.length) throw new Error('Select a complete project folder before importing.');
    const data = new FormData();
    files.forEach((file, index) => { data.append('files', file, file.name); data.append('paths', relativePaths[index] ?? file.name); });
    const query = new URLSearchParams({ parentDirectory, projectName });
    return readJson<ProjectSession>(await backendFetch(`/api/projects/import-browser-directory?${query.toString()}`, { method: 'POST', body: data }));
}


export async function analyzeExternalProject(file: File): Promise<ExternalProjectRecoveryReport> {
    const data = new FormData(); data.append('file', file);
    return readJson<ExternalProjectRecoveryReport>(await backendFetch('/api/external-project-recovery/analyze', { method: 'POST', body: data }));
}

export async function importExternalProjectArchive(parentDirectory: string, projectName: string, file: File): Promise<ExternalProjectImportResult> {
    const data = new FormData(); data.append('file', file); data.append('parentDirectory', parentDirectory); data.append('projectName', projectName);
    return readJson<ExternalProjectImportResult>(await backendFetch('/api/external-project-recovery/import', { method: 'POST', body: data }));
}
