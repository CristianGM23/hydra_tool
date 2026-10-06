import { useContext } from 'react';
import { HydraLanguageContext, type HydraLanguageContextValue } from './hydraLanguageContextValue';

export function useHydraLanguage(): HydraLanguageContextValue {
    return useContext(HydraLanguageContext);
}
