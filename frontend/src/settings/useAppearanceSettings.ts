import { useContext } from 'react';
import { AppearanceSettingsContext } from './appearanceSettingsContextValue';
import type { AppearanceSettingsContextValue } from './appearanceSettingsContextValue';

export function useAppearanceSettings(): AppearanceSettingsContextValue {
    const context = useContext(AppearanceSettingsContext);
    if(context === null) throw new Error('useAppearanceSettings must be used inside AppearanceSettingsProvider');
    return context;
}
