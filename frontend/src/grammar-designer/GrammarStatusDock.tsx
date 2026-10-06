import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import { HydraButton } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { GrammarCompilationPanel } from './GrammarCompilationPanel';
import { GrammarProblemsPanel } from './GrammarProblemsPanel';
import type {
    GrammarClassDiagramProjection,
    GrammarCodeAction,
    GrammarCompilationSnapshot,
    GrammarDiagnostic,
    GrammarProviderDescriptor,
} from './types';

interface GrammarEntryInferenceSummary {
    rule: string;
    method: string;
    confidence: number;
    mode: 'AUTO'|'MANUAL';
    selectedRule: string;
    provisional: boolean;
}

interface Props {
    diagnostics: readonly GrammarDiagnostic[];
    actions: readonly GrammarCodeAction[];
    message: string;
    snapshot: GrammarCompilationSnapshot | null;
    provider?: GrammarProviderDescriptor;
    projection: GrammarClassDiagramProjection | null;
    entryRuleInference: GrammarEntryInferenceSummary;
    onNavigate: (item: GrammarDiagnostic) => void;
    onApply: (action: GrammarCodeAction) => void;
    activeTab: 'PROBLEMS'|'INFORMATION';
    onTabChange: (tab: 'PROBLEMS'|'INFORMATION') => void;
}

interface ResizeSession {
    startY: number;
    startHeight: number;
}

const MIN_BODY_HEIGHT = 96;
const MAX_BODY_HEIGHT = 520;

/**
 * Compact lower workbench for grammar problems and compilation/inference information.
 * Dock collapse and active-panel collapse are deliberately independent so the editor
 * can keep its status affordances visible without permanently consuming canvas space.
 */
