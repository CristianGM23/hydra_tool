import { useEffect, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton } from '../design-system';
import type { HydraModuleId } from '../application-shell/types';
import { useHydraLanguage } from '../i18n';
import { loadActiveGrammarCompilation, loadGrammar } from '../grammar-designer/api';
import type { GrammarCompilationSnapshot, GrammarDefinitionModel } from '../grammar-designer/types';
import { loadCapabilityReadiness } from './api';
import { CapabilityTransactionPanel } from './CapabilityTransactionPanel';
import type { HydraCapabilityReadiness } from './types';
import './capability-center.css';

interface Props {
    contextId?: string;
    contextType?: 'METAMODEL'|'GRAMMAR';
    metamodelId?: string;
    compact?: boolean;
    onNavigate: (moduleId: HydraModuleId) => void;
}

const METRIC_LABELS: Readonly<Record<string, string>> = {
    relations: 'Relations', classes: 'Classes', rulesets: 'Rule sets', ruleSets: 'Rule sets', rules: 'Rules',
    eventTypes: 'Event types', bindings: 'Bindings', flows: 'Flows', nodes: 'Nodes',
    achievements: 'Achievements', levels: 'Levels', forms: 'Forms', representations: 'Representations',
    tools: 'Tools', profiles: 'Profiles', features: 'Features', mappings: 'Mappings'
};

export function CapabilityReadinessView(props: Props) {
    const contextId = (props.contextId ?? props.metamodelId ?? '').trim();
    const contextType = props.contextType ?? 'METAMODEL';
    if (contextId === '') return <CapabilityReadinessEmpty />;
    return <CapabilityReadinessSession key={`${contextType}:${contextId}`} {...props} contextId={contextId} contextType={contextType} />;
}

function CapabilityReadinessEmpty() {
    const { t } = useHydraLanguage();
    return <section className="hydra-empty-state"><h2>{t('Select a semantic context')}</h2><p>{t('Choose a metamodel or grammar to inspect its structure and related capabilities.')}</p></section>;
}

function CapabilityReadinessSession({ contextId = '', contextType = 'METAMODEL', compact = false, onNavigate }: Props) {
    const { t } = useHydraLanguage();
    const [readiness, setReadiness] = useState<HydraCapabilityReadiness | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        let active = true;
        void loadCapabilityReadiness(contextType, contextId).then(result => {
            if (active) setReadiness(result);
        }).catch(exception => {
            if (!active) return;
            setError(exception instanceof Error ? exception.message : t('Could not load project status'));
        }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [contextId, contextType, t]);

    if (error !== '') {
        if (contextType === 'GRAMMAR') return <GrammarCapabilityReadinessFallback compact={compact} contextId={contextId} onNavigate={onNavigate} />;
        return <section className="hydra-empty-state error"><h2>{t('Project status unavailable')}</h2><HydraActionableErrorNotice message={error} context="project status" /></section>;
    }
    if (loading || readiness === null || readiness.contextId !== contextId) return <section className="hydra-empty-state"><h2>{t('Loading project status…')}</h2></section>;

    return <section className={`capability-readiness${compact ? ' compact' : ''}`}>
        <header>
            <div className="capability-score" aria-label={`${readiness.score} ${t('percent ready')}`}>
                <div className="capability-score-circle"><strong>{readiness.score}%</strong></div>
                <span className="capability-score-caption">{readiness.configuredLayers}/{readiness.totalLayers} {t('areas ready')}</span>
            </div>
            <div><p>{t('Project status')}</p><h2>{readiness.contextName}</h2><small>{t(readiness.contextType === 'GRAMMAR' ? 'Grammar' : 'Metamodel')} · {readiness.contextId}</small></div>
        </header>
        <div className="capability-layer-grid">
            {readiness.layers.map(layer => <article className={layer.configured ? 'configured' : 'empty'} key={layer.id}>
                <div><span className="capability-state">{t(layer.configured ? 'Configured' : 'Not configured')}</span><h3>{t(layer.displayName)}</h3></div>
                <dl>{Object.entries(layer.metrics).map(([name, value]) => <div key={name}><dt>{t(METRIC_LABELS[name] ?? name)}</dt><dd>{value}</dd></div>)}</dl>
                <HydraButton size="small" variant={layer.configured ? 'secondary' : 'primary'} onClick={() => onNavigate(layer.recommendedModule)}>{t(layer.configured ? 'Open area' : 'Configure area')}</HydraButton>
            </article>)}
        </div>
        {(readiness.warnings?.length ?? 0) > 0 && <aside className="capability-readiness-warnings" role="status"><h3>{t('Some project areas need attention')}</h3><ul>{readiness.warnings?.map(warning => <li key={warning}>{t(warning)}</li>)}</ul></aside>}
        {!compact && <><aside className="capability-next-steps"><h3>{t('Recommended next steps')}</h3><ol>{readiness.recommendedNextSteps.map(step => <li key={step}>{t(step)}</li>)}</ol></aside>{contextType === 'METAMODEL' && <CapabilityTransactionPanel metamodelId={contextId} />}</>}
    </section>;
}

