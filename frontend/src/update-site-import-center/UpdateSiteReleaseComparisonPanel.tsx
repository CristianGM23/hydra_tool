import { HydraButton } from '../design-system/HydraButton';
import { useMemo, useState } from 'react';
import { HydraFormField, HydraSelect, HydraTooltip } from '../design-system';
import { explainSubject } from './UpdateSiteDiagnosticKnowledge';
import type { UpdateSiteReleaseComparison, UpdateSiteReleaseRisk, UpdateSiteVersionDelta } from './types';

interface Props { release: UpdateSiteReleaseComparison; }
type ChangeFilter = 'ALL' | 'MODEL' | 'HIGH_RISK';

export function UpdateSiteReleaseComparisonPanel({ release }: Props) {
    const [filter, setFilter] = useState<ChangeFilter>('ALL');
    const [selectedId, setSelectedId] = useState('');
    const filtered = useMemo(() => release.deltas.filter(delta => {
        if (filter === 'MODEL') return delta.modelBearing;
        if (filter === 'HIGH_RISK') return delta.risk === 'HIGH';
        return true;
    }), [filter, release.deltas]);
    const selected = filtered.find(delta => delta.id === selectedId) ?? filtered[0] ?? null;
    const totals = release.deltas.reduce((result, delta) => ({
        added: result.added + delta.addedArtifacts.length,
        removed: result.removed + delta.removedArtifacts.length,
        changed: result.changed + delta.changedArtifacts.length,
        unchanged: result.unchanged + delta.unchangedArtifacts
    }), { added: 0, removed: 0, changed: 0, unchanged: 0 });

    return <section className="update-site-release-comparison">
        <header className="update-site-release-header">
            <div>
                <h3>Visual release comparison</h3>
                <p>Compare bundle versions by content, not only by file name or version number.</p>
            </div>
            <HydraTooltip label="About release comparison" title="Release comparison">
                <p>Changed means the same relative artifact path exists in both versions but its SHA-256 content differs.</p>
                <p>High risk does not mean invalid: it means model-bearing evidence changed and should be recovered again.</p>
            </HydraTooltip>
        </header>

        <div className="update-site-release-kpis">
            <ReleaseMetric label="Added" value={totals.added} tone="added" />
            <ReleaseMetric label="Changed" value={totals.changed} tone="changed" />
            <ReleaseMetric label="Removed" value={totals.removed} tone="removed" />
            <ReleaseMetric label="Unchanged" value={totals.unchanged} tone="unchanged" />
            <ReleaseMetric label="Unresolved child repositories" value={release.unresolvedCompositeChildren} tone="warning" />
        </div>

        <div className="update-site-release-controls">
            <HydraFormField label="Comparison filter" targetId="release-comparison-filter"
                help="Restricts the list without changing the underlying analysis.">
                <HydraSelect id="release-comparison-filter" ariaLabel="Comparison filter" value={filter} options={[{value:'ALL',label:'All version deltas',description:'Show every detected release difference.'},{value:'MODEL',label:'Model-bearing bundles',description:'Show bundles that contain model-related artifacts.'},{value:'HIGH_RISK',label:'High-risk deltas',description:'Show only changes with elevated compatibility risk.'}]} onChange={value => setFilter(value as ChangeFilter)} />
            </HydraFormField>
            <span>{filtered.length} comparison(s)</span>
        </div>

        <div className="update-site-release-layout">
            <div className="update-site-release-list" aria-label="Available release comparisons">
                {filtered.map(delta => <HydraButton className={selected?.id === delta.id ? 'selected' : ''}
                    key={`${delta.artifactType}:${delta.id}`} onClick={() => setSelectedId(delta.id)} type="button">
                    <span><strong>{delta.id}</strong><small>{delta.fromVersion} → {delta.toVersion}</small></span>
                    <RiskBadge risk={delta.risk} />
                    <DeltaBar delta={delta} />
                </HydraButton>)}
                {filtered.length === 0 && <p>No release deltas match this filter.</p>}
            </div>

            {selected !== null && <ReleaseDetail delta={selected} />}
        </div>

        <section className="update-site-composite-children">
            <header><h4>Composite repository children</h4><span>{release.compositeRepositories.length}</span></header>
            <div>{release.compositeRepositories.map(child => <article key={`${child.sourcePath}:${child.location}`}>
                <strong>{child.location}</strong>
                <span>{child.presentInUpload ? 'Included' : child.remote ? 'Remote reference' : 'Missing child'}</span>
                <p>{child.explanation}</p>
                <code>{child.sourcePath}</code>
            </article>)}</div>
        </section>
    </section>;
}

function ReleaseDetail({ delta }: { delta: UpdateSiteVersionDelta }) {
    const groups = [
        { id: 'ADDED', label: 'Added artifacts', values: delta.addedArtifacts },
        { id: 'CHANGED', label: 'Changed content', values: delta.changedArtifacts },
        { id: 'REMOVED', label: 'Removed artifacts', values: delta.removedArtifacts }
    ];
    return <article className="update-site-release-detail">
        <header>
            <div><span>{delta.artifactType}</span><h4>{delta.id}</h4><p>{delta.fromVersion} → {delta.toVersion}</p></div>
            <RiskBadge risk={delta.risk} />
        </header>
        <p>{delta.explanation}</p>
        <div className="update-site-release-recommendation"><strong>Recommended action</strong><span>{delta.recommendation}</span></div>
        <DeltaBar delta={delta} detailed />
        <div className="update-site-release-change-groups">
            {groups.map(group => <section key={group.id}>
                <header><strong>{group.label}</strong><span>{group.values.length}</span></header>
                {group.values.length === 0 ? <small>No items.</small> : group.values.map(path => {
                    const explanation = explainSubject(path);
                    return <details key={path}>
                        <summary><code>{path}</code></summary>
                        <p><strong>Purpose:</strong> {explanation.purpose}</p>
                        <p><strong>Impact:</strong> {explanation.impact}</p>
                    </details>;
                })}
            </section>)}
        </div>
    </article>;
}

function DeltaBar({ delta, detailed = false }: { delta: UpdateSiteVersionDelta; detailed?: boolean }) {
    const values = [delta.addedArtifacts.length, delta.changedArtifacts.length, delta.removedArtifacts.length, delta.unchangedArtifacts];
    const total = Math.max(1, values.reduce((sum, value) => sum + value, 0));
    return <div className={`update-site-delta-bar${detailed ? ' detailed' : ''}`} aria-label={`${values[0]} added, ${values[1]} changed, ${values[2]} removed and ${values[3]} unchanged`}>
        <span className="added" style={{ width: `${values[0] / total * 100}%` }} />
        <span className="changed" style={{ width: `${values[1] / total * 100}%` }} />
        <span className="removed" style={{ width: `${values[2] / total * 100}%` }} />
        <span className="unchanged" style={{ width: `${values[3] / total * 100}%` }} />
    </div>;
}

function ReleaseMetric({ label, value, tone }: { label: string; value: number; tone: string }) {
    return <article className={`update-site-release-metric ${tone}`}><strong>{value}</strong><span>{label}</span></article>;
}

function RiskBadge({ risk }: { risk: UpdateSiteReleaseRisk }) {
    return <span className={`update-site-release-risk risk-${risk.toLowerCase()}`}>{risk}</span>;
}
