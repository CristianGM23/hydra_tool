import { HydraButton } from '../design-system/HydraButton';
import type { ChangeEvent } from 'react';
import { HydraActionableErrorNotice } from '../design-system';
import type { ProjectTab } from './types';
import { projectFileDownloadUrl } from './projectApi';

interface ProjectFileEditorProps {
    tab: ProjectTab;
    onChange: (value: string) => void;
    onSave: () => void;
    onReload: () => void;
}

export function ProjectFileEditor({ tab, onChange, onSave, onReload }: ProjectFileEditorProps) {
    if (tab.loading) return <div className="project-file-state">Loading {tab.name}…</div>;
    if (tab.error !== '') return <div className="project-file-state error"><HydraActionableErrorNotice message={tab.error} context="project file" /></div>;
    if (tab.content === null) return <div className="project-file-state">No file is loaded.</div>;
    if (tab.content.binary || !tab.content.editable) {
        return (
            <section className="project-file-preview">
                <h2>{tab.name}</h2>
                <p>This file cannot be edited as text. Media type: {tab.content.mediaType}; size: {tab.content.size} bytes.</p>
                <a href={projectFileDownloadUrl(tab.path)}>Download file</a>
            </section>
        );
    }
    return (
        <section className="project-file-editor">
            <header>
                <div>
                    <strong>{tab.path}</strong>
                    <span>{tab.content.artifactKind} · {tab.content.ownership}</span>
                </div>
                <div>
                    <HydraButton type="button" onClick={onReload}>Reload</HydraButton>
                    <HydraButton className="project-primary-action" disabled={!tab.dirty} type="button" onClick={onSave}>Save</HydraButton>
                </div>
            </header>
            <textarea aria-label={`Content of ${tab.name}`} spellCheck={false} value={tab.draft} onChange={(event: ChangeEvent<HTMLTextAreaElement>) => onChange(event.target.value)} />
        </section>
    );
}
