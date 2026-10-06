import { HydraButton, HydraWindowControls } from '../design-system';
import type { ProjectSession, ProjectTreeNode } from './types';
import { useHydraLanguage } from '../i18n';
import { projectPresetLabel } from './projectPresentation';

interface Props { open:boolean; node:ProjectTreeNode|null; session:ProjectSession; onClose:()=>void; }

export function ProjectEntryPropertiesDialog({ open,node,session,onClose }:Props){
    const { t }=useHydraLanguage();
    if(!open||node===null)return null;
    const project=node.nodeType==='PROJECT';
    const rows:Array<[string,string]> = project ? [
        [t('Name'),session.project?.name??node.name],
        [t('Project ID'),session.project?.id??''],
        [t('Initial project type'),session.project?.createdFromPreset ? t(projectPresetLabel(session.project.createdFromPreset)) : ''],
        [t('Working mode'),session.project?.experienceMode??''],
        [t('Active areas'),String(session.project?.enabledCapabilities.length??0)],
        [t('Main component'),session.project?.primaryArtifact?.relativePath||t('Not selected')],
        [t('Directory'),session.directory],
    ] : [
        [t('Name'),node.name],
        [t('Type'),node.nodeType],
        [t('Content type'),node.artifactKind],
        [t('Relative path'),node.relativePath],
        [t('Ownership'),node.ownership],
        [t('Status'),node.status],
        [t('Size'),`${node.size} bytes`],
        [t('Last modified'),node.lastModified??'—'],
        [t('Editable'),node.editable?t('Yes'):t('No')],
        [t('File version'),node.etag||'—'],
    ];
    return <div className="project-dialog-backdrop" role="presentation"><section aria-modal="true" className="project-dialog project-entry-properties-dialog" role="dialog"><header><div><h2>{t('Properties')}</h2><p>{project?t('Main information and current configuration for this HYDRA project.'):t('Information about the selected project entry.')}</p></div><HydraWindowControls closeLabel={t('Close')} onClose={onClose}/></header><div className="project-dialog-scroll"><dl className="project-properties-grid">{rows.map(([label,value])=><div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl></div><footer><HydraButton type="button" onClick={onClose}>{t('Close')}</HydraButton></footer></section></div>;
}
