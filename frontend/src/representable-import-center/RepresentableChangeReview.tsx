import { HydraFormField, HydraSelect } from '../design-system';
import type { MetaModel } from '../metamodel-designer/types';
import { useHydraLanguage } from '../i18n';
import type { ExternalChangeCandidate } from './types';
import { isClassLikeCandidate, matchingClasses, shortExternalName, type CandidateResolution, type CandidateResolutionMap } from './representableImportGuidance';

interface Props {
    changes: ExternalChangeCandidate[];
    metamodel: MetaModel | null;
    resolutions: CandidateResolutionMap;
    onResolutionChange: (externalId: string, resolution: CandidateResolution) => void;
    source: string;
    sourceError: string;
    onSourceChange: (source: string) => void;
}

export function RepresentableChangeReview({ changes, metamodel, resolutions, onResolutionChange, source, sourceError, onSourceChange }: Props) {
    const { t } = useHydraLanguage();
    return <div className="representable-change-review">
        <section className="representable-guidance-note"><strong>{t('What HYDRA detected')}</strong><p>{t('Review each external change in business terms. HYDRA will propose whether to create a new neutral element or reuse an existing metamodel element before it builds the technical operation.')}</p></section>
        {changes.length === 0 ? <div className="hydra-empty-state"><strong>{t('No external changes loaded')}</strong><span>{t('Open the technical input below and paste or load the normalized change list.')}</span></div> : <div className="representable-change-cards">{changes.map(candidate => {
            const classLike = isClassLikeCandidate(candidate);
            const classes = matchingClasses(candidate, metamodel);
            const selected = resolutions[candidate.externalId] ?? '';
            const externalName = shortExternalName(candidate);
            const options = [
                ...(candidate.changeKind === 'ADD' ? [{ value: 'CREATE_NEW', label: `${t('Create new class')} ${externalName}`, description: t('Use this when the external class represents a new concept that does not already exist in the selected metamodel.'), category: t('Create new') }] : []),
                ...classes.map(metaClass => ({ value: `EXISTING:${metaClass.id}`, label: `${t('Use existing class')} ${metaClass.name}`, description: metaClass.name.toLowerCase() === externalName.toLowerCase() ? t('Name match detected. Reusing this class avoids creating a duplicate concept.') : `${t('Map the external change to the existing class')} ${metaClass.name}.`, category: t(metaClass.name.toLowerCase() === externalName.toLowerCase() ? 'Recommended' : 'Existing metamodel classes') }))
            ];
            return <article className="representable-change-card" key={candidate.externalId}>
                <header><span className={`representable-change-kind ${candidate.changeKind.toLowerCase()}`}>{t(candidate.changeKind)}</span><div><strong>{externalName}</strong><small>{candidate.nativeKind} · {candidate.qualifiedName}</small></div></header>
                <p>{t(changeDescription(candidate))}</p>
                {classLike && <HydraFormField label="Destination in the metamodel" targetId={`representable-target-${candidate.externalId}`} required help="Choose whether this external class should become a new class or be reconciled with a class that already exists. HYDRA lists compatible classes from the selected metamodel and recommends exact name matches." example="Use existing class Customer" error={selected === '' ? 'Choose a destination before previewing this class change.' : undefined}>
                    <HydraSelect ariaLabel="Destination in the metamodel" value={selected} options={options} placeholder="Choose what HYDRA should do…" onChange={value => onResolutionChange(candidate.externalId, value as CandidateResolution)} />
                </HydraFormField>}
                {!classLike && <div className="representable-auto-resolution"><strong>{t('Automatic neutral mapping')}</strong><span>{t('This kind of element is mapped by the selected technology adapter. The preview will show whether the mapping is complete, partial or unsupported.')}</span></div>}
            </article>;
        })}</div>}
        <details className="representable-technical-input" open={sourceError !== '' || changes.length === 0}>
            <summary>{t('Technical input (advanced)')}</summary>
            <p>{t('Most users do not need to edit this JSON directly. It is the normalized change list produced by an external analyzer or integration.')}</p>
            <HydraFormField label="External change candidates" targetId="representable-source" required help="Normalized technical input used by the mapper. Prefer reviewing the cards above instead of editing IDs and kinds manually." error={sourceError || undefined}>
                <textarea id="representable-source" rows={14} value={source} onChange={event => onSourceChange(event.target.value)} />
            </HydraFormField>
        </details>
    </div>;
}

function changeDescription(candidate: ExternalChangeCandidate): string {
    if (candidate.changeKind === 'ADD') return 'The external source proposes adding this element. Decide whether it represents a new concept or one that already exists in HYDRA.';
    if (candidate.changeKind === 'UPDATE') return 'The external source proposes updating this element. Select the existing HYDRA element that should receive the change.';
    if (candidate.changeKind === 'DELETE') return 'The external source proposes deleting this element. Confirm which existing HYDRA element this change refers to before persisting it.';
    if (candidate.changeKind === 'RENAME') return 'The external source proposes renaming this element. Resolve it to the existing HYDRA element first.';
    return 'The external source proposes moving this element. Resolve the semantic destination before persisting the neutral package.';
}
