import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import type { MetamodelSummary } from '../types/editor';
import { listGrammars } from '../grammar-designer/api';
import type { GrammarSummary } from '../grammar-designer/types';
import { HydraButton, HydraSelect, HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { recordAuditActivity } from '../audit-log';
import { getProjectCreationCatalog, getProjectSession, listRecentProjects } from '../project-explorer/projectApi';
import type { ProjectCreationCatalog, ProjectPreset, ProjectSession, ProjectSummary } from '../project-explorer/types';
import { projectPresetDescription, projectPresetGuidance, projectPresetLabel } from '../project-explorer/projectPresentation';
import type { HydraModuleId } from './types';

const LazyCapabilityReadinessView = lazy(() => import('../capability-center/CapabilityReadinessView')
    .then(module => ({ default: module.CapabilityReadinessView })));

interface Props {
    metamodels: MetamodelSummary[];
    selectedMetamodelId: string;
    onMetamodelChange: (metamodelId: string) => void;
    onNavigate: (moduleId: HydraModuleId) => void;
}

interface HomeArea {
    id: string;
    title: string;
    description: string;
    capabilities: string[];
    module: HydraModuleId;
}

type ProjectManagerEntryPoint = 'GENERAL' | 'HOME_NEW_PROJECT' | 'HOME_OPEN_PROJECT' | 'HOME_IMPORT_PROJECT' | 'HOME_CONNECT_PROJECT' | 'HOME_QUICK_PRESET';
type HomeSectionId = 'start' | 'project-areas' | 'quick-create' | 'current-work' | 'recent-work';

const PROJECT_AREAS: readonly HomeArea[] = [
    { id: 'language', title: 'Design the language', description: 'Define the concepts, the grammar and the way they are edited.', capabilities: ['METAMODEL_DESIGN', 'GRAMMAR_DESIGN', 'EDITOR_DEFINITIONS', 'VISUAL_EDITOR', 'TEXTUAL_EDITOR'], module: 'METAMODEL' },
    { id: 'models', title: 'Create and edit models', description: 'Work with examples and models created with your language.', capabilities: ['MODEL_EDITING', 'TEXTUAL_MODELING', 'VISUAL_MODELING'], module: 'MODEL' },
    { id: 'quality', title: 'Check and correct', description: 'Add rules to detect problems and help solve them.', capabilities: ['VALIDATIONS', 'QUICK_FIXES'], module: 'VALIDATIONS' },
    { id: 'behavior', title: 'Define behavior', description: 'Configure events, flows and mechanics that react to what happens in models.', capabilities: ['EVENTS', 'EVENT_FLOWS', 'GAMIFICATION', 'OPERATIONS'], module: 'EVENTS' },
    { id: 'transform', title: 'Transform and generate', description: 'Convert models, generate text or code and prepare reusable editors.', capabilities: ['TRANSFORMATION_M2M', 'TRANSFORMATION_M2T', 'TRANSFORMATION_T2M', 'EDITOR_GENERATION', 'DRAWIO_EXPORT', 'PLANTUML_EXPORT'], module: 'TRANSFORMATIONS' },
    { id: 'share', title: 'Save, connect and share', description: 'Connect the project with external repositories and prepare its distribution.', capabilities: ['REPOSITORY_INTEGRATION', 'INTEGRATIONS'], module: 'REPOSITORIES' },
] as const;

const QUICK_PRESETS: readonly ProjectPreset[] = ['GRAPHICAL_LANGUAGE', 'TEXTUAL_DSL', 'MODELING_VALIDATION', 'M2M_TRANSFORMATION', 'COMPLETE_EDITOR', 'COMPLETE_MDE'];

export function HydraHome({ metamodels, selectedMetamodelId, onMetamodelChange, onNavigate }: Props) {
    const { t } = useHydraLanguage();
    const [grammars, setGrammars] = useState<GrammarSummary[]>([]);
    const [projectSession, setProjectSession] = useState<ProjectSession | null>(null);
    const [recentProjects, setRecentProjects] = useState<ProjectSummary[]>([]);
    const [catalog, setCatalog] = useState<ProjectCreationCatalog | null>(null);
    const contextKey = selectedMetamodelId === '' ? '' : selectedMetamodelId.startsWith('GRAMMAR:') ? selectedMetamodelId : `METAMODEL:${selectedMetamodelId}`;
    const startSectionRef = useRef<HTMLElement | null>(null);
    const projectAreasSectionRef = useRef<HTMLElement | null>(null);
    const quickCreateSectionRef = useRef<HTMLElement | null>(null);
    const currentWorkSectionRef = useRef<HTMLElement | null>(null);
    const recentWorkSectionRef = useRef<HTMLElement | null>(null);

    useEffect(() => { void listGrammars().then(setGrammars).catch(() => setGrammars([])); }, []);
    useEffect(() => {
        let active = true;
        const loadProjects = async (): Promise<void> => {
            try {
                const [session, recent, creationCatalog] = await Promise.all([getProjectSession(), listRecentProjects(), getProjectCreationCatalog()]);
                if (!active) return;
                setProjectSession(session);
                setRecentProjects(recent);
                setCatalog(creationCatalog);
            } catch {
                if (!active) return;
                setProjectSession(null);
                setRecentProjects([]);
                setCatalog(null);
            }
        };
        void loadProjects();
        const reload = () => { void loadProjects(); };
        window.addEventListener('hydra:project-session-changed', reload);
        return () => { active = false; window.removeEventListener('hydra:project-session-changed', reload); };
    }, []);

    const selectedContext = useMemo(() => {
        if (contextKey.startsWith('GRAMMAR:')) return { type: 'GRAMMAR' as const, id: contextKey.slice('GRAMMAR:'.length) };
        if (contextKey.startsWith('METAMODEL:')) return { type: 'METAMODEL' as const, id: contextKey.slice('METAMODEL:'.length) };
        return null;
    }, [contextKey]);

    const contextOptions = useMemo(() => [
        ...metamodels.map(item => ({ value: `METAMODEL:${item.id}`, label: `${metamodelName(item)} · ${item.id} · ${t('Metamodel')}`, description: `${t('Name')}: ${metamodelName(item)} · ${t('ID')}: ${item.id} · ${t('Type')}: ${t('Metamodel')}`, category: t('Metamodels') })),
        ...grammars.map(item => ({ value: `GRAMMAR:${item.id}`, label: `${item.name} · ${item.id} · ${t('Grammar')}`, description: `${t('Name')}: ${item.name} · ${t('ID')}: ${item.id} · ${t('Type')}: ${t('Grammar')} · ${item.technology}`, category: t('Grammars') }))
    ], [metamodels, grammars, t]);

    const enabledCapabilities = useMemo(() => new Set(projectSession?.project?.enabledCapabilities ?? []), [projectSession]);
    const visibleAreas = useMemo(() => PROJECT_AREAS.filter(area => area.capabilities.some(capability => enabledCapabilities.has(capability))), [enabledCapabilities]);
    const activeProject = projectSession?.active === true ? projectSession.project : null;
    const primaryModule = activeProject?.primaryArtifact?.relativePath ? moduleForPrimaryArtifact(activeProject.primaryArtifact.kind, activeProject.primaryArtifact.relativePath) : null;

    useEffect(() => {
        const openHomeSection = (event: Event): void => {
            const detail = (event as CustomEvent<{ section?: HomeSectionId }>).detail;
            const section = detail?.section;
            if (section === undefined) return;
            const target = section === 'start' ? startSectionRef.current
                : section === 'project-areas' ? projectAreasSectionRef.current ?? quickCreateSectionRef.current
                : section === 'quick-create' ? quickCreateSectionRef.current ?? projectAreasSectionRef.current
                : section === 'current-work' ? currentWorkSectionRef.current
                : recentWorkSectionRef.current;
            target?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        };
        window.addEventListener('hydra:home-section-open', openHomeSection);
        return () => window.removeEventListener('hydra:home-section-open', openHomeSection);
    }, []);

    const navigate = (nextModule: HydraModuleId) => {
        if (nextModule !== 'HOME') {
            void recordAuditActivity({
                activityType: 'navigation.module.changed', moduleId: 'navigation', action: 'NAVIGATE',
                resourceType: 'module', resourceId: nextModule, displayName: nextModule,
                before: { moduleId: 'HOME' }, after: { moduleId: nextModule },
                metadata: { fromModule: 'HOME', toModule: nextModule, source: 'home' }, dedupeKey: `HOME->${nextModule}`
            });
        }
        onNavigate(nextModule);
    };

    const changeMetamodel = (nextMetamodelId: string) => {
        if (nextMetamodelId !== selectedMetamodelId && nextMetamodelId !== '') {
            void recordAuditActivity({
                activityType: 'navigation.metamodel.changed', moduleId: 'navigation', action: 'NAVIGATE',
                resourceType: 'metamodel', resourceId: nextMetamodelId, displayName: nextMetamodelId,
                before: { metamodelId: selectedMetamodelId || null }, after: { metamodelId: nextMetamodelId },
                metadata: { activeModule: 'HOME', source: 'home-metamodel-selector' }, dedupeKey: `${selectedMetamodelId}->${nextMetamodelId}`
            });
        }
        onMetamodelChange(nextMetamodelId);
    };

    const changeContext = (nextContextKey: string) => {
        if (nextContextKey.startsWith('METAMODEL:')) changeMetamodel(nextContextKey.slice('METAMODEL:'.length));
        if (nextContextKey.startsWith('GRAMMAR:')) onMetamodelChange(nextContextKey);
    };

    const openProjectManager = (detail: { mode?: 'CREATE' | 'ACCESS'; preset?: ProjectPreset; accessSource?: 'LOCAL' | 'ZIP' | 'DIRECTORY' | 'REPOSITORY' | 'EXTERNAL'; entryPoint?: ProjectManagerEntryPoint } = {}) => {
        window.dispatchEvent(new CustomEvent('hydra:project-manager-open', { detail }));
    };

    const openRecentProject = (directory: string) => {
        window.dispatchEvent(new CustomEvent('hydra:project-open', { detail: { directory } }));
    };

    const quickPresets = catalog?.presets.filter(item => QUICK_PRESETS.includes(item.id)) ?? [];

    return <div className="hydra-home hydra-home-project-dashboard">
        <div className="hydra-home-content">
            <section className="hydra-home-dashboard" aria-labelledby="hydra-home-title">
                <header ref={startSectionRef} className="hydra-home-dashboard-header hydra-home-section-anchor">
                    <div>
                        <p className="hydra-home-kicker">{t('Your workspace')}</p>
                        <h1 id="hydra-home-title">{activeProject === null ? t('Start or continue a HYDRA project') : activeProject.name}</h1>
                        <p>{activeProject === null
                            ? t('Create a project for what you want to build, open an existing one or continue recent work.')
                            : `${t(projectPresetLabel(activeProject.createdFromPreset))} · ${t(activeProject.experienceMode === 'GUIDED' ? 'Guided mode' : 'Free mode')} · ${activeProject.enabledCapabilities.length} ${t('active areas')}`}</p>
                    </div>
                    <div className="hydra-home-header-actions">
                        <HydraTooltip label={t('Information about the home dashboard')} title={t('Home dashboard')}><p>{t('The home dashboard gives you a project-first entry point. Start from the top actions, use the quick-start cards when you want a prepared starting point and return to the current-work panel whenever you need to continue a language or model already in progress.')}</p></HydraTooltip>
                        {activeProject !== null && <HydraButton type="button" onClick={() => openProjectManager({ mode: 'ACCESS', accessSource: 'LOCAL', entryPoint: 'HOME_OPEN_PROJECT' })}>{t('Change project')}</HydraButton>}
                    </div>
                </header>

                <div className="hydra-home-primary-actions" aria-label={t('Project actions')}>
                    <HomeActionCard title={t('New project')} description={t('Choose what you want to create and HYDRA will prepare only the parts you need.')} help={t('Use this option when you are starting new work. HYDRA lets you choose a purpose, proposes the appropriate project areas and then creates a clean local project that can grow later.')} action={t('Create project')} onClick={() => openProjectManager({ mode: 'CREATE', entryPoint: 'HOME_NEW_PROJECT' })} />
                    <HomeActionCard title={t('Open project')} description={t('Continue working with a HYDRA project already saved on this computer.')} help={t('Use this option for a HYDRA project that already exists locally. The dialog focuses only on locating the project and deciding whether its files should be loaded into the current workspace.')} action={t('Open project')} onClick={() => openProjectManager({ mode: 'ACCESS', accessSource: 'LOCAL', entryPoint: 'HOME_OPEN_PROJECT' })} />
                    <HomeActionCard title={t('Bring an existing project')} description={t('Import a HYDRA project, a project folder or a project created with another modeling technology.')} help={t('Use this option when the work must be copied or recovered into HYDRA. You can import a portable HYDRA archive, a complete project folder or a project created with another supported modeling technology.')} action={t('Import')} onClick={() => openProjectManager({ mode: 'ACCESS', accessSource: 'ZIP', entryPoint: 'HOME_IMPORT_PROJECT' })} />
                    <HomeActionCard title={t('Connect an external location')} description={t('Open or import work stored in a configured repository or cloud space.')} help={t('Use this option when the project is stored outside this computer. HYDRA will focus the dialog on an already configured repository or cloud profile and the remote project path.')} action={t('Connect')} onClick={() => openProjectManager({ mode: 'ACCESS', accessSource: 'REPOSITORY', entryPoint: 'HOME_CONNECT_PROJECT' })} />
                </div>

                {activeProject !== null && <>
                    <section className="hydra-home-project-summary" aria-labelledby="hydra-home-continue-title">
                        <article className="hydra-home-dashboard-card hydra-home-continue-card">
                            <div>
                                <div className="hydra-home-card-heading"><p className="hydra-home-card-eyebrow">{t('Continue working')}</p><HydraTooltip label={t('Information about continuing the current project')} title={t('Continue working')}><p>{t('This card uses the main component of the project as a shortcut. If no main component has been chosen yet, HYDRA opens the most relevant enabled project area instead.')}</p></HydraTooltip></div>
                                <h2 id="hydra-home-continue-title">{activeProject.primaryArtifact?.relativePath ? primaryArtifactLabel(activeProject.primaryArtifact.relativePath) : t('Choose where to continue')}</h2>
                                <p>{activeProject.primaryArtifact?.relativePath
                                    ? `${t('Main component')}: ${activeProject.primaryArtifact.relativePath}`
                                    : t('You have not selected a main component yet. Open one of the project areas to continue.')}</p>
                            </div>
                            <HydraButton variant="primary" size="large" type="button" onClick={() => navigate(primaryModule ?? preferredModuleForCapabilities(enabledCapabilities))}>{t('Continue')}</HydraButton>
                        </article>
                        <article className="hydra-home-dashboard-card hydra-home-project-card">
                            <div className="hydra-home-card-heading"><p className="hydra-home-card-eyebrow">{t('Project overview')}</p><HydraTooltip label={t('Information about the project overview')} title={t('Project overview')}><p>{t('This summary shows how the project was initially created, which working mode is active, how many areas are enabled and which component is used as the main entry point.')}</p></HydraTooltip></div>
                            <h2>{t(projectPresetLabel(activeProject.createdFromPreset))}</h2>
                            <p>{activeProject.description || t('This project can grow as you add new areas of work.')}</p>
                            <dl>
                                <div><dt><span>{t('Working mode')}</span><HydraTooltip label={t('Information about the working mode')} title={t('Working mode')}><p>{t('Guided mode reduces visible complexity and proposes the next step. Free mode exposes the full workbench without changing the stored project model.')}</p></HydraTooltip></dt><dd>{t(activeProject.experienceMode === 'GUIDED' ? 'Guided' : 'Free')}</dd></div>
                                <div><dt><span>{t('Active areas')}</span><HydraTooltip label={t('Information about active project areas')} title={t('Active areas')}><p>{t('Active areas are the parts of HYDRA currently enabled for this project. They determine which editors and workflows are shown, but disabling an area never deletes existing project files.')}</p></HydraTooltip></dt><dd>{activeProject.enabledCapabilities.length}</dd></div>
                                <div><dt><span>{t('Main component')}</span><HydraTooltip label={t('Information about the main component')} title={t('Main component')}><p>{t('The main component is the preferred entry point for continuing the project, for example its principal grammar, metamodel or model.')}</p></HydraTooltip></dt><dd>{activeProject.primaryArtifact?.relativePath ? activeProject.primaryArtifact.relativePath : t('Not selected')}</dd></div>
                            </dl>
                            <HydraButton type="button" onClick={() => window.dispatchEvent(new CustomEvent('hydra:project-capabilities-open'))}>{t('Configure project')}</HydraButton>
                        </article>
                    </section>

                    <section ref={projectAreasSectionRef} className="hydra-home-project-areas hydra-home-section-anchor" aria-labelledby="hydra-home-areas-title">
                        <HomeSectionHeading eyebrow={t('Your project')} titleId="hydra-home-areas-title" title={t('What you can work on')} support={t('Only the areas enabled for this project are shown.')} tooltipLabel={t('Information about enabled project areas')} tooltipTitle={t('What you can work on')} tooltipBody={t('Each card represents one functional area already enabled in the open project. HYDRA chooses the most suitable editor or module for that area so that you can continue directly from the home dashboard.')} />
                        <div className="hydra-home-card-grid">
                            {visibleAreas.map(area => <HomeAreaCard key={area.id} title={t(area.title)} description={t(area.description)} help={t(projectAreaHelp(area.id))} onClick={() => navigate(bestModuleForArea(area, enabledCapabilities))} />)}
                        </div>
                    </section>
                </>}

                {activeProject === null && <section ref={quickCreateSectionRef} className="hydra-home-create-fast hydra-home-section-anchor" aria-labelledby="hydra-home-create-fast-title">
                    <HomeSectionHeading eyebrow={t('Create quickly')} titleId="hydra-home-create-fast-title" title={t('What do you want to build?')} support={t('These choices only prepare the starting point. You can add more areas later.')} tooltipLabel={t('Information about quick starting points')} tooltipTitle={t('What do you want to build?')} tooltipBody={t('Quick starting points create a new project already oriented to a typical goal. They reduce the initial setup while keeping the project flexible, because you can always add or remove areas later.')} />
                    <div className="hydra-home-card-grid">
                        {quickPresets.map(item => <HomeAreaCard key={item.id} title={t(projectPresetLabel(item.id))} description={t(projectPresetDescription(item.id))} help={t(projectPresetGuidance(item.id).summary)} onClick={() => openProjectManager({ mode: 'CREATE', preset: item.id, entryPoint: 'HOME_QUICK_PRESET' })} />)}
                    </div>
                </section>}

                <section ref={currentWorkSectionRef} className="hydra-home-context-dashboard hydra-home-section-anchor" aria-labelledby="hydra-home-context-title">
                    <HomeSectionHeading eyebrow={t('Current work')} titleId="hydra-home-context-title" title={t('Model or language in focus')} support={t('Choose a model definition or grammar to review its current state and continue editing it.')} tooltipLabel={t('Information about the selected model or language')} tooltipTitle={t('Model or language in focus')} tooltipBody={t('This area helps you resume work from a concrete metamodel or grammar. The readiness panel shows what is already prepared and which areas still need attention before the language or editor is complete.')} />
                    <div className="hydra-home-context-card">
                        <div className="hydra-home-context-selector-row"><HydraSelect ariaLabel={t('Home semantic context')} value={contextKey} placeholder={t('Select a metamodel or grammar')} options={contextOptions} onChange={changeContext} /><HydraTooltip label={t('Information about the current model or language selector')} title={t('Model or language in focus')}><p>{t('Use this selector to choose the metamodel or grammar whose current preparation state you want to review. It does not change the project itself; it changes only the context shown in this dashboard block.')}</p></HydraTooltip></div>
                        {selectedContext === null
                            ? <div className="hydra-empty-state"><h3>{t('Nothing selected yet')}</h3><p>{t('Choose a metamodel or grammar to see what is ready and what still needs attention.')}</p></div>
                            : <Suspense fallback={<div className="hydra-home-readiness-loading" role="status">{t('Loading project status…')}</div>}>
                                <LazyCapabilityReadinessView compact contextId={selectedContext.id} contextType={selectedContext.type} onNavigate={navigate} />
                            </Suspense>}
                    </div>
                </section>

                <section ref={recentWorkSectionRef} className="hydra-home-recent hydra-home-section-anchor" aria-labelledby="hydra-home-recent-title">
                    <HomeSectionHeading eyebrow={t('Recent work')} titleId="hydra-home-recent-title" title={t('Recent projects')} support={t('Open a recent project directly or go to the full project manager.')} tooltipLabel={t('Information about recent projects')} tooltipTitle={t('Recent projects')} tooltipBody={t('Recent projects show the work you opened most recently in HYDRA. Use this list to reopen a project quickly, or use the full project manager if you need another location or a different source.') } action={<HydraButton type="button" onClick={() => openProjectManager({ mode: 'ACCESS', accessSource: 'LOCAL', entryPoint: 'HOME_OPEN_PROJECT' })}>{t('See all projects')}</HydraButton>} />
                    {recentProjects.length === 0
                        ? <div className="hydra-empty-state"><p>{t('No recent projects yet. Create or open one to see it here.')}</p></div>
                        : <div className="hydra-home-recent-grid">{recentProjects.slice(0, 6).map(project => <HomeRecentCard key={`${project.id}:${project.directory}`} name={project.name} location={friendlyLocation(project.directory)} active={project.active} available={project.available} onClick={() => openRecentProject(project.directory)} />)}</div>}
                </section>
            </section>
        </div>
    </div>;
}

function HomeSectionHeading({ eyebrow, titleId, title, support, tooltipLabel, tooltipTitle, tooltipBody, action }: { eyebrow: string; titleId: string; title: string; support: string; tooltipLabel: string; tooltipTitle: string; tooltipBody: string; action?: ReactNode }) {
    return <div className="hydra-home-section-heading"><div><p>{eyebrow}</p><div className="hydra-home-section-title-row"><h2 id={titleId}>{title}</h2><HydraTooltip label={tooltipLabel} title={tooltipTitle}><p>{tooltipBody}</p></HydraTooltip></div></div>{action ?? <span>{support}</span>}</div>;
}

function HomeActionCard({ title, description, help, action, onClick }: { title: string; description: string; help: string; action: string; onClick: () => void }) {
    return <article className="hydra-home-dashboard-card hydra-home-action-card"><header className="hydra-home-card-heading"><h2>{title}</h2><HydraTooltip label={"More information about this card"} title={title}><p>{help}</p></HydraTooltip></header><p>{description}</p><footer><HydraButton type="button" onClick={onClick}>{action}</HydraButton></footer></article>;
}

function HomeAreaCard({ title, description, help, onClick }: { title: string; description: string; help: string; onClick: () => void }) {
    return <article className="hydra-home-dashboard-card hydra-home-area-card-shell"><div className="hydra-home-card-help"><HydraTooltip label={"More information about this card"} title={title}><p>{help}</p></HydraTooltip></div><HydraButton className="hydra-home-area-card" type="button" onClick={onClick}><span><strong>{title}</strong><small>{description}</small></span><span aria-hidden="true">→</span></HydraButton></article>;
}

function HomeRecentCard({ name, location, active, available, onClick }: { name: string; location: string; active: boolean; available: boolean; onClick: () => void }) {
    const { t } = useHydraLanguage();
    return <article className="hydra-home-dashboard-card hydra-home-recent-card-shell"><div className="hydra-home-card-help"><HydraTooltip label={t('More information about this recent project')} title={name}><p>{active ? t('This project is currently open. The card keeps its location visible so you can identify where it is stored.') : t('Open this recent project directly from its saved location. If the location is no longer available, the card stays disabled instead of attempting a broken open operation.')}</p></HydraTooltip></div><HydraButton className="hydra-home-recent-card" type="button" disabled={!available} onClick={onClick}><strong>{name}</strong><span>{active ? t('Currently open') : t('Open project')}</span><small>{location}</small></HydraButton></article>;
}

function projectAreaHelp(areaId: string): string {
    const help: Record<string, string> = {
        language: 'Use this area to define the concepts of the language, its grammar and the editing experience that users will work with.',
        models: 'Use this area to create and edit concrete models that conform to the language or metamodel defined in the project.',
        quality: 'Use this area to define checks that detect modeling problems and corrections that help users resolve them.',
        behavior: 'Use this area to define events, flows and reactions that make models respond to changes or user actions.',
        transform: 'Use this area to convert models, generate text or code and prepare generated editors or other derived outputs.',
        share: 'Use this area to connect the project with external storage and repositories and prepare it for sharing or delivery.'
    };
    return help[areaId] ?? 'Open this project area to continue the corresponding HYDRA workflow.';
}

function bestModuleForArea(area: HomeArea, capabilities: ReadonlySet<string>): HydraModuleId {
    if (area.id === 'language') {
        if (capabilities.has('GRAMMAR_DESIGN') && !capabilities.has('METAMODEL_DESIGN')) return 'GRAMMARS';
        if (capabilities.has('EDITOR_DEFINITIONS') && !capabilities.has('METAMODEL_DESIGN') && !capabilities.has('GRAMMAR_DESIGN')) return 'EDITORS';
    }
    if (area.id === 'models' && capabilities.has('TEXTUAL_MODELING') && !capabilities.has('MODEL_EDITING')) return 'TEXTUAL_MODELS';
    if (area.id === 'quality' && capabilities.has('QUICK_FIXES') && !capabilities.has('VALIDATIONS')) return 'QUICK_FIXES';
    if (area.id === 'behavior' && capabilities.has('EVENT_FLOWS') && !capabilities.has('EVENTS')) return 'EVENT_FLOWS';
    return area.module;
}

function preferredModuleForCapabilities(capabilities: ReadonlySet<string>): HydraModuleId {
    const area = PROJECT_AREAS.find(candidate => candidate.capabilities.some(capability => capabilities.has(capability)));
    return area === undefined ? 'METAMODEL' : bestModuleForArea(area, capabilities);
}

function moduleForPrimaryArtifact(kind: string, path: string): HydraModuleId {
    const normalized = `${kind}:${path}`.toUpperCase();
    if (normalized.includes('GRAMMAR')) return 'GRAMMARS';
    if (normalized.includes('VALIDATION')) return 'VALIDATIONS';
    if (normalized.includes('QUICK')) return 'QUICK_FIXES';
    if (normalized.includes('EVENT_FLOW')) return 'EVENT_FLOWS';
    if (normalized.includes('EVENT')) return 'EVENTS';
    if (normalized.includes('GAMIFICATION')) return 'GAMIFICATION';
    if (normalized.includes('TRANSFORMATION') || normalized.includes('/TRANSFORMATIONS/')) return 'TRANSFORMATIONS';
    if (normalized.includes('EDITOR')) return 'EDITORS';
    if (normalized.includes('MODEL') && !normalized.includes('METAMODEL')) return 'MODEL';
    return 'METAMODEL';
}

function primaryArtifactLabel(path: string): string {
    const normalized = path.replaceAll('\\', '/');
    return normalized.split('/').filter(Boolean).at(-1) ?? path;
}

function friendlyLocation(directory: string): string {
    if (directory.trim() === '') return '';
    const normalized = directory.replaceAll('\\', '/').replace(/\/$/, '');
    const segments = normalized.split('/').filter(Boolean);
    if (segments.length <= 2) return normalized;
    return `…/${segments.slice(-2).join('/')}`;
}

function metamodelName(item: MetamodelSummary): string {
    const fileName = item.fileName.trim();
    if (fileName === '') return item.id;
    const base = fileName.replace(/\.[^.]+$/, '').trim();
    return base === '' ? item.id : base;
}
