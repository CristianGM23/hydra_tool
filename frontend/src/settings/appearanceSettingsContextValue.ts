import { createContext } from 'react';
import type { AppearanceSettings } from './appearanceSettings';

export interface AppearanceSettingsContextValue {
    settings: AppearanceSettings;
    updateSettings: (settings: AppearanceSettings) => void;
    resetSettings: () => void;
}

export const AppearanceSettingsContext = createContext<AppearanceSettingsContextValue | null>(null);
