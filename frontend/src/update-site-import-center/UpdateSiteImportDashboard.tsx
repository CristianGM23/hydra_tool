import { HydraTooltip } from '../design-system';
import type { UpdateSiteDashboardSlice, UpdateSiteImportDashboard as Dashboard } from './types';

interface Props { dashboard: Dashboard; }

const CHART_COLORS = ['#16a34a', '#2563eb', '#d97706', '#dc2626', '#7c3aed', '#0891b2', '#db2777', '#65a30d', '#475569'];

export function UpdateSiteImportDashboard({ dashboard }: Props) {
    const classified = dashboard.recoveredFiles + dashboard.partialFiles + dashboard.supportingFiles
        + dashboard.unrecoverableFiles + dashboard.failedFiles;
    const tracedElements = dashboard.confidence.reduce((total, slice) => total + slice.value, 0);
    const needsReview = dashboard.partialFiles + dashboard.unrecoverableFiles + dashboard.failedFiles;

    return <section className="update-site-dashboard" aria-label="Import recovery dashboard">
        <header className="update-site-dashboard-header">
            <div>
                <p className="update-site-eyebrow">Analysis overview</p>
                <h3>Recovery dashboard</h3>
                <p>Read the result in seconds: coverage, confidence, file outcomes and detected bundle roles.</p>
            </div>
            <HydraTooltip label="About the recovery dashboard" title="How to read this dashboard">
                <p>Circular charts compare proportions. The number in the centre is the total represented by that chart.</p>
                <p>Coverage estimates how much repository information produced neutral HYDRA elements. Supporting files help explain or configure the original tool but do not directly become metamodel elements.</p>
            </HydraTooltip>
        </header>

        <div className="update-site-kpis">
            <Kpi value={dashboard.inspectedFiles} label="Files inspected"
                help="Every expanded file that HYDRA examined without executing bundle code." />
            <Kpi value={classified} label="Classified artifacts"
                help="Files assigned a recovery outcome after type and role inspection." />
            <Kpi value={tracedElements} label="Traced elements"
                help="Neutral elements with provenance and confidence information." />
            <Kpi value={needsReview} label="Need review"
                help="Partial, unrecoverable or failed files that deserve attention before importing." />
        </div>

        <div className="update-site-dashboard-grid">
            <article className="update-site-chart-card">
                <ChartTitle title="Recovery coverage"
                    help="Weighted coverage: fully recovered files count completely and partially recovered files count halfway." />
                <CoverageRing value={dashboard.recoveryCoverage} />
            </article>
            <article className="update-site-chart-card">
                <ChartTitle title="File outcomes"
                    help="Recovered files produced neutral content. Supporting files helped discovery. Partial and not recovered files remain visible for review." />
                <Donut slices={dashboard.fileStatus} ariaLabel="File outcome distribution" centreCaption="files" />
            </article>
            <article className="update-site-chart-card">
                <ChartTitle title="Confidence"
                    help="Exact comes from original metamodel sources. Recovered comes from generated artifacts. Derived comes from schemas or grammars. Inferred comes from code or data." />
                <Donut slices={dashboard.confidence} ariaLabel="Recovered element confidence distribution" centreCaption="elements" />
            </article>
            <article className="update-site-chart-card">
                <ChartTitle title="Bundle roles"
                    help="Roles are determined from manifests, extension points, dependencies and the artifacts contained by each bundle." />
                <Donut slices={dashboard.bundleRoles.slice(0, 9)} ariaLabel="Bundle role distribution" centreCaption="bundles" />
            </article>
        </div>
    </section>;
}

function Kpi({ value, label, help }: { value: number; label: string; help: string }) {
    return <article>
        <div className="update-site-kpi-heading">
            <span>{label}</span>
            <HydraTooltip label={`About ${label}`} title={label}><p>{help}</p></HydraTooltip>
        </div>
        <strong>{value}</strong>
    </article>;
}

function ChartTitle({ title, help }: { title: string; help: string }) {
    return <header className="update-site-chart-title">
        <h4>{title}</h4>
        <HydraTooltip label={`About ${title}`} title={title}><p>{help}</p></HydraTooltip>
    </header>;
}

function CoverageRing({ value }: { value: number }) {
    const normalized = Math.min(100, Math.max(0, value));
    const circumference = 276.46;
    const length = normalized / 100 * circumference;
    return <div className="update-site-donut-layout update-site-coverage-layout">
        <svg className="update-site-donut" viewBox="0 0 120 120" role="img"
            aria-label={`Recovery coverage ${Math.round(normalized)} percent`}>
            <circle className="update-site-donut-base" cx="60" cy="60" r="44" />
            <circle className="update-site-donut-segment update-site-coverage-segment" cx="60" cy="60" r="44"
                strokeDasharray={`${length} ${circumference - length}`} />
            <text x="60" y="57" textAnchor="middle">{Math.round(normalized)}%</text>
            <text className="update-site-donut-caption" x="60" y="72" textAnchor="middle">coverage</text>
        </svg>
        <div className="update-site-chart-explanation">
            <strong>{coverageLabel(normalized)}</strong>
            <span>{coverageExplanation(normalized)}</span>
        </div>
    </div>;
}

function Donut({ slices, ariaLabel, centreCaption }: {
    slices: UpdateSiteDashboardSlice[];
    ariaLabel: string;
    centreCaption: string;
}) {
    const visible = slices.filter(slice => slice.value > 0);
    const total = visible.reduce((sum, slice) => sum + slice.value, 0);
    if (total === 0) return <p className="update-site-chart-empty">No data was produced for this category.</p>;
    const circumference = 276.46;
    const segments = visible.map((slice, index) => ({
        slice,
        index,
        length: slice.value / total * circumference,
        offset: visible.slice(0, index).reduce((sum, previous) => sum + previous.value / total * circumference, 0)
    }));
    return <div className="update-site-donut-layout">
        <svg className="update-site-donut" viewBox="0 0 120 120" role="img" aria-label={ariaLabel}>
            <circle className="update-site-donut-base" cx="60" cy="60" r="44" />
            {segments.map(({ slice, index, length, offset }) => <circle
                className="update-site-donut-segment"
                cx="60" cy="60" r="44" key={slice.id}
                stroke={CHART_COLORS[index % CHART_COLORS.length]}
                strokeDasharray={`${length} ${circumference - length}`}
                strokeDashoffset={-offset}>
                <title>{slice.label}: {slice.value}. {slice.explanation}</title>
            </circle>)}
            <text x="60" y="57" textAnchor="middle">{total}</text>
            <text className="update-site-donut-caption" x="60" y="72" textAnchor="middle">{centreCaption}</text>
        </svg>
        <div className="update-site-chart-legend">
            {visible.map((slice, index) => <div key={slice.id}>
                <i style={{ background: CHART_COLORS[index % CHART_COLORS.length] }} />
                <span title={slice.explanation}>{slice.label}</span>
                <strong>{slice.value}</strong>
            </div>)}
        </div>
    </div>;
}

function coverageLabel(value: number): string {
    if (value >= 85) return 'High recovery coverage';
    if (value >= 60) return 'Useful recovery with review';
    if (value >= 30) return 'Partial recovery';
    return 'Limited recovery';
}

function coverageExplanation(value: number): string {
    if (value >= 85) return 'Most inspected files produced usable neutral information.';
    if (value >= 60) return 'The import is useful, but partial and supporting files should be reviewed.';
    if (value >= 30) return 'Important information was recovered, but missing dependencies or sources may limit fidelity.';
    return 'Treat the result as exploratory and inspect diagnostics before importing.';
}
