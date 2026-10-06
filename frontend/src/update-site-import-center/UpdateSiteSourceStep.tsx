import { useRef, useState } from 'react';
import { HydraButton, HydraFormField, HydraTooltip, openFileInputPicker } from '../design-system';
import { UpdateSiteRepositorySourcePanel } from './UpdateSiteRepositorySourcePanel';
import type { UpdateSiteRepositorySourceSummary } from './types';

type SourceMode = 'LOCAL' | 'GIT' | 'REMOTE_P2';
interface Props {
    files: File[];
    analysisSource: string;
    repositoryUrl: string;
    repositoryReference: string;
    repositorySubdirectory: string;
    repositoryProfileId: string;
    repositorySummary: UpdateSiteRepositorySourceSummary | null;
    busy: boolean;
    onFilesSelected: (files: FileList | null) => void;
    onClearFiles: () => void;
    onRepositoryUrlChange: (value: string) => void;
    onRepositoryReferenceChange: (value: string) => void;
    onRepositorySubdirectoryChange: (value: string) => void;
    onRepositoryProfileIdChange: (value: string) => void;
    onAnalyzeRepository: () => void;
    onOpenRemote: () => void;
}

/** Simple source chooser: only the selected acquisition method is expanded. */
export function UpdateSiteSourceStep(props: Props) {
    const [mode, setMode] = useState<SourceMode>(() => props.analysisSource === 'REPOSITORY_SNAPSHOT' ? 'GIT' : props.analysisSource.startsWith('REMOTE') ? 'REMOTE_P2' : 'LOCAL');
    const archiveInputRef = useRef<HTMLInputElement>(null);
    const directoryInputRef = useRef<HTMLInputElement>(null);
    return <section className="update-site-source-step">
        <input ref={archiveInputRef} className="update-site-hidden-file-input" id="guided-update-site-archive" aria-label="Guided update site archive" type="file" accept=".zip,.jar,.xml,.p2,.updatesite" onChange={event => props.onFilesSelected(event.target.files)} />
        <input ref={directoryInputRef} className="update-site-hidden-file-input" aria-label="Guided update site directory" type="file" multiple {...({ webkitdirectory: '', directory: '' } as Record<string, string>)} onChange={event => props.onFilesSelected(event.target.files)} />
        <header className="update-site-source-step-heading"><div><h4>Where should HYDRA recover from?</h4><p>Choose one source. The remaining recovery workflow is identical, so you can review evidence and conflicts in the same place.</p></div><HydraTooltip label="About update-site sources" title="One recovery pipeline"><p>Archives, folders, remote p2 repositories and Git snapshots all enter the same bounded, non-executing analysis pipeline. Choose the source that preserves the most original metadata; the later evidence, conflict and recovery steps remain the same.</p></HydraTooltip></header>
        <nav className="update-site-source-methods" aria-label="Update-site source type">
            <HydraButton aria-pressed={mode === 'LOCAL'} className={mode === 'LOCAL' ? 'active' : ''} type="button" onClick={() => { setMode('LOCAL'); openFileInputPicker(archiveInputRef.current); }}><strong>Archive or folder</strong><span>Open a ZIP/JAR now, or choose an unpacked folder below</span></HydraButton>
            <HydraButton aria-pressed={mode === 'GIT'} className={mode === 'GIT' ? 'active' : ''} type="button" onClick={() => setMode('GIT')}><strong>Git repository</strong><span>Git, GitHub or GitLab URL</span></HydraButton>
            <HydraButton aria-pressed={mode === 'REMOTE_P2'} className={mode === 'REMOTE_P2' ? 'active' : ''} type="button" onClick={() => setMode('REMOTE_P2')}><strong>Remote p2</strong><span>Allowlisted HTTPS p2 endpoint</span></HydraButton>
        </nav>
        {mode === 'LOCAL' && <section className="update-site-source-method-panel">
            <header><div><span className="update-site-source-kicker">Local</span><h3>Choose an archive or folder</h3><p>HYDRA expands nested p2 and plug-in archives with bounded streaming and never loads the full repository into memory at once.</p></div></header>
            <HydraFormField label="Update-site source" targetId="guided-update-site-archive" required help="ZIP/JAR containers and unpacked directories are supported. content.xml, artifacts.xml, category.xml, feature.xml and Eclipse model descriptors improve recovery confidence.">
                <div className="update-site-source-pickers"><HydraButton size="small" onClick={() => openFileInputPicker(archiveInputRef.current)}>Choose archive</HydraButton><HydraButton size="small" onClick={() => openFileInputPicker(directoryInputRef.current)}>Choose directory</HydraButton></div>
            </HydraFormField>
            <div className={`update-site-file-summary${props.files.length > 0 ? ' selected' : ''}`}><div><strong>{props.files.length === 0 ? 'No local source selected' : `${props.files.length} file(s) selected`}</strong><small>{props.files.length === 0 ? 'Choose a ZIP/JAR or an unpacked repository folder.' : `${formatBytes(props.files.reduce((total, file) => total + file.size, 0))} selected · ${props.files[0].webkitRelativePath || props.files[0].name}`}</small></div>{props.files.length > 0 && <HydraButton size="small" variant="ghost" onClick={() => { if(archiveInputRef.current)archiveInputRef.current.value='';if(directoryInputRef.current)directoryInputRef.current.value='';props.onClearFiles(); }}>Clear</HydraButton>}</div>
        </section>}
        {mode === 'GIT' && <UpdateSiteRepositorySourcePanel repositoryUrl={props.repositoryUrl} reference={props.repositoryReference} subdirectory={props.repositorySubdirectory} profileId={props.repositoryProfileId} summary={props.repositorySummary} busy={props.busy} onRepositoryUrlChange={props.onRepositoryUrlChange} onReferenceChange={props.onRepositoryReferenceChange} onSubdirectoryChange={props.onRepositorySubdirectoryChange} onProfileIdChange={props.onRepositoryProfileIdChange} onAnalyze={props.onAnalyzeRepository} />}
        {mode === 'REMOTE_P2' && <section className="update-site-source-method-panel remote-p2"><header><div><span className="update-site-source-kicker">Remote p2</span><h3>Analyze an HTTPS p2 repository</h3><p>Use the dedicated remote workspace for composite repositories, mirrors and allowlisted p2 endpoints. Create a verified snapshot before importing.</p></div></header><HydraButton size="small" variant="primary" onClick={props.onOpenRemote}>Open Remote p2 workspace</HydraButton></section>}
    </section>;
}
function formatBytes(value: number): string { if(value<1024)return `${value} B`;if(value<1024*1024)return `${(value/1024).toFixed(1)} KiB`;return `${(value/(1024*1024)).toFixed(1)} MiB`; }
