/**
 * Fachada estable de compatibilidad para la configuración visual.
 * Mantiene los imports históricos mientras separa proveedor y hook para React Refresh.
 */
export { AppearanceSettingsProvider } from './AppearanceSettingsProvider';
export { useAppearanceSettings } from './useAppearanceSettings';
export type { AppearanceSettingsContextValue } from './appearanceSettingsContextValue';
