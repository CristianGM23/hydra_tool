import { useEffect, useMemo, useState } from 'react';
import { HydraButton, HydraFormField, HydraSelect, HydraTooltip } from '../design-system';
import { loadRemoteStorageProfiles } from '../remote-storage';
import type { RemoteStorageProfileDescriptor } from '../remote-storage';
import type { UpdateSiteRepositorySourceSummary } from './types';

interface Props {
    repositoryUrl: string;
    reference: string;
    subdirectory: string;
    profileId: string;
    busy: boolean;
    summary: UpdateSiteRepositorySourceSummary | null;
    onRepositoryUrlChange: (value: string) => void;
    onReferenceChange: (value: string) => void;
    onSubdirectoryChange: (value: string) => void;
    onProfileIdChange: (value: string) => void;
    onAnalyze: () => void;
}

/** Guided, security-conscious source form for Git/GitHub/GitLab update-site recovery. */
export function UpdateSiteRepositorySourcePanel(props: Props) {
    const provider = repositoryProvider(props.repositoryUrl);
    const [profiles, setProfiles] = useState<RemoteStorageProfileDescriptor[]>([]);
    useEffect(() => {
        let active = true;
        void loadRemoteStorageProfiles().then(values => {
            if(active) setProfiles(values.filter(profile => ['GITHUB', 'GITLAB', 'GENERIC_GIT'].includes(profile.provider)));
        }).catch(() => { if(active) setProfiles([]); });
        return () => { active = false; };
    }, []);
    const profileOptions = useMemo(() => [
        { value: '', label: 'Public repository', description: 'No credential profile is used.', category: 'Access' },
        ...profiles.map(profile => ({ value: profile.id, label: profile.displayName || profile.id,
            description: `${profile.provider} · ${profile.configured ? 'configured' : 'credentials missing'}${profile.enabled ? '' : ' · disabled'}`, category: profile.provider }))
    ], [profiles]);
    return <section className="update-site-control-section update-site-repository-source">
        <header className="update-site-source-section-heading">
            <div><span className="update-site-source-kicker">{provider}</span><h3>Import from Git repository</h3><p>Paste a repository URL or a GitHub/GitLab folder URL. HYDRA snapshots the repository, detects likely update-site roots and analyzes the files without executing repository code.</p></div>
            <HydraTooltip label="About Git repository source" title="Safe Git repository import"><p>HYDRA performs a bounded shallow clone into an isolated workspace. Credentials stay server-side, unsafe URLs and symbolic links are rejected, and the captured snapshot is reused for analysis, reconciliation and import.</p></HydraTooltip>
        </header>
        <div className="update-site-repository-source-grid dense">
            <HydraFormField width="wide" label="Repository or folder URL" targetId="guided-update-site-repository-url" required help="HTTPS only. You can paste a clone URL, a GitHub /tree/... URL or a GitLab /-/tree/... URL. HYDRA derives the repository, branch and folder when possible."><input id="guided-update-site-repository-url" value={props.repositoryUrl} onChange={event => props.onRepositoryUrlChange(event.target.value)} placeholder="https://github.com/org/repository/tree/main/updatesite" /></HydraFormField>
            <HydraFormField width="compact" label="Branch / tag" targetId="guided-update-site-repository-ref" help="Optional override. Leave empty when the branch is already present in a GitHub/GitLab folder URL."><input id="guided-update-site-repository-ref" value={props.reference} onChange={event => props.onReferenceChange(event.target.value)} placeholder="Auto / main" /></HydraFormField>
            <HydraFormField width="normal" label="Update-site folder" targetId="guided-update-site-repository-subdir" help="Optional folder inside a monorepo. HYDRA uses a sparse checkout when possible so a selected subfolder does not require downloading unrelated blobs."><input id="guided-update-site-repository-subdir" value={props.subdirectory} onChange={event => props.onSubdirectoryChange(event.target.value)} placeholder="Auto-detect / updatesite" /></HydraFormField>
            <HydraFormField width="normal" label="Repository access" targetId="guided-update-site-repository-profile" help="Choose an existing Git/GitHub/GitLab profile for a private repository. Secrets remain server-side and HYDRA verifies that the profile belongs to this exact repository before using it."><HydraSelect id="guided-update-site-repository-profile" ariaLabel="Repository access" showFilter={profiles.length > 6} value={props.profileId} options={profileOptions} onChange={props.onProfileIdChange} /></HydraFormField>
        </div>
        <div className="update-site-source-safety-strip"><span><b>Shallow snapshot</b> only the selected revision is captured.</span><span><b>Sparse when possible</b> monorepo subfolders avoid unrelated blobs.</span><span><b>No code execution</b> repository scripts and plug-ins remain inert.</span></div>
        <div className="update-site-setup-actions"><HydraButton size="small" variant="primary" loading={props.busy} disabled={props.repositoryUrl.trim() === ''} onClick={props.onAnalyze}>Capture and analyze repository</HydraButton></div>
        {props.summary !== null && <section className="update-site-repository-capture-summary" aria-label="Repository capture summary">
            <header><div><strong>Repository snapshot ready</strong><span>{props.summary.fileCount} files · {formatBytes(props.summary.totalBytes)} · {props.summary.provider}</span></div><code>{props.summary.snapshotId}</code></header>
            <div className="update-site-detected-root-list">
                <div className="update-site-detected-root-intro"><strong>Detected update-site roots</strong><span>Use a suggested root when a monorepo contains several products. Keeping the field empty analyzes the complete repository and can recover evidence across roots.</span></div>
                {props.summary.detectedRoots.map(root => <article className={root.recommended ? 'recommended' : ''} key={`${root.path}:${root.score}`}>
                    <div><strong>{root.path || 'Repository root'}</strong><small>{root.markers.join(' · ') || 'Fallback root'}</small></div>
                    <span>Score {root.score}</span>
                    {root.path !== '' && <HydraButton size="small" variant="ghost" onClick={() => props.onSubdirectoryChange(root.path)}>Use this root</HydraButton>}
                </article>)}
            </div>
            {props.summary.skippedPaths.length > 0 && <p className="update-site-inline-warning">{props.summary.skippedPaths.length} oversized file(s) were skipped by the configured safety limits.</p>}
        </section>}
    </section>;
}

function repositoryProvider(value: string): string {
    const normalized = value.toLowerCase();
    if (normalized.includes('github.com')) return 'GitHub';
    if (normalized.includes('gitlab')) return 'GitLab';
    return 'Git';
}
function formatBytes(value: number): string { if (value < 1024) return `${value} B`; if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`; return `${(value / (1024 * 1024)).toFixed(1)} MiB`; }
