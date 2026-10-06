export type ProjectTreeView = 'LOGICAL' | 'PHYSICAL';
export type ProjectNodeType = 'PROJECT' | 'GROUP' | 'DIRECTORY' | 'FILE';
export type ProjectNodeStatus = 'NORMAL' | 'GENERATED' | 'READ_ONLY' | 'EXTERNAL_CHANGE' | 'ERROR';
export type FileOwnership = 'OWNED' | 'GENERATED' | 'EXTERNAL' | 'USER_MANAGED';
export type ArtifactKind =
    | 'PROJECT_DESCRIPTOR' | 'PROJECT_INTERNAL' | 'METAMODEL' | 'METAMODEL_DIAGRAM' | 'MODEL' | 'EDITOR_MODEL_SESSION' | 'EDITOR_SESSION_DIAGRAM' | 'MODEL_DIAGRAM'
    | 'EDITOR_DEFINITION' | 'REPRESENTATION_DEFINITION' | 'PALETTE_DEFINITION' | 'FORM_DEFINITION' | 'NOTATION_DEFINITION'
    | 'EXPRESSION_MODEL' | 'VALIDATION_MODEL' | 'ACTION_MODEL' | 'OPERATION_BEHAVIOR_MODEL'
    | 'EVENT_DEFINITION_MODEL' | 'EVENT_BINDING_MODEL' | 'EVENT_FLOW_MODEL' | 'EVENT_ARCHITECTURE_DIAGRAM' | 'EVENT_FLOW_DIAGRAM' | 'EVENT_FLOW_RUNTIME' | 'GAMIFICATION_DIAGRAM'
    | 'GAMIFICATION_DEFINITION_MODEL' | 'GAMIFICATION_BINDING_MODEL' | 'INTEGRATION_PROFILE'
    | 'GENERATED_DRAWIO' | 'GENERATED_PLANTUML' | 'GENERATED_JAVA' | 'GENERATED_PYTHON' | 'GENERATED_ECORE'
    | 'GENERATED_IMAGE' | 'ASSET' | 'DOCUMENTATION' | 'TEST_RESOURCE' | 'GENERIC_FILE';

export type ProjectExperienceMode = 'GUIDED' | 'FREE';
export type ProjectPreset = 'EMPTY' | 'GRAPHICAL_LANGUAGE' | 'TEXTUAL_DSL' | 'HYBRID_LANGUAGE' | 'MODELING_VALIDATION' | 'M2M_TRANSFORMATION' | 'M2T_GENERATION' | 'T2M_ENGINEERING' | 'COMPLETE_EDITOR' | 'COMPLETE_MDE';

export interface ProjectPrimaryArtifact { kind: string; id: string; relativePath: string; }
export interface ProjectPresetDescriptor { id: ProjectPreset; label: string; description: string; capabilities: string[]; }
export interface ProjectCapabilityDescriptor { id: string; requires: string[]; }
export interface ProjectCreationCatalog { presets: ProjectPresetDescriptor[]; capabilities: ProjectCapabilityDescriptor[]; }

export interface ProjectDescriptor {
    schemaVersion: string;
    id: string;
    name: string;
    description: string;
    createdAt: string;
    updatedAt: string;
    storageMode: 'LOCAL_DIRECTORY' | 'IMPORTED_ARCHIVE';
    enabledCapabilities: string[];
    createdFromPreset: ProjectPreset;
    experienceMode: ProjectExperienceMode;
    primaryArtifact: ProjectPrimaryArtifact;
}

export interface ProjectSession {
    active: boolean;
    project: ProjectDescriptor | null;
    directory: string;
    openedAt: string | null;
    restoredTabs: string[];
}

export interface ProjectSummary {
    id: string;
    name: string;
    directory: string;
    lastOpenedAt: string | null;
    active: boolean;
    available: boolean;
}

export interface ProjectTreeNode {
    id: string;
    name: string;
    relativePath: string;
    nodeType: ProjectNodeType;
    artifactKind: ArtifactKind;
    ownership: FileOwnership;
    status: ProjectNodeStatus;
    editable: boolean;
    size: number;
    lastModified: string | null;
    etag: string;
    children: ProjectTreeNode[];
}

export interface ProjectFileContent {
    relativePath: string;
    name: string;
    artifactKind: ArtifactKind;
    ownership: FileOwnership;
    mediaType: string;
    charset: string;
    binary: boolean;
    content: string;
    etag: string;
    size: number;
    lastModified: string;
    editable: boolean;
}

export interface ProjectOperationResult {
    session: ProjectSession;
    relativePath: string;
    etag: string;
    tree: ProjectTreeNode;
}

export interface ProjectFileChange {
    type: 'CREATED' | 'MODIFIED' | 'DELETED';
    relativePath: string;
    artifactKind: ArtifactKind;
    detectedAt: string;
    etag: string;
}

export interface ProjectChangeSet {
    checkedAt: string;
    changes: ProjectFileChange[];
}

export interface ProjectSyncFileChange {
    source: string;
    destination: string;
    operation: 'CREATE' | 'REPLACE' | 'SKIP';
    artifactKind: ArtifactKind;
    size: number;
}

export interface ProjectSynchronizationPlan {
    direction: 'WORKSPACE_TO_PROJECT' | 'PROJECT_TO_WORKSPACE';
    mode: 'MERGE' | 'REPLACE';
    changes: ProjectSyncFileChange[];
    warnings: string[];
    executable: boolean;
}

export interface ProjectSynchronizationResult {
    direction: 'WORKSPACE_TO_PROJECT' | 'PROJECT_TO_WORKSPACE';
    mode: 'MERGE' | 'REPLACE';
    completedAt: string;
    appliedChanges: ProjectSyncFileChange[];
    warnings: string[];
}

export interface ProjectTab {
    path: string;
    name: string;
    content: ProjectFileContent | null;
    draft: string;
    dirty: boolean;
    loading: boolean;
    error: string;
}

export interface ProjectSearchMatch { relativePath: string; artifactKind: ArtifactKind; line: number; column: number; preview: string; }
export interface ProjectSearchResult { query: string; matches: ProjectSearchMatch[]; truncated: boolean; }
export interface ProjectResolvedPath { relativePath: string; absolutePath: string; directory: boolean; exists: boolean; }

export type ProjectPathPickerMode = 'DIRECTORY' | 'PROJECT_DESCRIPTOR' | 'ZIP_ARCHIVE' | 'METAMODEL_FILE';
export interface ProjectPathPickerResult {
    available: boolean;
    cancelled: boolean;
    selectedPath: string;
    selectedDirectory: string;
    fileName: string;
    mode: ProjectPathPickerMode;
    message: string;
}
