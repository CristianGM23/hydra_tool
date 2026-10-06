import { HydraButton } from '../design-system/HydraButton';
import { useHydraLanguage } from '../i18n';

function ProjectExplorerToggleIcon({ expanded }: { expanded: boolean }) {
    return <svg aria-hidden="true" className="project-explorer-toggle-icon" viewBox="0 0 20 20"><path d={expanded ? 'M12.5 4.5 7 10l5.5 5.5' : 'M7.5 4.5 13 10l-5.5 5.5'} fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.8"/></svg>;
}

export function ProjectExplorerCollapsedTab({ onExpand }: { onExpand: () => void }) {
    const { t } = useHydraLanguage();
    return <HydraButton aria-label={t('Expand project explorer')} className="project-explorer-collapsed-tab" title={t('Expand project explorer')} type="button" onClick={onExpand}><ProjectExplorerToggleIcon expanded={false}/><span>{t('Project explorer')}</span></HydraButton>;
}

export function ProjectExplorerPanelHeader({ active, projectName, onRefresh, onCollapse }: { active: boolean; projectName?: string; onRefresh: () => void; onCollapse: () => void }) {
    const { t } = useHydraLanguage();
    return <header><div><strong>{t('PROJECT EXPLORER')}</strong><span>{active ? projectName : t('No project open')}</span></div><div className="project-explorer-header-actions"><HydraButton disabled={!active} aria-label={t('Refresh project explorer')} title={t('Refresh')} type="button" onClick={onRefresh}>↻</HydraButton><HydraButton aria-label={t('Collapse project explorer')} className="project-explorer-toggle" title={t('Collapse project explorer')} type="button" onClick={onCollapse}><ProjectExplorerToggleIcon expanded /></HydraButton></div></header>;
}