export function GrammarStatusDock({ diagnostics, actions, message, snapshot, provider, projection, entryRuleInference, onNavigate, onApply, activeTab, onTabChange }: Props) {
    const { t } = useHydraLanguage();
    const [collapsed, setCollapsed] = useState(false);
    const [panelCollapsed, setPanelCollapsed] = useState(false);
    const [bodyHeight, setBodyHeight] = useState<number | undefined>();
    const bodyRef = useRef<HTMLDivElement | null>(null);
    const resizeSession = useRef<ResizeSession | null>(null);

    useEffect(() => () => {
        resizeSession.current = null;
    }, []);

    const problemDiagnostics = diagnostics.filter(item => item.severity !== 'INFO');
    const informationDiagnostics = diagnostics.filter(item => item.severity === 'INFO' && item.code !== 'GRAMMAR_EFFECTIVE_ENTRY_RULE');
    const errors = problemDiagnostics.filter(item => item.severity === 'ERROR').length;
    const warnings = problemDiagnostics.filter(item => item.severity === 'WARNING').length;
    const projectionNotices = projection?.warnings ?? [];
    const effectiveEntryRule = entryRuleInference.mode === 'MANUAL' && entryRuleInference.selectedRule !== '' ? entryRuleInference.selectedRule : entryRuleInference.rule;
    const infos = informationDiagnostics.length + (effectiveEntryRule !== '' ? 1 : 0) + projectionNotices.length + (message !== '' ? 1 : 0);

    function beginResize(event: ReactPointerEvent<HTMLDivElement>) {
        if (collapsed) return;
        event.preventDefault();
        const startHeight = bodyRef.current?.getBoundingClientRect().height ?? MIN_BODY_HEIGHT;
        resizeSession.current = { startY: event.clientY, startHeight };
        event.currentTarget.setPointerCapture?.(event.pointerId);
    }

    function resize(event: ReactPointerEvent<HTMLDivElement>) {
        const session = resizeSession.current;
        if (!session) return;
        const next = session.startHeight + (event.clientY - session.startY);
        setBodyHeight(Math.max(MIN_BODY_HEIGHT, Math.min(MAX_BODY_HEIGHT, Math.round(next))));
    }

    function endResize(event: ReactPointerEvent<HTMLDivElement>) {
        if (!resizeSession.current) return;
        resizeSession.current = null;
        event.currentTarget.releasePointerCapture?.(event.pointerId);
    }

    return <section className={`grammar-status-dock${collapsed ? ' collapsed' : ''}`}>
        {!collapsed && <div
            className="grammar-status-resize-handle"
            role="separator"
            aria-orientation="horizontal"
            aria-label={t('Resize lower section')}
            onPointerDown={beginResize}
            onPointerMove={resize}
            onPointerUp={endResize}
            onPointerCancel={endResize}
        />}
        <header className="grammar-status-dock-tabs">
            <div>
                <HydraButton type="button" className={activeTab === 'PROBLEMS' ? 'active' : ''} aria-pressed={activeTab === 'PROBLEMS'} onClick={() => { onTabChange('PROBLEMS'); setPanelCollapsed(false); }}>
                    {t('Problems')} <span>{errors + warnings}</span>
                </HydraButton>
                <HydraButton type="button" className={activeTab === 'INFORMATION' ? 'active' : ''} aria-pressed={activeTab === 'INFORMATION'} onClick={() => { onTabChange('INFORMATION'); setPanelCollapsed(false); }}>
                    {t('Information')} {infos > 0 && <span>{infos}</span>}
                </HydraButton>
            </div>
            <div className="grammar-status-dock-controls">
                {!collapsed && <HydraButton type="button" aria-expanded={!panelCollapsed} onClick={() => setPanelCollapsed(value => !value)}>
                    {t(panelCollapsed ? 'Expand current panel' : 'Collapse current panel')}
                </HydraButton>}
                <HydraButton type="button" aria-expanded={!collapsed} onClick={() => setCollapsed(value => !value)}>
                    {t(collapsed ? 'Expand lower section' : 'Collapse lower section')}
                </HydraButton>
            </div>
        </header>
        {!collapsed && <div ref={bodyRef} className="grammar-status-dock-body" style={bodyHeight === undefined ? undefined : { height: bodyHeight }}>
            {!panelCollapsed && (activeTab === 'PROBLEMS'
                ? <GrammarProblemsPanel diagnostics={problemDiagnostics} actions={actions} onNavigate={onNavigate} onApply={onApply}/>
                : <div className="grammar-inference-information">
                    {informationDiagnostics.map((item,index) => <article className="grammar-information-diagnostic" key={`${item.code}:${item.documentId}:${index}`}>
                        <strong>{t(item.title || 'Information')}</strong>
                        <p>{t(item.message)}</p>
                        {(item.cause || item.solution) && <details><summary>{t('Details')}</summary>{item.cause && <p>{t(item.cause)}</p>}{item.solution && <p>{t(item.solution)}</p>}</details>}
                    </article>)}
                    {message !== '' && <article><strong>{t('Information')}</strong><p>{t(message)}</p></article>}
                    {effectiveEntryRule !== '' && <article className="grammar-entry-inference-summary">
                        <strong>{t('Effective entry rule')}</strong>
                        <p>{effectiveEntryRule}{entryRuleInference.mode === 'AUTO' && entryRuleInference.confidence > 0 ? ` · ${entryRuleInference.confidence}%` : ''}</p>
                        <p>{entryRuleInference.method === '' ? '' : entryRuleInference.method.replaceAll('_',' ').toLowerCase()}{entryRuleInference.provisional ? ` · ${t('provisional until backend analysis confirms it')}` : ''}{entryRuleInference.mode === 'MANUAL' ? ` · ${t('Manual override')}: ${entryRuleInference.selectedRule}` : ` · ${t('Automatic selection')}`}</p>
                    </article>}
                    {projectionNotices.length > 0 && <article>
                        <strong>{t('Inference information')}</strong>
                        {projectionNotices.map((notice, index) => <div key={`${notice.code}:${notice.path}:${index}`}>
                            <p>{t(notice.message)}</p>
                            <details><summary>{t('Technical details')}</summary><span>{notice.code}{notice.path ? ` · ${notice.path}` : ''}</span></details>
                        </div>)}
                    </article>}
                    <GrammarCompilationPanel snapshot={snapshot} provider={provider}/>
                </div>)}
        </div>}
    </section>;
}
