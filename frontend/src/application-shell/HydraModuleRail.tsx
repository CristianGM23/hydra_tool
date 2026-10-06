import { HydraButton } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { HYDRA_MODULES } from './moduleRegistry';
import type { HydraModuleCategory, HydraModuleId } from './types';

interface Props {
    activeModule: HydraModuleId;
    onNavigate: (moduleId: HydraModuleId) => void;
}

const categories: Array<{ id: HydraModuleCategory; label: string; description: string }> = [
    { id: 'DSL', label: 'Language and behavior', description: 'Define the language, its editors, rules, events and transformations.' },
    { id: 'USE', label: 'Models and connections', description: 'Create models and connect the project with external locations and technologies.' },
    { id: 'DELIVERY', label: 'Import and delivery', description: 'Recover existing work, package editors and prepare delivery.' },
    { id: 'OPERATIONS', label: 'Current status and tools', description: 'Review project readiness, available tools and generated outputs.' }
];

const homeSections: Array<{ id: 'start' | 'quick-create' | 'project-areas' | 'current-work' | 'recent-work'; label: string }> = [
    { id: 'start', label: 'Start or continue' },
    { id: 'quick-create', label: 'Quick start' },
    { id: 'project-areas', label: 'Project areas' },
    { id: 'current-work', label: 'Current work' },
    { id: 'recent-work', label: 'Recent work' }
];

export function HydraModuleRail({ activeModule, onNavigate }: Props) {
    const { t } = useHydraLanguage();
    const openHomeSection = (section: typeof homeSections[number]['id']) => {
        if (activeModule !== 'HOME') onNavigate('HOME');
        window.setTimeout(() => window.dispatchEvent(new CustomEvent('hydra:home-section-open', { detail: { section } })), activeModule === 'HOME' ? 0 : 60);
    };

    return <aside aria-label={t('HYDRA modules')} className="hydra-module-rail">
        <div className="hydra-module-rail-intro">
            <strong>{t('Project workflow')}</strong>
            <span>{t('Move through the project areas in the order that best fits your work.')}</span>
        </div>

        <section className="hydra-module-rail-home-section">
            <header><h2>{t('Home dashboard')}</h2><p>{t('Move between the main blocks of the current project dashboard.')}</p></header>
            <div>
                {homeSections.map(item => <HydraButton
                    key={item.id}
                    className="hydra-module-link hydra-home-global-link"
                    size="small"
                    variant={activeModule === 'HOME' ? 'quiet' : 'quiet'}
                    onClick={() => openHomeSection(item.id)}
                >{t(item.label)}</HydraButton>)}
            </div>
        </section>

        {categories.map(category => <section key={category.id}>
            <header><h2>{t(category.label)}</h2><p>{t(category.description)}</p></header>
            <div>{HYDRA_MODULES.filter(module => module.category === category.id).map(module => {
                const active = activeModule === module.id;
                return <div className={`hydra-module-rail-entry${active ? ' active' : ''}`} key={module.id}>
                    <HydraButton
                        aria-current={active ? 'page' : undefined}
                        className={`hydra-module-link accent-${module.accent}`}
                        size="small"
                        variant={active ? 'primary' : 'quiet'}
                        title={t(module.description)}
                        onClick={() => onNavigate(module.id)}
                    >{t(module.shortTitle)}</HydraButton>
                    {active && <p className="hydra-module-link-description">{t(module.description)}</p>}
                </div>;
            })}</div>
        </section>)}
    </aside>;
}
