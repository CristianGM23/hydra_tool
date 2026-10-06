import { HydraButton, HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { INTEGRATION_RECIPES, type IntegrationRecipeId } from './integrationRecipes';

interface Props {
    onApply: (recipe: IntegrationRecipeId) => void;
}

/** Common multi-field presets. The complete technology and method catalogues remain above. */
export function IntegrationRecipeChooser({ onApply }: Props) {
    const { t } = useHydraLanguage();
    return <section className="integration-guided-recipes hydra-designer-wide" aria-labelledby="integration-recipes-title">
        <header className="integration-guided-recipes-header">
            <div>
                <span className="integration-guided-recipes-kicker">{t('Quick configuration')}</span>
                <strong id="integration-recipes-title">{t('Common integration configurations')}</strong>
                <p>{t('These are optional shortcuts for frequent scenarios. Each one selects a technology and coordinates delivery, integration method, capabilities and packaging; every resulting value remains editable.')}</p>
            </div>
            <HydraTooltip label={t('About quick integration configurations')} title={t('What do quick configurations change?')}>
                <p>{t('Quick configurations do not add hidden technologies. The complete destination catalogue is the Integration technology field and the complete set of host integration methods is shown above for the selected technology.')}</p>
                <p>{t('Use a shortcut when its scenario matches your goal. Otherwise configure the technology and integration method directly; HYDRA validates the same profile in both cases.')}</p>
            </HydraTooltip>
        </header>
        <div className="integration-guided-recipe-grid" role="group" aria-label={t('Available quick integration configurations')}>
            {INTEGRATION_RECIPES.map(recipe => <HydraButton className="integration-guided-recipe" key={recipe.id} type="button" onClick={() => onApply(recipe.id)}>
                <span><strong>{t(recipe.name)}</strong><small>{t(recipe.description)}</small></span>
            </HydraButton>)}
        </div>
        <small className="integration-guided-recipes-note">{t('The shortcuts are not the complete list of integrations. Change Integration technology to access EMF, Sirius, Xtext, Langium, BESSER, GLSP, Eclipse Modeling, VS Code, mxGraph / draw.io and projection targets.')}</small>
    </section>;
}