function GrammarCapabilityReadinessFallback({ contextId, compact = false, onNavigate }: { contextId: string; compact?: boolean; onNavigate: (moduleId: HydraModuleId) => void; }) {
    const { t } = useHydraLanguage();
    const [grammar, setGrammar] = useState<GrammarDefinitionModel | null>(null);
    const [snapshot, setSnapshot] = useState<GrammarCompilationSnapshot | null>(null);
    const [loading, setLoading] = useState(true);
    const [summaryUnavailable, setSummaryUnavailable] = useState(false);

    useEffect(() => {
        let active = true;
        void Promise.all([loadGrammar(contextId), loadActiveGrammarCompilation(contextId)])
            .then(([loaded, activeSnapshot]) => { if (active) { setGrammar(loaded.grammar); setSnapshot(activeSnapshot); } })
            .catch(() => { if (active) setSummaryUnavailable(true); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [contextId]);

    const summary = useMemo(() => grammar === null ? null : grammarCapabilitySummary(grammar, snapshot), [grammar, snapshot]);
    return <section className={`capability-readiness capability-readiness-fallback${compact ? ' compact' : ''}`}>
        <header>
            <div className="capability-score" aria-label={t('Grammar context')}>
                <div className="capability-score-circle"><strong>✓</strong></div>
                <span className="capability-score-caption">{t('Available')}</span>
            </div>
            <div>
                <p>{t('Current language or model definition')}</p>
                <h2>{grammar?.name || contextId}</h2>
                <small>{t('Grammar')} · {contextId}{grammar ? ` · ${grammar.technology}` : ''}</small>
            </div>
        </header>
        {loading ? <div className="capability-readiness-fallback-copy" role="status"><strong>{t('Loading grammar summary…')}</strong></div>
            : summary !== null ? <>
                <div className="grammar-capability-summary-grid">
                    <GrammarSummaryCard title={t('Grammar definition')} configured metrics={[
                        [t('Technology'), summary.technology],
                        [t('Documents'), summary.documents],
                        [t('Entry rule'), summary.entryRule]
                    ]}/>
                    <GrammarSummaryCard title={t('Semantic model')} configured={summary.semanticConfigured} metrics={[
                        [t('Bindings'), summary.bindings],
                        [t('Inferred classifiers'), summary.classifiers],
                        [t('Linked metamodel'), summary.linkedMetamodel ? t('Yes') : t('No')]
                    ]}/>
                    <GrammarSummaryCard title={t('Textual editor')} configured={summary.textualConfigured} metrics={[
                        [t('Styles'), summary.textualStyles],
                        [t('Contextual help'), summary.contextualHelp],
                        [t('Reserved words'), summary.keywords]
                    ]}/>
                    <GrammarSummaryCard title={t('Generated editor and execution')} configured={summary.compiled} metrics={[
                        [t('Status'), summary.compiled ? t('Compiled') : t('No generated version yet')],
                        [t('Generated files'), summary.generatedFiles],
                        [t('Inferred classes'), summary.classifiers]
                    ]}/>
                </div>
                <div className="capability-readiness-fallback-copy">
                    <strong>{t('The grammar was selected correctly.')}</strong>
                    <p>{t('This summary is calculated from the saved grammar and its latest generated version. Missing project status information does not block work.')}</p>
                </div>
            </> : <div className="capability-readiness-fallback-copy">
                <strong>{t('The grammar is selected.')}</strong>
                <p>{summaryUnavailable ? t('The detailed summary is not available yet, but you can open the designer and continue working with the grammar.') : ''}</p>
            </div>}
        <div className="capability-readiness-fallback-actions">
            <HydraButton size="small" variant="primary" type="button" onClick={() => onNavigate('GRAMMARS')}>{t('Open grammar designer')}</HydraButton>
            {summary?.compiled && <HydraButton size="small" type="button" onClick={() => onNavigate('TEXTUAL_MODELS')}>{t('Open textual editor')}</HydraButton>}
        </div>
    </section>;
}

function GrammarSummaryCard({ title, configured, metrics }: { title: string; configured: boolean; metrics: ReadonlyArray<readonly [string, string | number]> }) {
    const { t } = useHydraLanguage();
    return <article className={`grammar-capability-summary-card ${configured ? 'configured' : 'empty'}`}>
        <header><span>{t(configured ? 'CONFIGURED' : 'PENDING')}</span><h3>{title}</h3></header>
        <dl>{metrics.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
    </article>;
}

function grammarCapabilitySummary(grammar: GrammarDefinitionModel, snapshot: GrammarCompilationSnapshot | null) {
    const settings = grammar.settings ?? {};
    const textualStyles = Object.keys(settings).filter(key => key.startsWith('hydra.textual.role.') || key.startsWith('hydra.textual.symbol.')).length;
    const contextualHelp = Object.entries(settings).filter(([key, value]) => key.startsWith('hydra.textual.hover.') && key.endsWith('.description') && value.trim() !== '').length;
    const keywords = (settings['hydra.textual.keywords'] ?? '').split(',').map(value => value.trim()).filter(Boolean).length;
    const bindings = grammar.semanticBindings?.bindings?.length ?? 0;
    const classifiers = snapshot?.classDiagram?.nodes?.length ?? 0;
    const generatedFiles = Object.keys(snapshot?.generatedProject?.files ?? {}).length;
    return {
        technology: grammar.technology,
        documents: grammar.documents.length,
        entryRule: grammar.entryRule || '—',
        bindings,
        classifiers,
        linkedMetamodel: grammar.metamodelId.trim() !== '',
        semanticConfigured: bindings > 0 || classifiers > 0 || grammar.metamodelId.trim() !== '',
        textualStyles,
        contextualHelp,
        keywords,
        textualConfigured: textualStyles > 0 || contextualHelp > 0 || keywords > 0,
        compiled: snapshot?.status === 'SUCCESS',
        generatedFiles
    };
}
