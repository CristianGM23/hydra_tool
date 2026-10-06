import { useMemo, useState } from 'react';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import { HydraFormField, HydraSelect, HydraStatusPill, HydraTooltip } from '../design-system';
import { explainDiagnostic, explainMissingDependency } from './UpdateSiteDiagnosticKnowledge';
import type { UpdateSiteDependency, UpdateSiteFileOutcome, UpdateSiteGrammarInspection,
    UpdateSiteImportLogEntry, UpdateSiteImportStage, UpdateSiteLogSeverity } from './types';

interface Props {
    logs: UpdateSiteImportLogEntry[];
    files: UpdateSiteFileOutcome[];
    grammars: UpdateSiteGrammarInspection[];
    missingDependencies?: UpdateSiteDependency[];
    diagnostics?: string[];
}

export function UpdateSiteImportLogPanel({
    logs,
    files,
    grammars,
    missingDependencies = [],
    diagnostics = []
}: Props) {
    const { t } = useHydraLanguage();
    const [stage, setStage] = useState<UpdateSiteImportStage | 'ALL'>('ALL');
    const [severity, setSeverity] = useState<UpdateSiteLogSeverity | 'ALL'>('ALL');
    const [query, setQuery] = useState('');
    const filtered = useMemo(() => logs.filter(entry => (stage === 'ALL' || entry.stage === stage)
        && (severity === 'ALL' || entry.severity === severity)
        && `${entry.title} ${entry.message} ${entry.sourcePath} ${entry.purpose}`.toLowerCase().includes(query.toLowerCase())),
    [logs, query, severity, stage]);
    const incompleteFiles = files.filter(file => file.status !== 'RECOVERED');
    const diagnosticItems = [
        ...missingDependencies.map(dependency => ({ key: `dependency:${dependency.sourceId}:${dependency.targetId}`,
            technical: `${dependency.sourceId} → ${dependency.targetId} ${dependency.versionRange}`.trim(),
            explanation: explainMissingDependency(dependency), severity: dependency.optional ? 'WARNING' : 'ERROR' })),
        ...diagnostics.map((diagnostic, index) => ({ key: `diagnostic:${index}`, technical: diagnostic,
            explanation: explainDiagnostic(diagnostic), severity: 'WARNING' }))
    ];

    return <div className="update-site-log-workspace">
        {diagnosticItems.length > 0 && <section className="update-site-diagnostic-summary">
            <header className="update-site-section-header">
                <div>
                    <p className="update-site-eyebrow">{t('Actionable diagnostics')}</p>
                    <h3>{t('Missing or incomplete elements')}</h3>
                    <p>{t('Each issue explains what the missing element normally does, the likely impact and the next useful action.')}</p>
                </div>
                <HydraTooltip label="About actionable diagnostics" title="How diagnostics are explained">
                    <p>{t('HYDRA separates technical evidence from a high-level explanation. The purpose tells you why the element exists in Eclipse or the original tool. Impact describes what may be absent from the reconstruction.')}</p>
                </HydraTooltip>
            </header>
            <div className="update-site-diagnostic-cards">
                {diagnosticItems.map(item => <article className={`diagnostic-${item.severity.toLowerCase()}`} key={item.key}>
                    <header>
                        <div><strong>{item.explanation.subject}</strong><span>{t(item.explanation.category)}</span></div>
                        <HydraStatusPill tone={severityTone(item.severity)}>{t(item.severity)}</HydraStatusPill>
                    </header>
                    <dl>
                        <dt>{t('What it is for')}</dt><dd>{t(item.explanation.purpose)}</dd>
                        <dt>{t('Recovery impact')}</dt><dd>{t(item.explanation.impact)}</dd>
                        <dt>{t('Recommended action')}</dt><dd>{t(item.explanation.recommendation)}</dd>
                    </dl>
                    <details><summary>{t('Technical detail')}</summary><code>{item.technical}</code></details>
                </article>)}
            </div>
        </section>}

        <section className="update-site-log-panel">
            <header>
                <div>
                    <p className="update-site-eyebrow">{t('Trace by stage')}</p>
                    <h3>{t('Detailed import log')}</h3>
                    <p>{t('Every important decision keeps its stage, evidence, purpose and recommended next action.')}</p>
                </div>
                <HydraTooltip label="About import logs" title="Why the log is detailed">
                    <p>{t('Update sites mix metamodels, generated code, runtime dependencies and UI resources. A file not converted is not necessarily an error.')}</p>
                    <p>{t('Open an entry to see what it is for, what HYDRA recovered and what should be reviewed next.')}</p>
                </HydraTooltip>
            </header>
            <div className="update-site-log-filters">
                <HydraFormField label="Search" targetId="update-site-log-search"
                    help="Search across stage titles, paths, explanations and high-level purpose.">
                    <input id="update-site-log-search" value={query}
                        onChange={event => setQuery(event.target.value)} placeholder="Path, stage or purpose" />
                </HydraFormField>
                <HydraFormField label="Stage" targetId="update-site-log-stage"
                    help="Limit the log to one recovery phase, such as grammar analysis or semantic recovery.">
                    <HydraSelect id="update-site-log-stage" ariaLabel="Stage" value={stage} options={[{ value:'ALL',label:'All stages',description:'Show entries from every recovery stage.' }, ...unique(logs.map(item => item.stage)).map(value => ({ value,label:human(value),description:'Show only log entries produced during this recovery stage.' }))]} onChange={value => setStage(value as UpdateSiteImportStage | 'ALL')} />
                </HydraFormField>
                <HydraFormField label="Severity" targetId="update-site-log-severity"
                    help="Warnings and errors deserve review; information and success entries preserve the audit trail.">
                    <HydraSelect id="update-site-log-severity" ariaLabel="Severity" value={severity} options={[{ value:'ALL',label:'All severities',description:'Show informational, successful, warning and error entries.' }, ...unique(logs.map(item => item.severity)).map(value => ({ value,label:human(value),description:'Show only log entries with this severity.' }))]} onChange={value => setSeverity(value as UpdateSiteLogSeverity | 'ALL')} />
                </HydraFormField>
            </div>
            <div className="update-site-log-list">
                {filtered.map(entry => <details className={`update-site-log-entry severity-${entry.severity.toLowerCase()}`} key={entry.id}>
                    <summary>
                        <span className="update-site-log-stage">{t(human(entry.stage))}</span>
                        <strong>{t(entry.title)}</strong>
                        <HydraStatusPill tone={logStatusTone(entry.severity)}>{t(human(entry.status))}</HydraStatusPill>
                        <small>{entry.sourcePath}</small>
                    </summary>
                    <div>
                        <p data-hydra-runtime-message="true" data-hydra-message-context="update">{entry.message}</p>
                        <dl>
                            <dt>{t('What this file or stage is for')}</dt><dd>{t(entry.purpose)}</dd>
                            {entry.recoveredAs !== '' && <><dt>{t('Recovered as')}</dt><dd>{t(entry.recoveredAs)}</dd></>}
                            {entry.nextAction !== '' && <><dt>{t('Recommended action')}</dt><dd>{t(entry.nextAction)}</dd></>}
                        </dl>
                        {entry.details.length > 0 && <details className="update-site-technical-details">
                            <summary>{t('Technical evidence')}</summary>
                            <ul>{entry.details.map(detail => <li key={detail}>{detail}</li>)}</ul>
                        </details>}
                    </div>
                </details>)}
                {filtered.length === 0 && <p className="update-site-chart-empty">{t('No log entries match the current filters.')}</p>}
            </div>
        </section>

        <section>
            <header className="update-site-section-header">
                <div>
                    <p className="update-site-eyebrow">{t('File-level review')}</p>
                    <h3>{t('Files that were not fully recovered')}</h3>
                    <p>{t('These files remain visible so partial reconstruction is never presented as the original source.')}</p>
                </div>
                <HydraTooltip label="About incomplete files" title="Why a file can remain incomplete">
                    <p>{t('Some files contain UI assets, runtime registrations or project-specific state instead of metamodel structure. They can still be valuable evidence.')}</p>
                </HydraTooltip>
            </header>
            <div className="update-site-file-outcomes">
                {incompleteFiles.map(file => <article key={file.sourcePath}>
                    <header>
                        <div><strong>{file.sourcePath}</strong><small>{t(human(file.artifactKind))}</small></div>
                        <HydraStatusPill tone={fileOutcomeTone(file.status)}>{t(human(file.status))}</HydraStatusPill>
                    </header>
                    <dl>
                        <dt>{t('What it is for')}</dt><dd>{t(file.purpose)}</dd>
                        <dt>{t('Recovery impact')}</dt><dd>{t(file.explanation)}</dd>
                        {file.recoveredAs !== '' && <><dt>{t('Recovered as')}</dt><dd>{t(file.recoveredAs)}</dd></>}
                        {file.nextAction !== '' && <><dt>{t('Recommended action')}</dt><dd>{t(file.nextAction)}</dd></>}
                    </dl>
                    {file.relatedElements.length > 0 && <details>
                        <summary>{t('Related neutral elements')}</summary>
                        <ul>{file.relatedElements.map(element => <li key={element}>{element}</li>)}</ul>
                    </details>}
                </article>)}
                {incompleteFiles.length === 0 && <p className="update-site-chart-empty">{t('Every inspected file was fully recovered.')}</p>}
            </div>
        </section>

        {grammars.length > 0 && <section>
            <header className="update-site-section-header">
                <div>
                    <p className="update-site-eyebrow">{t('Textual language analysis')}</p>
                    <h3>{t('Grammar inspection')}</h3>
                    <p>{t('Xtext and Langium files receive rule-level metrics, entry-root inference and structural recursion diagnostics.')}</p>
                </div>
                <HydraTooltip label="About grammar inspection" title="How grammar recovery works">
                    <p>{t('Parser rules can derive classes and relationships. Assignments describe properties. Cross-references describe links between model elements.')}</p>
                    <p>{t('HYDRA distinguishes safe recursion from problematic left recursion. Safe automatic rewrites are offered only when the semantic construction can be preserved and the result can be reanalyzed.')}</p>
                </HydraTooltip>
            </header>
            <div className="update-site-grammar-grid">
                {grammars.map(grammar => <article key={grammar.sourcePath}>
                    <header><strong>{grammar.language} · {grammar.grammarName}</strong><code>{grammar.sourcePath}</code></header>
                    <div>
                        <span>{t(`${grammar.parserRules} parser rules`)}</span>
                        <span>{t(`${grammar.terminalRules} terminals`)}</span>
                        <span>{t(`${grammar.assignments} assignments`)}</span>
                        <span>{t(`${grammar.crossReferences} cross refs`)}</span>
                    </div>
                    {grammar.inferredEntryRule && <p><strong>{t('Inferred entry rule')}:</strong> {grammar.inferredEntryRule} · {Math.round(grammar.entryInferenceConfidence*100)}% · {human(grammar.entryInferenceMethod)}</p>}
                    {grammar.recursiveRules.some(rule => !grammar.possibleLeftRecursions.includes(rule)) && <p><strong>{t('Recursive rules')}:</strong> {grammar.recursiveRules.filter(rule => !grammar.possibleLeftRecursions.includes(rule)).join(', ')}</p>}
                    {grammar.possibleLeftRecursions.length > 0 && <p>
                        <strong>{t('Left-recursive rules')}:</strong>{' '}
                        {grammar.possibleLeftRecursions.map(rule => {
                            const automatic = grammar.automaticResolutionRules.includes(rule);
                            return <span key={rule} className="update-site-recursion-rule">{rule}{automatic ? ` · ${t('automatic rewrite available')}` : ''}</span>;
                        })}
                    </p>}
                    {grammar.automaticResolutionRules.some(rule => !grammar.possibleLeftRecursions.includes(rule)) && <p><strong>{t('Automatic rewrite available')}:</strong> {grammar.automaticResolutionRules.filter(rule => !grammar.possibleLeftRecursions.includes(rule)).join(', ')}</p>}
                    {grammar.diagnostics.map(message => <small key={message}>{t(message)}</small>)}
                </article>)}
            </div>
        </section>}
    </div>;
}

function human(value: string): string {
    return value.replaceAll('_', ' ').toLowerCase();
}

function unique<T>(values: T[]): T[] {
    return Array.from(new Set(values));
}

function severityTone(value: string): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
    return value === 'ERROR' ? 'danger' : value === 'WARNING' ? 'warning' : value === 'SUCCESS' ? 'success' : 'info';
}
function logStatusTone(severity: UpdateSiteLogSeverity): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
    return severityTone(severity);
}
function fileOutcomeTone(status: UpdateSiteFileOutcome['status']): 'neutral' | 'info' | 'success' | 'warning' | 'danger' {
    return status === 'RECOVERED' ? 'success' : status === 'PARTIAL' ? 'warning' : status === 'SUPPORTING' ? 'info' : 'danger';
}
