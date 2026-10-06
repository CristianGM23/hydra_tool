import { HydraCheckbox } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { ExternalChangeCandidate, RepresentableImportPreview } from './types';
import { diagnosticExplanation, diagnosticLevel, diagnosticStatusLabel, previewReviewState } from './representableImportGuidance';

interface Props {
    preview: RepresentableImportPreview;
    changes: ExternalChangeCandidate[];
    partialReviewed: boolean;
    onPartialReviewedChange: (reviewed: boolean) => void;
}

export function RepresentablePreviewPanel({ preview, changes, partialReviewed, onPartialReviewedChange }: Props) {
    const { t } = useHydraLanguage();
    const state = previewReviewState(preview);
    const candidates = new Map(changes.map(candidate => [candidate.externalId, candidate]));
    const partial = preview.diagnostics.filter(item => item.representability === 'PARTIAL');
    return <div className="representable-review-result">
        <header className={`representable-review-header ${state.tone}`}><span>{t(state.label)}</span><div><strong>{t(resultHeading(state.tone))}</strong><small>{preview.operations.length} {t('neutral operation(s)')} · {state.reviewCount} {t('to review')} · {state.issueCount} {t('blocking issue(s)')}</small></div></header>
        <section className="representable-proposals"><h3>{t('What HYDRA proposes')}</h3>{preview.operations.map(operation => {
            const candidate = candidates.get(operation.externalId);
            const mappedExisting = candidate?.properties['hydra:resolution'] === 'MAP_EXISTING';
            return <article key={operation.externalId}><span>{t(operation.changeKind)}</span><div><strong>{t(proposalTitle(operation.changeKind, mappedExisting))}: {operation.neutralPath}</strong><small>{candidate?.qualifiedName ?? operation.externalId}</small></div><p>{t(mappedExisting ? 'HYDRA will reconcile the external change with the selected existing metamodel element instead of creating a duplicate.' : 'HYDRA will preserve this as a neutral operation. Review the destination and incoming values before persisting.')}</p></article>;
        })}</section>
        <section className="representable-compatibility-review"><h3>{t('Compatibility review')}</h3><p>{t('Green entries are successful compatibility checks, not incidents. Amber entries need human review. Red entries block persistence.')}</p>{preview.diagnostics.map(diagnostic => {
            const level = diagnosticLevel(diagnostic.representability);
            const explanation = diagnosticExplanation(diagnostic, candidates.get(diagnostic.externalId));
            return <article className={`representable-diagnostic ${level}`} key={`${diagnostic.externalId}:${diagnostic.code}`}>
                <span>{t(diagnosticStatusLabel(diagnostic.representability))}</span>
                <div><strong>{t(explanation.title)}</strong><small className="representable-affected-element">{explanation.affected}</small><p>{t(explanation.explanation)}</p><div className="representable-next-action"><b>{t('What to do')}</b><span>{t(explanation.action)}</span></div><details><summary>{t('Technical details')}</summary><dl className="representable-technical-details"><div><dt>{t('Diagnostic code')}</dt><dd><code>{diagnostic.code}</code></dd></div><div><dt>{t('Affected element')}</dt><dd><code>{explanation.affected}</code></dd></div><div><dt>{t('Compatibility level')}</dt><dd>{t(diagnosticStatusLabel(diagnostic.representability))}</dd></div></dl></details></div>
            </article>;
        })}</section>
        {partial.length > 0 && preview.applicable && <HydraCheckbox id="representable-partial-reviewed" label="I have reviewed the partial mappings" description="Required before persisting a Safe review that contains amber compatibility checks." checked={partialReviewed} onChange={event => onPartialReviewedChange(event.currentTarget.checked)} />}
    </div>;
}

function resultHeading(tone: 'ready' | 'review' | 'blocked' | 'empty'): string {
    if (tone === 'ready') return 'The package is ready to persist';
    if (tone === 'review') return 'Review the amber changes before persisting';
    if (tone === 'blocked') return 'Resolve blocking changes before persisting';
    return 'Generate a preview first';
}

function proposalTitle(changeKind: string, mappedExisting: boolean): string {
    if (mappedExisting) return 'Reconcile with existing element';
    if (changeKind === 'ADD') return 'Create new element';
    if (changeKind === 'DELETE') return 'Remove element';
    if (changeKind === 'RENAME') return 'Rename element';
    if (changeKind === 'MOVE') return 'Move element';
    return 'Update element';
}
