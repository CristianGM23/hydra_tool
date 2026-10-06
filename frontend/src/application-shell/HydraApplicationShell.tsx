import { useEffect, useRef, useState, type ReactNode } from 'react';
import type { MetamodelSummary } from '../types/editor';
import { HydraButton, HydraNavBar, HydraSelect } from '../design-system';
import { SettingsIcon } from '../components/AppIcons';
import { HydraLanguageToggle, useHydraLanguage } from '../i18n';
import { ActivityInteractionTracker, ActivityLogDialog, recordAuditActivity } from '../audit-log';
import { listGrammars } from '../grammar-designer/api';
import type { GrammarSummary } from '../grammar-designer/types';
import { HydraModuleRail } from './HydraModuleRail';
import { moduleDefinition, moduleSupportsGrammarContext } from './moduleRegistry';
import type { HydraModuleId } from './types';

interface Props {
    activeModule: HydraModuleId;
    metamodels: MetamodelSummary[];
    selectedMetamodelId: string;
    toolbar?: ReactNode;
    status?: ReactNode;
    children: ReactNode;
    onMetamodelChange: (metamodelId: string) => void;
    onNavigate: (moduleId: HydraModuleId) => void;
    onOpenSettings: () => void;
}

export function HydraApplicationShell({ activeModule, metamodels, selectedMetamodelId, toolbar, status,
        children, onMetamodelChange, onNavigate, onOpenSettings }: Props) {
    const module = moduleDefinition(activeModule);
    const showRail = true;
    const { t, language } = useHydraLanguage();
    const [activityLogOpen, setActivityLogOpen] = useState(false);
    const [grammars,setGrammars]=useState<GrammarSummary[]>([]);
    const previousModule = useRef(activeModule);
    const previousMetamodel = useRef(selectedMetamodelId);
    const previousLanguage = useRef(language);
    const grammarContextEnabled=moduleSupportsGrammarContext(activeModule);

    useEffect(()=>{
        if(!module.requiresMetamodel||!grammarContextEnabled)return;
        let active=true;
        void listGrammars().then(items=>{if(active)setGrammars(items);}).catch(()=>{if(active)setGrammars([]);});
        return()=>{active=false;};
    },[activeModule,grammarContextEnabled,module.requiresMetamodel]);

    const visibleGrammars=module.requiresMetamodel&&grammarContextEnabled?grammars:[];
    const semanticContextOptions=[
        ...metamodels.map(item=>({value:item.id,label:item.id,description:`${item.rootPackageCount} root package(s)`,category:t('Metamodels')})),
        ...visibleGrammars.map(item=>({value:`GRAMMAR:${item.id}`,label:`${item.name} · ${item.id}`,description:`${t('Grammar')} · ${item.technology}`,category:t('Grammars')}))
    ];

    useEffect(() => {
        const before = previousModule.current;
        if (before !== activeModule) {
            void recordAuditActivity({
                activityType: 'navigation.module.changed', moduleId: 'navigation', action: 'NAVIGATE',
                resourceType: 'module', resourceId: activeModule, displayName: activeModule,
                before: { moduleId: before }, after: { moduleId: activeModule },
                metadata: { fromModule: before, toModule: activeModule }, dedupeKey: `${before}->${activeModule}`
            });
            previousModule.current = activeModule;
        }
    }, [activeModule]);

    useEffect(() => {
        const before = previousMetamodel.current;
        if (before !== selectedMetamodelId && selectedMetamodelId !== '') {
            const grammar=selectedMetamodelId.startsWith('GRAMMAR:');
            void recordAuditActivity({
                activityType: grammar?'navigation.grammar.changed':'navigation.metamodel.changed', moduleId: 'navigation', action: 'NAVIGATE',
                resourceType: grammar?'grammar':'metamodel', resourceId: selectedMetamodelId, displayName: selectedMetamodelId,
                before: { semanticContextId: before || null }, after: { semanticContextId: selectedMetamodelId },
                metadata: { activeModule }, dedupeKey: `${before}->${selectedMetamodelId}`
            });
            previousMetamodel.current = selectedMetamodelId;
        } else if (before !== selectedMetamodelId) previousMetamodel.current = selectedMetamodelId;
    }, [activeModule, selectedMetamodelId]);


    useEffect(() => {
        const before = previousLanguage.current;
        if (before !== language) {
            void recordAuditActivity({
                activityType: 'settings.language.updated', moduleId: 'settings', action: 'CONFIGURE',
                resourceType: 'settings', resourceId: 'language', displayName: 'Application language',
                before: { language: before }, after: { language }, dedupeKey: `${before}->${language}`
            });
            previousLanguage.current = language;
        }
    }, [language]);
    const navigate = (nextModule: HydraModuleId) => {
        if (nextModule !== activeModule) {
            void recordAuditActivity({
                activityType: 'navigation.module.changed', moduleId: 'navigation', action: 'NAVIGATE',
                resourceType: 'module', resourceId: nextModule, displayName: nextModule,
                before: { moduleId: activeModule }, after: { moduleId: nextModule },
                metadata: { fromModule: activeModule, toModule: nextModule, source: 'navigation-command' },
                dedupeKey: `${activeModule}->${nextModule}`
            });
        }
        onNavigate(nextModule);
    };

    const changeSemanticContext = (nextContextId: string) => {
        if (nextContextId !== selectedMetamodelId && nextContextId !== '') {
            const grammar=nextContextId.startsWith('GRAMMAR:');
            void recordAuditActivity({
                activityType: grammar?'navigation.grammar.changed':'navigation.metamodel.changed', moduleId: 'navigation', action: 'NAVIGATE',
                resourceType: grammar?'grammar':'metamodel', resourceId: nextContextId, displayName: nextContextId,
                before: { semanticContextId: selectedMetamodelId || null }, after: { semanticContextId: nextContextId },
                metadata: { activeModule, source: grammar?'semantic-context-selector':'metamodel-selector' }, dedupeKey: `${selectedMetamodelId}->${nextContextId}`
            });
        }
        onMetamodelChange(nextContextId);
    };

    const openSettings = () => {
        void recordAuditActivity({ activityType: 'ui.settings.opened', moduleId: 'settings', action: 'OPEN',
            resourceType: 'dialog', resourceId: 'application-settings', displayName: 'Application settings',
            metadata: { activeModule }, dedupeKey: activeModule });
        onOpenSettings();
    };

    return <main className="application hydra-application-shell">
        <HydraNavBar
            brand={<HydraButton className="hydra-brand-button" data-hydra-i18n-skip="true" type="button" onClick={() => navigate('HOME')}><strong>HYDRA</strong><span>HYbrid DSL &amp; Representation Architecture</span></HydraButton>}
            primary={<>
                <HydraButton variant={activeModule === 'HOME' ? 'primary' : 'quiet'} onClick={() => navigate('HOME')}>{t('Home')}</HydraButton>
                {module.requiresMetamodel && <HydraSelect ariaLabel={t(grammarContextEnabled?'Active semantic context':'Active metamodel')}
                    value={selectedMetamodelId} placeholder={t(grammarContextEnabled?'Select a metamodel or grammar':'Select a metamodel')}
                    options={semanticContextOptions}
                    onChange={changeSemanticContext} />}
            </>}
            secondary={<><HydraLanguageToggle/><HydraButton aria-label={t('Open activity log')} onClick={() => setActivityLogOpen(true)}>{t('Activity log')}</HydraButton><HydraButton aria-label={t('Open application settings')} icon={<SettingsIcon />} onClick={openSettings}>{t('Settings')}</HydraButton></>}
            status={status}
        />
        <div className="hydra-shell-frame">
            {showRail && <HydraModuleRail activeModule={activeModule} onNavigate={navigate} />}
            <section className="hydra-module-surface">
                {activeModule !== 'HOME' && <header className="hydra-module-header">
                    <div className="hydra-module-heading">
                        <p>{t(module.category)}</p>
                        <h1>{t(module.title)}</h1>
                        <span className="hydra-module-description">{t(module.description)}</span>
                    </div>
                    {toolbar !== undefined && <div className="hydra-module-toolbar">{toolbar}</div>}
                </header>}
                <div className="hydra-module-content">{children}</div>
            </section>
        </div>
        <ActivityInteractionTracker activeModule={activeModule} />
        <ActivityLogDialog open={activityLogOpen} onClose={() => setActivityLogOpen(false)} />
    </main>;
}
