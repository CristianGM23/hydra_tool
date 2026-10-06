import { createContext } from 'react';
import type { HydraLanguage } from './translationCatalog';

export const HYDRA_LANGUAGE_STORAGE_KEY = 'hydra.language.v1';

/** Contrato compartido por el proveedor y los consumidores del idioma de HYDRA. */
export interface HydraLanguageContextValue {
    language: HydraLanguage;
    setLanguage: (language: HydraLanguage) => void;
    toggleLanguage: () => void;
    t: (text: string) => string;
}

/** Contexto estable del idioma; su valor real lo proporciona HydraLanguageProvider. */
export const HydraLanguageContext = createContext<HydraLanguageContextValue>({
    language: 'en',
    setLanguage: () => undefined,
    toggleLanguage: () => undefined,
    t: text => text
});
