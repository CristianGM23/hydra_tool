import { HydraButton } from '../design-system/HydraButton';
import { useHydraLanguage } from './useHydraLanguage';
import './i18n.css';

export function HydraLanguageToggle() {
    const { language, setLanguage, t } = useHydraLanguage();
    return <div className="hydra-language-toggle" aria-label={t('Switch application language')} role="group">
        <HydraButton aria-pressed={language === 'en'} size="small" variant={language === 'en' ? 'primary' : 'quiet'} onClick={() => setLanguage('en')}>EN</HydraButton>
        <HydraButton aria-pressed={language === 'es'} size="small" variant={language === 'es' ? 'primary' : 'quiet'} onClick={() => setLanguage('es')}>ES</HydraButton>
    </div>;
}
