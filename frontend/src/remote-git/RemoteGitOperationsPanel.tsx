import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { HydraButton, HydraFormField, HydraModal, HydraSelect, HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import {
    executeRemoteGitOperation,
    loadRemoteGitConflictView,
    loadRemoteGitRepositoryTree,
    saveRemoteGitConflictResolution,
} from './api';
import type {
    RemoteGitBranchGroup,
    RemoteGitBranchNode,
    RemoteGitConflictFileView,
    RemoteGitOperation,
    RemoteGitOperationRequest,
    RemoteGitOperationResult,
    RemoteGitPullStrategy,
    RemoteGitRepositoryTree,
} from './types';
import './remote-git.css';

interface Props {
    profileId: string;
    defaultBranch: string;
}

export function RemoteGitOperationsPanel({ profileId, defaultBranch }: Props) {
    const { t } = useHydraLanguage();
    const [status, setStatus] = useState<RemoteGitOperationResult | null>(null);
    const [repositoryTree, setRepositoryTree] = useState<RemoteGitRepositoryTree | null>(null);
    const [branch, setBranch] = useState(defaultBranch || 'main');
    const [sourceBranch, setSourceBranch] = useState('');
    const [commitMessage, setCommitMessage] = useState('');
    const [pullStrategy, setPullStrategy] = useState<RemoteGitPullStrategy>('FF_ONLY');
    const [busy, setBusy] = useState<RemoteGitOperation | ''>('');
    const [treeBusy, setTreeBusy] = useState(false);
    const [message, setMessage] = useState('');
    const [conflictBusy, setConflictBusy] = useState(false);
    const [activeConflictPath, setActiveConflictPath] = useState('');
    const [conflictView, setConflictView] = useState<RemoteGitConflictFileView | null>(null);
    const [resolvedContent, setResolvedContent] = useState('');

    const request = useCallback((): RemoteGitOperationRequest => ({
        profileId,
        branch,
        sourceBranch,
        commitMessage,
        pullStrategy,
    }), [profileId, branch, sourceBranch, commitMessage, pullStrategy]);

    const refreshTree = useCallback(async (preferredBranch = '') => {
        setTreeBusy(true);
        try {
            setRepositoryTree(await loadRemoteGitRepositoryTree(profileId, preferredBranch || branch));
        } catch (error) {
            setMessage(error instanceof Error ? error.message : t('Could not load the Git repository tree.'));
        } finally {
            setTreeBusy(false);
        }
    }, [branch, profileId, t]);

    const refreshAll = useCallback(async () => {
        setBusy('STATUS');
        setMessage('');
        try {
            const result = await executeRemoteGitOperation('STATUS', request());
            setStatus(result);
            if (result.currentBranch) setBranch(result.currentBranch);
            await refreshTree(result.currentBranch || branch);
        } catch (error) {
            setMessage(error instanceof Error ? error.message : t('Git operation failed.'));
        } finally {
            setBusy('');
        }
    }, [branch, refreshTree, request, t]);

    const execute = useCallback(async (operation: RemoteGitOperation) => {
        setBusy(operation); setMessage('');
        try {
            const result = await executeRemoteGitOperation(operation, request());
            setStatus(result);
            setMessage(result.message || (result.success ? t('Git operation completed.') : t('Git operation needs attention.')));
            if (result.currentBranch) setBranch(result.currentBranch);
            await refreshTree(result.currentBranch || branch);
        } catch (error) {
            setMessage(error instanceof Error ? error.message : t('Git operation failed.'));
        } finally { setBusy(''); }
    }, [branch, refreshTree, request, t]);

    useEffect(() => {
        let active = true;
        const run = async (): Promise<void> => {
            if (!active) return;
            await refreshAll();
        };
        void run();
        return () => { active = false; };
    }, [profileId, refreshAll]);

    const initialized = status?.initialized === true;
    const conflicts = status?.conflictedFiles ?? [];
    const changes = status?.changedFiles ?? [];
    const selectedBranchNode = useMemo(() => repositoryTree?.groups.flatMap(group => group.branches).find(item => item.name === branch || item.fullName === branch) ?? null, [branch, repositoryTree]);

    async function openConflict(path: string): Promise<void> {
        setConflictBusy(true);
        try {
            const view = await loadRemoteGitConflictView(profileId, path);
            setActiveConflictPath(path);
            setConflictView(view);
            setResolvedContent(view.resultContent);
        } catch (error) {
            setMessage(error instanceof Error ? error.message : t('Could not load the visual conflict view.'));
        } finally {
            setConflictBusy(false);
        }
    }

    async function saveConflict(stageResolved: boolean): Promise<void> {
        if (activeConflictPath === '') return;
        setConflictBusy(true);
        try {
            const result = await saveRemoteGitConflictResolution({
                profileId,
                relativePath: activeConflictPath,
                resolvedContent,
                stageResolved,
            });
            setMessage(result.message || t('Conflict resolution saved.'));
            setConflictView(null);
            setActiveConflictPath('');
            await refreshAll();
        } catch (error) {
            setMessage(error instanceof Error ? error.message : t('Could not save the conflict resolution.'));
        } finally {
            setConflictBusy(false);
        }
    }

    return <section className="remote-git-panel">
        <header className="remote-git-panel-heading">
            <div>
                <strong>{t('Git repository operations')}</strong>
                <span>{t('Operate on the currently open local HYDRA project using the selected repository profile. HYDRA exposes only bounded Git actions; it never accepts an arbitrary shell command.')}</span>
            </div>
            <HydraTooltip label={t('Information about Git repository operations')} title={t('Safe Git workflow')}>
                <p>{t('Initialize connects the active project to the selected remote without embedding credentials in the Git URL. Fetch downloads remote references, Pull integrates the selected remote branch, Push uploads local commits, and Merge combines a chosen source branch into the current branch. Force push and arbitrary reset commands are intentionally not exposed.')}</p>
            </HydraTooltip>
        </header>

        <div className="remote-git-status-grid">
            <StatusCard label={t('Repository state')} value={initialized ? t('Git initialized') : t('Not initialized')} />
            <StatusCard label={t('Current branch')} value={status?.currentBranch || '—'} />
            <StatusCard label={t('Remote')} value={status?.remoteName || `hydra-${profileId}`} />
            <StatusCard label={t('Ahead / behind')} value={initialized ? `${status?.ahead ?? 0} / ${status?.behind ?? 0}` : '—'} />
        </div>

        {!initialized && <div className="remote-git-callout">
            <strong>{t('Initialize this project first')}</strong>
            <p>{t('The active HYDRA project is not yet a Git working tree. Initialization creates local Git metadata and connects a safe remote URL derived from this repository profile; no token is written into .git/config.')}</p>
            <HydraButton loading={busy === 'INIT'} variant="primary" onClick={() => void execute('INIT')}>{t('Initialize and connect')}</HydraButton>
        </div>}

        {initialized && <>
            <div className="remote-git-field-grid">
                <HydraFormField
                    example="main"
                    help={t('Remote branch used by Pull and Push. Pull first fetches the branch into a remote-tracking ref. Push never uses --force and therefore preserves the remote non-fast-forward protection.')}
                    label={t('Remote branch')}
                    required
                    targetId="remote-git-branch"
                ><input id="remote-git-branch" value={branch} onChange={event => setBranch(event.target.value)} /></HydraFormField>
                <HydraFormField
                    example="feature/validation-ui"
                    help={t('Branch to merge into the currently checked-out branch. HYDRA uses a local branch when it exists; otherwise it fetches the named remote branch first. Conflicts are reported instead of being auto-resolved.')}
                    label={t('Merge source branch')}
                    targetId="remote-git-source-branch"
                ><input id="remote-git-source-branch" value={sourceBranch} onChange={event => setSourceBranch(event.target.value)} /></HydraFormField>
                <HydraFormField
                    help={t('Fast-forward only is the safest default because it refuses divergent history. Merge creates a merge commit when necessary. Rebase replays local commits on top of the remote branch and should be chosen only when that history policy is intended.')}
                    label={t('Pull strategy')}
                    targetId="remote-git-pull-strategy"
                    options={[
                        { value: 'FF_ONLY', label: t('Fast-forward only'), description: t('Reject divergent history instead of creating a merge commit.') },
                        { value: 'MERGE', label: t('Merge'), description: t('Integrate remote changes with a normal Git merge.') },
                        { value: 'REBASE', label: t('Rebase'), description: t('Replay local commits on top of the fetched remote branch.') },
                    ]}
                ><HydraSelect id="remote-git-pull-strategy" ariaLabel={t('Pull strategy')} showFilter={false} value={pullStrategy} options={[
                    { value: 'FF_ONLY', label: t('Fast-forward only') },
                    { value: 'MERGE', label: t('Merge') },
                    { value: 'REBASE', label: t('Rebase') },
                ]} onChange={value => setPullStrategy(value as RemoteGitPullStrategy)} /></HydraFormField>
                <HydraFormField
                    example={t('Update validation rules and repository settings')}
                    help={t('Commit message used by Commit all. HYDRA stages the current project changes with git add -A and creates one local commit. It does not push automatically; Push remains a separate explicit action.')}
                    label={t('Commit message')}
                    targetId="remote-git-commit-message"
                ><input id="remote-git-commit-message" value={commitMessage} onChange={event => setCommitMessage(event.target.value)} /></HydraFormField>
            </div>

            <div className="remote-git-action-groups">
                <ActionGroup title={t('Synchronize')} description={t('Exchange commits and remote references without force operations.') }>
                    <HydraButton loading={busy === 'FETCH'} onClick={() => void execute('FETCH')}>{t('Fetch')}</HydraButton>
                    <HydraButton loading={busy === 'PULL'} onClick={() => void execute('PULL')}>{t('Pull')}</HydraButton>
                    <HydraButton loading={busy === 'PUSH'} variant="primary" onClick={() => void execute('PUSH')}>{t('Push')}</HydraButton>
                </ActionGroup>
                <ActionGroup title={t('Local history')} description={t('Create commits and branches explicitly before synchronizing them.') }>
                    <HydraButton disabled={commitMessage.trim() === ''} loading={busy === 'COMMIT_ALL'} onClick={() => void execute('COMMIT_ALL')}>{t('Commit all')}</HydraButton>
                    <HydraButton disabled={branch.trim() === ''} loading={busy === 'CREATE_BRANCH'} onClick={() => void execute('CREATE_BRANCH')}>{t('Create branch')}</HydraButton>
                    <HydraButton disabled={branch.trim() === ''} loading={busy === 'CHECKOUT'} onClick={() => void execute('CHECKOUT')}>{t('Checkout branch')}</HydraButton>
                </ActionGroup>
                <ActionGroup title={t('Merge')} description={t('Integrate another branch and surface conflicts for manual resolution.') }>
                    <HydraButton disabled={sourceBranch.trim() === ''} loading={busy === 'MERGE'} onClick={() => void execute('MERGE')}>{t('Merge source')}</HydraButton>
                    <HydraButton disabled={!status?.mergeInProgress} loading={busy === 'ABORT_MERGE'} variant="danger" onClick={() => void execute('ABORT_MERGE')}>{t('Abort merge / rebase')}</HydraButton>
                    <HydraButton loading={busy === 'STATUS'} variant="ghost" onClick={() => void refreshAll()}>{t('Refresh status')}</HydraButton>
                </ActionGroup>
            </div>

            <section className="remote-git-tree-panel">
                <header>
                    <div>
                        <strong>{t('Repository tree and branches')}</strong>
                        <span>{t('Understand the repository as a tree: HYDRA groups the local and remote branches and shows the latest commits under each branch so it is easier to decide when to checkout, pull, push or merge.')}</span>
                    </div>
                    <HydraButton loading={treeBusy} variant="ghost" onClick={() => void refreshTree()}>{t('Refresh tree')}</HydraButton>
                </header>
                {repositoryTree === null ? <p className="remote-git-empty-state">{t('The repository tree will appear here after HYDRA reads the current Git state.')}</p>
                    : <>
                        <div className="remote-git-tree-summary">
                            <StatusCard label={t('Repository root')} value={repositoryTree.repositoryRoot} />
                            <StatusCard label={t('Checked-out branch')} value={repositoryTree.currentBranch || '—'} />
                            <StatusCard label={t('Merge workflow')} value={repositoryTree.mergeInProgress ? t('Merge or rebase in progress') : t('Clean working state')} />
                            <StatusCard label={t('Selected branch commits')} value={selectedBranchNode ? selectedBranchNode.commits.length.toString() : '0'} />
                        </div>
                        <div className="remote-git-tree-groups">
                            {repositoryTree.groups.map(group => <BranchGroupView key={group.id} group={group} currentBranch={repositoryTree.currentBranch} onCheckout={value => setBranch(value)} t={t} />)}
                        </div>
                    </>}
            </section>
        </>}

        {(changes.length > 0 || conflicts.length > 0) && <div className="remote-git-file-state">
            {conflicts.length > 0 && <section className="conflicts"><strong>{t('Merge conflicts')}</strong><p>{t('Open any conflicted file to compare the base, local, remote and result versions inside HYDRA before you stage it and continue the merge.')}</p><ul>{conflicts.map(file => <li key={file}><code>{file}</code><HydraButton variant="ghost" loading={conflictBusy && activeConflictPath === file} onClick={() => void openConflict(file)}>{t('Resolve visually')}</HydraButton></li>)}</ul></section>}
            {changes.length > 0 && <section><strong>{t('Local changes')}</strong><ul>{changes.slice(0, 30).map(file => <li key={file}><code>{file}</code></li>)}</ul>{changes.length > 30 && <small>{t('More changed files are present; the list is truncated in this panel.')}</small>}</section>}
        </div>}
        {message !== '' && <p className="remote-git-message" role="status">{message}</p>}

        <HydraModal
            open={conflictView !== null}
            onClose={() => { setConflictView(null); setActiveConflictPath(''); }}
            title={t('Visual conflict resolution')}
            description={t('Review the base, local and remote versions side by side and edit the resulting content that HYDRA should keep in the working tree.')}
            size="wide"
            className="remote-git-conflict-modal"
            footer={<>
                <HydraButton onClick={() => { setConflictView(null); setActiveConflictPath(''); }}>{t('Close')}</HydraButton>
                <HydraButton disabled={conflictBusy || conflictView === null} loading={conflictBusy} variant="ghost" onClick={() => void saveConflict(false)}>{t('Save result only')}</HydraButton>
                <HydraButton disabled={conflictBusy || conflictView === null} loading={conflictBusy} variant="primary" onClick={() => void saveConflict(true)}>{t('Save and stage resolved file')}</HydraButton>
            </>}
        >
            {conflictView !== null && <div className="remote-git-conflict-layout">
                <header className="remote-git-conflict-header">
                    <strong>{conflictView.relativePath}</strong>
                    <span>{t('Use the result editor as the final merged document. The other panes are read-only references that explain where the conflict comes from.')}</span>
                </header>
                <div className="remote-git-conflict-grid">
                    <ConflictPane title={t('Base')} description={t('Common ancestor version used by Git as the merge starting point.')} value={conflictView.baseContent} readOnly />
                    <ConflictPane title={t('Local / ours')} description={t('Content from the currently checked-out branch before integrating the incoming changes.')} value={conflictView.localContent} readOnly />
                    <ConflictPane title={t('Remote / theirs')} description={t('Incoming content from the branch or remote reference being merged or rebased.')} value={conflictView.remoteContent} readOnly />
                    <ConflictPane title={t('Result')} description={t('Editable result that HYDRA will write back to the working tree.')} value={resolvedContent} onChange={setResolvedContent} />
                </div>
            </div>}
        </HydraModal>
    </section>;
}

function StatusCard({ label, value }: { label: string; value: string }) { return <div><span>{label}</span><strong title={value}>{value}</strong></div>; }
function ActionGroup({ title, description, children }: { title: string; description: string; children: ReactNode }) { return <section><header><strong>{title}</strong><span>{description}</span></header><div>{children}</div></section>; }

function BranchGroupView({ group, currentBranch, onCheckout, t }: { group: RemoteGitBranchGroup; currentBranch: string; onCheckout: (value: string) => void; t: (key: string) => string }) {
    return <section className="remote-git-branch-group">
        <header>
            <strong>{group.label}</strong>
            <span>{group.description}</span>
        </header>
        <ul>
            {group.branches.map(branch => <BranchNodeView key={branch.fullName} branch={branch} currentBranch={currentBranch} onCheckout={onCheckout} t={t} />)}
        </ul>
    </section>;
}

function BranchNodeView({ branch, currentBranch, onCheckout, t }: { branch: RemoteGitBranchNode; currentBranch: string; onCheckout: (value: string) => void; t: (key: string) => string }) {
    const targetBranch = branch.local ? branch.name : branch.fullName.replace(/^origin\//, '');
    return <li className={`remote-git-branch-node ${branch.current || currentBranch === branch.name ? 'current' : ''}`}>
        <div className="remote-git-branch-title">
            <div>
                <strong>{branch.name}</strong>
                <span>{branch.lastCommitSubject || t('No commits detected')}</span>
            </div>
            <div className="remote-git-branch-badges">
                {branch.current && <mark>{t('Current')}</mark>}
                {branch.local && <small>{t('Local')}</small>}
                {branch.remote && <small>{t('Remote')}</small>}
                {(branch.ahead > 0 || branch.behind > 0) && <small>{branch.ahead}/{branch.behind}</small>}
                {branch.local && <HydraButton variant="ghost" onClick={() => onCheckout(targetBranch)}>{t('Use as target branch')}</HydraButton>}
            </div>
        </div>
        <div className="remote-git-branch-meta">
            <span>{t('Last commit')}: <code>{branch.lastCommitShortId || '—'}</code></span>
            {branch.upstream !== '' && <span>{t('Upstream')}: <code>{branch.upstream}</code></span>}
            {branch.lastCommitAt !== '' && <span>{branch.lastCommitAt}</span>}
        </div>
        {branch.commits.length > 0 && <ol className="remote-git-commit-tree">
            {branch.commits.map(commit => <li key={commit.id}><span className="commit-id">{commit.shortId}</span><div><strong>{commit.subject}</strong><small>{commit.authoredAt}</small></div></li>)}
        </ol>}
    </li>;
}

function ConflictPane({ title, description, value, readOnly = false, onChange }: { title: string; description: string; value: string; readOnly?: boolean; onChange?: (value: string) => void }) {
    return <section className="remote-git-conflict-pane">
        <header><strong>{title}</strong><span>{description}</span></header>
        <textarea value={value} readOnly={readOnly} onChange={event => onChange?.(event.target.value)} spellCheck={false} />
    </section>;
}
