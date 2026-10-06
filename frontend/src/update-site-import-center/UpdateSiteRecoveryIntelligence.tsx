import type { UpdateSiteImportAnalysis } from './types';

interface Props { analysis: UpdateSiteImportAnalysis; }

/** Compact, user-facing summary of how HYDRA reconstructed the imported source. */
export function UpdateSiteRecoveryIntelligence({ analysis }: Props) {
    const confidence = {
        exact: analysis.candidates.filter(candidate => candidate.confidence === 'EXACT').length,
        recovered: analysis.candidates.filter(candidate => candidate.confidence === 'RECOVERED').length,
        derived: analysis.candidates.filter(candidate => candidate.confidence === 'DERIVED').length,
        inferred: analysis.candidates.filter(candidate => candidate.confidence === 'INFERRED').length,
    };
    const formats = unique(analysis.candidates.map(candidate => candidate.format)).slice(0, 12);
    const reconciliation = analysis.reconciliation;
    const safe = reconciliation?.conflicts.filter(conflict => !conflict.blocking).length ?? 0;
    const blocking = reconciliation?.conflicts.filter(conflict => conflict.blocking).length ?? 0;
    return <section className="update-site-recovery-intelligence">
        <header><div><span>Recovery intelligence</span><h3>How much can HYDRA reconstruct automatically?</h3><p>HYDRA combines exact models, Eclipse metadata, schemas, grammars and static source inference. It never executes recovered project code during analysis.</p></div></header>
        <div className="update-site-recovery-confidence-grid">
            <article className="exact"><strong>{confidence.exact}</strong><span>Exact sources</span><small>Original model evidence.</small></article>
            <article className="recovered"><strong>{confidence.recovered}</strong><span>Recovered sources</span><small>Reconstructed from model/editor metadata.</small></article>
            <article className="derived"><strong>{confidence.derived}</strong><span>Derived sources</span><small>Schemas and grammars transformed safely.</small></article>
            <article className="inferred"><strong>{confidence.inferred}</strong><span>Source inference</span><small>Java, Kotlin, TypeScript, Go and other static evidence.</small></article>
        </div>
        <div className="update-site-recovery-intelligence-row">
            <div><strong>Detected source formats</strong><div className="update-site-format-chips">{formats.map(format => <span key={format}>{human(format)}</span>)}</div></div>
            <div className="update-site-resolution-summary"><strong>Conflict assistance</strong><span><b>{safe}</b> safe automatic resolution(s)</span><span><b>{blocking}</b> decision(s) still require review</span></div>
        </div>
        <div className="update-site-source-capability-note"><strong>Additional repository recovery</strong><span>Xcore metamodels and TypeScript/Go model source are recovered statically when original Ecore/GenModel evidence is not available. Complementary class features can be unioned automatically when they do not conflict.</span></div>
    </section>;
}
function unique(values: string[]): string[] { return Array.from(new Set(values)); }
function human(value: string): string { return value.replaceAll('_', ' ').toLowerCase().replace(/^./, letter => letter.toUpperCase()); }
