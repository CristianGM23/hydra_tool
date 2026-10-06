import { useState } from 'react';
import { useHydraLanguage } from '../i18n';
import type { GrammarCodeAction, GrammarDiagnostic } from './types';
import { oneBasedLocation } from '../grammar-language-service/grammarDiagnostics';
import { HydraButton } from '../design-system';

interface Props {
    diagnostics: readonly GrammarDiagnostic[];
    actions: readonly GrammarCodeAction[];
    onNavigate: (item: GrammarDiagnostic) => void;
    onApply: (action: GrammarCodeAction) => void;
}

export function GrammarProblemsPanel({ diagnostics, actions, onNavigate, onApply }: Props) {
    const { t } = useHydraLanguage();
    const [expanded, setExpanded] = useState('');
    const byId = new Map(actions.map(action => [action.id, action]));
    if (diagnostics.length === 0) return <section className="grammar-problems empty">
        <strong>{t('No blocking problems')}</strong>
        <span>{t('The draft can be analyzed and compiled.')}</span>
    </section>;

    return <section className="grammar-problems">{diagnostics.map((item, index) => {
        const key = `${item.code}:${item.documentId}:${item.range.start.line}:${item.range.start.column}:${index}`;
        const open = expanded === key;
        const quickFixes = item.codeActionIds.map(id => byId.get(id)).filter((value): value is GrammarCodeAction => value !== undefined);
        return <article className={`grammar-problem ${item.severity.toLowerCase()}`} key={key}>
            <header>
                <HydraButton type="button" onClick={() => onNavigate(item)}><strong>{t(item.title)}</strong><span>{item.documentId} · {oneBasedLocation(item)}</span></HydraButton>
                <HydraButton type="button" aria-expanded={open} onClick={() => setExpanded(open ? '' : key)}>{t(open ? 'Hide' : 'Details')}</HydraButton>
            </header>
            <p>{t(item.message)}</p>
            {quickFixes.length > 0 && <div className="grammar-problem-quick-fixes" aria-label={t('Quick fixes')}>
                <strong>{t('Quick fixes')}</strong>
                <div>{quickFixes.map(action => <HydraButton className="grammar-quick-fix" variant={action.safeAutomatic ? 'primary' : 'secondary'} type="button" onClick={() => onApply(action)} key={action.id}>{t(action.metadata['automaticResolution']==='true'?'Resolve automatically':action.safeAutomatic ? 'Apply safe correction' : 'Apply correction')} · {t(action.title)}</HydraButton>)}</div>
            </div>}
            {open && <div className="grammar-problem-details">
                <dl>
                    <dt>{t('From')}</dt><dd>{[item.rule && `${t('Rule')}: ${item.rule}`, item.property && `${t('Property')}: ${item.property}`, item.expectedType && `${t('Expected type')}: ${item.expectedType}`].filter(Boolean).join(' · ') || t('Grammar context')}</dd>
                    <dt>{t('Why')}</dt><dd>{t(item.cause)}</dd>
                    <dt>{t('Consequence')}</dt><dd>{t(item.consequence)}</dd>
                    <dt>{t('How to fix it')}</dt><dd>{t(item.solution)}</dd>
                </dl>
                {item.candidates.length > 0 && <div><strong>{t('HYDRA found candidates')}</strong><ol>{item.candidates.slice(0, 5).map(candidate => <li key={candidate.symbolId}><span>{candidate.name}</span><span>{Math.round(candidate.score * 100)} %</span><small>{t(candidate.reason)}</small></li>)}</ol></div>}
                {(item.nativeCode || item.technicalDetail) && <details><summary>{t('Technical details')}</summary><pre>{[item.nativeCode, item.technicalDetail].filter(Boolean).join('\n')}</pre></details>}
            </div>}
        </article>;
    })}</section>;
}
