import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { translateUiText, type HydraLanguage } from './translationCatalog';
import { HYDRA_LANGUAGE_STORAGE_KEY, HydraLanguageContext, type HydraLanguageContextValue } from './hydraLanguageContextValue';

const STORAGE_KEY = HYDRA_LANGUAGE_STORAGE_KEY;

export function HydraLanguageProvider({ children }: { children: ReactNode }) {
    const [language, setLanguageState] = useState<HydraLanguage>(readLanguage);

    const setLanguage = useCallback((next: HydraLanguage): void => {
        setLanguageState(next);
        try { window.localStorage.setItem(STORAGE_KEY, next); } catch { /* Storage can be unavailable in hardened browsers. */ }
    }, []);

    useEffect(() => {
        document.documentElement.lang = language;
        document.documentElement.dataset.hydraLanguage = language;
    }, [language]);

    const value = useMemo<HydraLanguageContextValue>(() => ({
        language,
        setLanguage,
        toggleLanguage: () => setLanguage(language === 'en' ? 'es' : 'en'),
        t: text => translateUiText(text, language)
    }), [language, setLanguage]);

    return <HydraLanguageContext.Provider value={value}>{children}</HydraLanguageContext.Provider>;
}

function readLanguage(): HydraLanguage {
    try {
        const stored = window.localStorage.getItem(STORAGE_KEY);
        if (stored === 'en' || stored === 'es') return stored;
    } catch { /* Preserve the compatible English default. */ }
    return 'en';
}
