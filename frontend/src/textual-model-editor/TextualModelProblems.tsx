import { Fragment, useState } from 'react';
import { HydraButton } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { TextualModelDiagnostic } from './types';

interface Props {
    diagnostics: readonly TextualModelDiagnostic[];
    pending?: boolean;
    onNavigate?: (diagnostic: TextualModelDiagnostic) => void;
    onApplySyntaxFix?: (diagnostic: TextualModelDiagnostic) => void;
}

export function TextualModelProblems({ diagnostics, pending = false, onNavigate, onApplySyntaxFix }: Props) {
    const { t } = useHydraLanguage();
    const [expanded, setExpanded] = useState('');
    return <section className="textual-problems">
        <header>
            <div><strong>{t('Textual model problems')}</strong><span>{t('Problems are explained against the active compiled semantic contract; the broken draft never replaces the last valid model.')}</span></div>
            <span className="textual-problem-count">{diagnostics.length}</span>
        </header>
        {pending
            ? <div className="textual-problems-empty"><strong>{t('Textual validation pending')}</strong><span>{t('HYDRA will not consider the draft valid until it has been checked against the active compiled grammar.')}</span></div>
            : diagnostics.length === 0
                ? <div className="textual-problems-empty"><strong>{t('No blocking textual problems')}</strong><span>{t('The draft matches the compiled semantic contract.')}</span></div>
                : diagnostics.map((item, index) => {
                const key = `${item.code}:${item.documentId}:${item.range.start.line}:${item.range.start.column}:${index}`;
                const open = expanded === key;
                const metadata = item.metadata ?? {};
                const cause = metadata.cause || t('The text cannot be reconciled safely with the active compiled grammar in this range.');
                const recommended = metadata.recommendedAction || item.solution;
                const expected = metadata.expectedHuman || metadata.expected || '';
                const quickFixLabel = metadata.quickFixLabel || '';
                return <article className={item.severity.toLowerCase()} key={key}>
                    <header>
                        <div>
                            <span className="textual-problem-severity">{t(item.severity)}</span>
                            <strong>{t(item.title)}</strong>
                            <span>{item.documentId} · {t('Line')} {item.range.start.line + 1}, {t('column')} {item.range.start.column + 1}</span>
                        </div>
                        <div className="textual-problem-actions">
                            {onNavigate && <HydraButton type="button" onClick={() => onNavigate(item)}>{t('Go to problem')}</HydraButton>}
                            <HydraButton aria-expanded={open} type="button" onClick={() => setExpanded(open ? '' : key)}>{t(open ? 'Hide details' : 'Show details')}</HydraButton>
                        </div>
                    </header>
                    <p className="textual-problem-summary">{t(item.message)}</p>
                    <div className="textual-problem-guidance">
                        <div><strong>{t('Why')}</strong><span>{t(cause)}</span></div>
                        <div><strong>{t('Recommended solution')}</strong><span>{t(recommended)}</span>{expected !== '' && <small>{t('Expected by the grammar')}: {expected}</small>}{quickFixLabel !== '' && onApplySyntaxFix && <HydraButton type="button" variant="primary" onClick={() => onApplySyntaxFix(item)}>{t(quickFixLabel)}</HydraButton>}</div>
                    </div>
                    {open && <div className="textual-problem-details">
                        <div><strong>{t('Consequence')}</strong><span>{t(item.consequence)}</span></div>
                        <details>
                            <summary>{t('Technical details')}</summary>
                            <dl>
                                <dt>{t('Code')}</dt><dd>{item.code}</dd>
                                <dt>{t('Document')}</dt><dd>{item.documentId}</dd>
                                {Object.entries(metadata).filter(([name]) => !['cause', 'recommendedAction'].includes(name)).map(([name, value]) => <Fragment key={name}><dt>{name}</dt><dd>{value}</dd></Fragment>)}
                            </dl>
                        </details>
                    </div>}
                </article>;
            })}
    </section>;
}
