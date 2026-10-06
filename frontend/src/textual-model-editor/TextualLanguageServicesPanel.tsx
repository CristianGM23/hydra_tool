import { HydraButton } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { GrammarCompilationSnapshot } from '../grammar-designer/types';
import type { TextualRuntimeNegotiation } from './types';

interface Props {
    snapshot: GrammarCompilationSnapshot | null;
    negotiation: TextualRuntimeNegotiation | null;
    loading: boolean;
    error: string;
    onDownloadProject: () => void;
    neutralCompletionAvailable?: boolean;
}

const orderedFeatures = ['DIAGNOSTICS', 'OUTLINE', 'QUICK_FIXES', 'COMPLETION', 'HOVER', 'GO_TO_DEFINITION', 'FIND_REFERENCES', 'RENAME', 'FORMATTING', 'FOLDING'] as const;

export function TextualLanguageServicesPanel({ snapshot, negotiation, loading, error, onDownloadProject, neutralCompletionAvailable = false }: Props) {
    const { t } = useHydraLanguage();
    const supported = new Set(negotiation?.supportedFeatures ?? []);
    if(neutralCompletionAvailable)supported.add('COMPLETION');
    const unavailable = new Set(negotiation?.unavailableFeatures ?? []);
    const generatedFiles = Object.keys(snapshot?.generatedProject.files ?? {}).length;

    return <section className="textual-language-services" aria-label={t('Textual editor services')}>
        <header>
            <div>
                <strong>{t('Textual editor services')}</strong>
                <p>{t('HYDRA separates canonical model services from provider-generated editor services. Diagnostics, outline and safe quick fixes remain HYDRA capabilities; completion, hover, navigation and rename depend on what the selected grammar runtime can actually provide.')}</p>
            </div>
            <span className="textual-runtime-badge">{loading ? t('Checking runtime…') : t(negotiation?.effectiveMode ?? 'Runtime not available')}</span>
        </header>
        {error !== '' && <div className="textual-runtime-warning" role="status"><strong>{t('Runtime not ready')}</strong><span>{t('The active grammar snapshot does not expose a usable editor runtime yet. Generate or compile the grammar project and choose a compatible runtime mode.')}</span><details><summary>{t('Technical details')}</summary><code>{error}</code></details></div>}
        <div className="textual-language-service-grid">
            {orderedFeatures.map(feature => {
                const status = supported.has(feature) ? 'supported' : unavailable.has(feature) ? 'unavailable' : 'unknown';
                return <div className={`textual-language-service ${status}`} key={feature}>
                    <strong>{t(feature)}</strong>
                    <span>{feature==='COMPLETION'&&neutralCompletionAvailable?t('Available · Ctrl+Space · derived from the active grammar'):t(status === 'supported' ? 'Available' : status === 'unavailable' ? 'Requires another generated runtime or provider capability' : 'Not negotiated')}</span>
                </div>;
            })}
        </div>
        <div className="textual-generated-project-summary">
            <div>
                <strong>{t('Generated language project')}</strong>
                <span>{snapshot === null
                    ? t('No active compiled grammar snapshot is available yet.')
                    : `${snapshot.generatedProject.projectId || snapshot.grammarId} · ${generatedFiles} ${t('files')}`}</span>
                {snapshot !== null && <small>{t('Build this project when the provider exposes completion, hover or other services through a generated LSP, worker or parser host.')}</small>}
            </div>
            <HydraButton disabled={snapshot === null || snapshot.status !== 'SUCCESS' || generatedFiles === 0} type="button" onClick={onDownloadProject}>{t('Download generated language project')}</HydraButton>
        </div>
    </section>;
}
