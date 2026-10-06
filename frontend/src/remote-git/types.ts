export type RemoteGitOperation = 'STATUS' | 'INIT' | 'FETCH' | 'PULL' | 'PUSH' | 'MERGE' | 'ABORT_MERGE' | 'COMMIT_ALL' | 'CREATE_BRANCH' | 'CHECKOUT';
export type RemoteGitPullStrategy = 'FF_ONLY' | 'MERGE' | 'REBASE';

export interface RemoteGitOperationRequest {
    profileId: string;
    branch: string;
    sourceBranch: string;
    commitMessage: string;
    pullStrategy: RemoteGitPullStrategy;
}

export interface RemoteGitOperationResult {
    operation: RemoteGitOperation;
    profileId: string;
    success: boolean;
    initialized: boolean;
    currentBranch: string;
    remoteName: string;
    remoteBranch: string;
    ahead: number;
    behind: number;
    mergeInProgress: boolean;
    conflictedFiles: string[];
    changedFiles: string[];
    message: string;
    executedAt: string;
}

export interface RemoteGitCommitNode {
    id: string;
    shortId: string;
    parentIds: string[];
    authoredAt: string;
    subject: string;
}

export interface RemoteGitBranchNode {
    name: string;
    fullName: string;
    local: boolean;
    remote: boolean;
    current: boolean;
    upstream: string;
    ahead: number;
    behind: number;
    lastCommitShortId: string;
    lastCommitAt: string;
    lastCommitSubject: string;
    commits: RemoteGitCommitNode[];
}

export interface RemoteGitBranchGroup {
    id: string;
    label: string;
    description: string;
    branches: RemoteGitBranchNode[];
}

export interface RemoteGitRepositoryTree {
    profileId: string;
    repositoryRoot: string;
    initialized: boolean;
    currentBranch: string;
    mergeInProgress: boolean;
    groups: RemoteGitBranchGroup[];
}

export interface RemoteGitConflictFileView {
    profileId: string;
    relativePath: string;
    baseContent: string;
    localContent: string;
    remoteContent: string;
    resultContent: string;
    mergeInProgress: boolean;
}

export interface RemoteGitConflictResolutionRequest {
    profileId: string;
    relativePath: string;
    resolvedContent: string;
    stageResolved: boolean;
}

export interface RemoteGitConflictResolutionResult {
    relativePath: string;
    staged: boolean;
    remainingConflicts: string[];
    changedFiles: string[];
    message: string;
}
