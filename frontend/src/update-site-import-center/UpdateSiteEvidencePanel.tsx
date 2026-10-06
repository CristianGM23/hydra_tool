import { HydraButton } from '../design-system/HydraButton';
import { useMemo, useState } from 'react';
import { HydraFormField, HydraSelect, HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { UpdateSiteConfidence, UpdateSiteImportAnalysis } from './types';

interface Props { analysis: UpdateSiteImportAnalysis; }

export function UpdateSiteEvidencePanel({ analysis }: Props) {
    const { t } = useHydraLanguage();
    const [query, setQuery] = useState('');
    const [confidence, setConfidence] = useState<UpdateSiteConfidence | 'ALL'>('ALL');
    const [visibleCount, setVisibleCount] = useState(50);
    const filtered = useMemo(() => analysis.provenance.filter(item =>
        (confidence === 'ALL' || item.confidence === confidence)
        && `${item.elementPath} ${item.origin} ${item.sourcePath} ${item.originalElement} ${item.method}`
            .toLowerCase().includes(query.toLowerCase())), [analysis.provenance, confidence, query]);
    const visible = filtered.slice(0, visibleCount);
    const sourceCount = new Set(analysis.provenance.map(item => item.sourcePath)).size;
    const warningCount = analysis.provenance.reduce((total, item) => total + item.warnings.length, 0);

    return <section className="update-site-evidence-panel">
        <header className="update-site-section-header">
            <div>
                <p className="update-site-eyebrow">{t('Traceability')}</p>
                <h3>{t('Element provenance and confidence')}</h3>
                <p>{t('Trace every recovered element back to its bundle, file, original identifier and recovery method.')}</p>
            </div>
            <HydraTooltip label={t('About provenance and evidence')} title={t('Why provenance matters')}>
                <p>{t('Recovered structures may come from original Ecore, generated EMF code, registries, schemas or static inference. HYDRA keeps the origin visible so a reconstruction is never presented as if it were the original source.')}</p>
                <p>{t('Use confidence as a review aid, not as a guarantee. Exact evidence is strongest; inferred evidence requires the most manual review.')}</p>
            </HydraTooltip>
        </header>

        <div className="update-site-evidence-summary">
            <article><strong>{analysis.provenance.length}</strong><span>{t('Traced elements')}</span></article>
            <article><strong>{sourceCount}</strong><span>{t('Evidence files')}</span></article>
            <article><strong>{warningCount}</strong><span>{t('Warnings')}</span></article>
        </div>

        <div className="update-site-evidence-filters">
            <HydraFormField label={t('Search evidence')} targetId="update-site-evidence-search"
                help={t('Search by neutral path, source bundle, source file, original identifier or recovery method.')}>
                <input id="update-site-evidence-search" value={query}
                    onChange={event => { setQuery(event.target.value); setVisibleCount(50); }} placeholder={t('Book.authors, library.ecore, bytecode…')} />
            </HydraFormField>
            <HydraFormField label={t('Confidence')} targetId="update-site-evidence-confidence"
                help={t('Filter by how directly HYDRA can tie an element to source evidence. The level does not hide provenance and does not silently promote reconstructed elements.')}>
                <HydraSelect id="update-site-evidence-confidence" ariaLabel={t('Confidence')} value={confidence} options={[
                    { value:'ALL',label:t('All confidence levels'),description:t('Show every traced element. Use this for a complete audit regardless of how each element was recovered.') },
                    { value:'EXACT',label:t('Exact'),description:t('Original authoritative evidence: for example the source Ecore or another directly parsed original definition. Prefer this level when an exact source exists.') },
                    { value:'RECOVERED',label:t('Recovered'),description:t('Reconstructed from authoritative generated artifacts such as GenModel, plug-in registry or generated EMF structures. Usually reliable, but not identical to the missing original source.') },
                    { value:'DERIVED',label:t('Derived'),description:t('Safely transformed from another recognized representation such as a schema, grammar or mapping document. Review transformation assumptions before replacing existing definitions.') },
                    { value:'INFERRED',label:t('Inferred'),description:t('Deduced from indirect static evidence such as bytecode or source patterns. Treat it as a candidate that requires explicit manual review before materialization.') }
                ]} onChange={value => { setConfidence(value as UpdateSiteConfidence | 'ALL'); setVisibleCount(50); }} />
            </HydraFormField>
        </div>

        <div className="update-site-evidence-list">
            {visible.map(item => <article key={`${item.elementId}:${item.elementPath}`}>
                <header>
                    <div>
                        <strong aria-label={`${t('Neutral path')} ${item.elementPath}`}>{item.elementPath}</strong>
                        <code>{item.elementId}</code>
                    </div>
                    <span className={`update-site-confidence confidence-${item.confidence.toLowerCase()}`}>{t(confidenceLabel(item.confidence))}</span>
                </header>
                <dl>
                    <dt>{t('Origin bundle')}</dt><dd>{item.origin || t('Repository root')}</dd>
                    <dt>{t('Source file')}</dt><dd><code>{item.sourcePath}</code></dd>
                    <dt>{t('Original element')}</dt><dd>{t(originalElementLabel(item.elementPath, item.originalElement))}</dd>
                    <dt>{t('Recovery method')}</dt><dd>{t(human(item.method))}</dd>
                    <dt>{t('Format')}</dt><dd>{item.format}</dd>
                </dl>
                {item.warnings.length > 0 && <div className="update-site-evidence-warnings">
                    <strong>{t('Warnings')}</strong>
                    <ul>{item.warnings.map(warning => <li key={warning}>{warning}</li>)}</ul>
                </div>}
            </article>)}
            {filtered.length === 0 && <p className="update-site-chart-empty">{t('No provenance entries match the current filters.')}</p>}
            {visible.length < filtered.length && <HydraButton className="update-site-evidence-load-more" type="button" onClick={() => setVisibleCount(current => current + 50)}>{t('Load 50 more')} · {t('showing')} {visible.length} {t('of')} {filtered.length}</HydraButton>}
        </div>
    </section>;
}

function originalElementLabel(elementPath: string, originalElement: string): string {
    const original = originalElement.trim();
    if (original === '') return 'Not available';
    if (original === elementPath.trim()) return 'Same as neutral path';
    return original;
}
function confidenceLabel(value: UpdateSiteConfidence): string { return value === 'EXACT' ? 'Exact' : value === 'RECOVERED' ? 'Recovered' : value === 'DERIVED' ? 'Derived' : 'Inferred'; }
function human(value: string): string { return value.replaceAll('_', ' ').toLowerCase().replace(/^./, letter => letter.toUpperCase()); }
