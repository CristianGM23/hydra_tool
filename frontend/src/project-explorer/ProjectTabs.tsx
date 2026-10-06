import { HydraButton } from '../design-system/HydraButton';
import type { ProjectTab } from './types';

interface ProjectTabsProps {
    tabs: ProjectTab[];
    activePath: string;
    onActivate: (path: string) => void;
    onClose: (path: string) => void;
}

/**
 * Shows only opened project files. The active HYDRA layer is already visible in
 * the module rail/header, so repeating it as a tab adds noise without adding a
 * navigation capability.
 */
export function ProjectTabs({ tabs, activePath, onActivate, onClose }: ProjectTabsProps) {
    if (tabs.length === 0) return null;
    return (
        <div className="project-tabs" role="tablist" aria-label="Open project files">
            {tabs.map(tab => (
                <div className={`project-tab${activePath === tab.path ? ' active' : ''}`} key={tab.path} role="tab" aria-selected={activePath === tab.path}>
                    <HydraButton type="button" onClick={() => onActivate(tab.path)}>{tab.name}{tab.dirty ? ' *' : ''}</HydraButton>
                    <HydraButton aria-label={`Close ${tab.name}`} className="project-tab-close" type="button" onClick={() => onClose(tab.path)}>×</HydraButton>
                </div>
            ))}
        </div>
    );
}
