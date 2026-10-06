import { HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { injectionCategory, integrationDynamicDescription, orderedInjectionStrategies, readableIntegrationValue } from './integrationDesignerOptions';
import type { InjectionStrategy, IntegrationTechnologyDescriptor } from './types';

interface Props {
    descriptor: IntegrationTechnologyDescriptor;
    value: InjectionStrategy;
    onChange: (value: InjectionStrategy) => void;
}

/**
 * Shows every host-integration method supported by the selected technology.
 * Technology and integration method are deliberately separate concepts: adding
 * a new technology or strategy only requires descriptor metadata, not another
 * conditional branch in the guided UI.
 */
export function IntegrationMethodChooser({ descriptor, value, onChange }: Props) {
    const { t } = useHydraLanguage();
    return <section className="integration-guided-methods hydra-designer-wide" aria-labelledby="integration-methods-title">
        <header className="integration-guided-methods-header">
            <div>
                <span className="integration-guided-methods-kicker">{t('Host project')}</span>
                <strong id="integration-methods-title">{t('How to integrate the selected technology')}</strong>
                <p>{t('The technology identifies the destination. The integration method defines how HYDRA contributes generated artifacts to the host project without turning that method into another technology.')}</p>
            </div>
            <HydraTooltip label={t('About integration methods')} title={t('Technology versus integration method')}>
                <p>{t('A technology such as Sirius, GLSP, Langium, BESSER, VS Code or mxGraph / draw.io may support several safe integration methods. HYDRA only shows methods declared by the selected technology.')}</p>
                <p>{t('The recommended method is supplied by the technology catalogue. You can choose another supported method when the host project requires a different packaging boundary.')}</p>
            </HydraTooltip>
        </header>
        <div className="integration-guided-method-grid" role="radiogroup" aria-label={t('Available integration methods')}>
            {orderedInjectionStrategies(descriptor).map(strategy => {
                const recommended = strategy === descriptor.recommendedInjectionStrategy;
                return <label className={`integration-guided-method${value === strategy ? ' selected' : ''}${recommended ? ' recommended' : ''}`} key={strategy}>
                    <input checked={value === strategy} name="guided-integration-method" type="radio" value={strategy} onChange={() => onChange(strategy)} />
                    <span>
                        <strong>{t(readableIntegrationValue(strategy))}</strong>
                        <small>{t(integrationDynamicDescription(strategy))}</small>
                        <em>{recommended ? t('Recommended') : t(injectionCategory(strategy))}</em>
                    </span>
                </label>;
            })}
        </div>
        <small className="integration-guided-methods-note">{t('These are the integration methods available for the selected technology, not additional technologies. Change Integration technology above to see the methods supported by another platform.')}</small>
    </section>;
}
