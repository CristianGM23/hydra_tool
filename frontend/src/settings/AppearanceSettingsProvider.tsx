import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { APPEARANCE_STORAGE_KEY, DEFAULT_APPEARANCE_SETTINGS, fontFamilyCss, normalizeAppearanceSettings, type AppearanceSettings } from './appearanceSettings';
import { AppearanceSettingsContext } from './appearanceSettingsContextValue';
import type { AppearanceSettingsContextValue } from './appearanceSettingsContextValue';
import { responsiveAppearanceMetrics, type ViewportSize } from './responsiveSizing';

function readStoredSettings(): AppearanceSettings {
    try {
        const raw = window.localStorage.getItem(APPEARANCE_STORAGE_KEY);
        return raw === null ? DEFAULT_APPEARANCE_SETTINGS : normalizeAppearanceSettings(JSON.parse(raw) as Partial<AppearanceSettings>);
    } catch {
        return DEFAULT_APPEARANCE_SETTINGS;
    }
}

function readViewport(): ViewportSize {
    return { width: window.innerWidth, height: window.innerHeight };
}

/** Mantiene y aplica preferencias visuales configurables y adaptables al viewport. */
export function AppearanceSettingsProvider({ children }: { children: ReactNode }) {
    const [settings, setSettings] = useState<AppearanceSettings>(readStoredSettings);
    const [viewport, setViewport] = useState<ViewportSize>(readViewport);
    const [systemDark, setSystemDark] = useState(() => window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false);

    useEffect(() => {
        let frame = 0;
        function resize(): void {
            if (window.cancelAnimationFrame !== undefined) window.cancelAnimationFrame(frame);
            frame = window.requestAnimationFrame !== undefined
                ? window.requestAnimationFrame(() => setViewport(readViewport()))
                : window.setTimeout(() => setViewport(readViewport()), 0);
        }
        window.addEventListener('resize', resize, { passive: true });
        return () => {
            window.removeEventListener('resize', resize);
            if (window.cancelAnimationFrame !== undefined) window.cancelAnimationFrame(frame);
        };
    }, []);

    useEffect(() => {
        const media = window.matchMedia?.('(prefers-color-scheme: dark)');
        if (media === undefined) return;
        const changed = (event: MediaQueryListEvent) => setSystemDark(event.matches);
        media.addEventListener?.('change', changed);
        return () => media.removeEventListener?.('change', changed);
    }, []);

    useEffect(() => {
        const root = document.documentElement;
        const metrics = responsiveAppearanceMetrics(settings, viewport);
        root.style.setProperty('--app-font-family', fontFamilyCss(settings.fontFamily));
        root.style.setProperty('--app-configured-base-font-size', `${settings.baseFontSize}px`);
        root.style.setProperty('--app-configured-window-font-size', `${settings.windowFontSize}px`);
        root.style.setProperty('--app-configured-tab-font-size', `${settings.tabFontSize}px`);
        root.style.setProperty('--app-configured-form-font-size', `${settings.formFontSize}px`);
        root.style.setProperty('--app-configured-diagram-font-size', `${settings.diagramFontSize}px`);
        root.style.setProperty('--app-configured-tooltip-font-size', `${settings.tooltipFontSize}px`);
        root.style.setProperty('--app-responsive-scale', String(metrics.scale));
        root.style.setProperty('--app-base-font-size', `${metrics.baseFontSize}px`);
        root.style.setProperty('--app-window-font-size', `${metrics.windowFontSize}px`);
        root.style.setProperty('--app-tab-font-size', `${metrics.tabFontSize}px`);
        root.style.setProperty('--app-form-font-size', `${metrics.formFontSize}px`);
        root.style.setProperty('--app-diagram-font-size', `${metrics.diagramFontSize}px`);
        root.style.setProperty('--app-tooltip-font-size', `${metrics.tooltipFontSize}px`);
        root.style.setProperty('--app-toolbar-height', `${metrics.toolbarHeight}px`);
        root.style.setProperty('--app-control-min-height', `${metrics.controlMinHeight}px`);
        root.style.setProperty('--app-control-padding-x', `${metrics.controlPaddingHorizontal}px`);
        root.style.setProperty('--app-control-padding-y', `${metrics.controlPaddingVertical}px`);
        root.style.setProperty('--app-checkbox-size', `${metrics.checkboxSize}px`);
        root.style.setProperty('--app-panel-padding', `${metrics.panelPadding}px`);
        root.style.setProperty('--app-layout-gap', `${metrics.layoutGap}px`);
        const dark = settings.colorScheme === 'DARK' || (settings.colorScheme === 'SYSTEM' && systemDark);
        root.dataset.hydraTheme = dark ? 'dark' : 'light';
        root.dataset.hydraColorScheme = settings.colorScheme.toLowerCase();
        root.style.colorScheme = dark ? 'dark' : 'light';
        root.dataset.appFontFamily = settings.fontFamily;
        root.dataset.responsiveSizing = String(settings.responsiveSizing);
        root.dataset.responsiveScale = String(metrics.scale);
    }, [settings, viewport, systemDark]);

    const value = useMemo<AppearanceSettingsContextValue>(() => ({
        settings,
        updateSettings: next => {
            const normalized = normalizeAppearanceSettings(next);
            setSettings(normalized);
            persistSettings(normalized);
        },
        resetSettings: () => {
            setSettings(DEFAULT_APPEARANCE_SETTINGS);
            persistSettings(DEFAULT_APPEARANCE_SETTINGS);
        },
    }), [settings]);

    return <AppearanceSettingsContext.Provider value={value}>{children}</AppearanceSettingsContext.Provider>;
}

function persistSettings(settings: AppearanceSettings): void {
    try { window.localStorage.setItem(APPEARANCE_STORAGE_KEY, JSON.stringify(settings)); }
    catch { /* Storage can be unavailable in hardened browsers. */ }
}
