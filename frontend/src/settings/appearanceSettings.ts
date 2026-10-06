export type ColorSchemeId = 'SYSTEM' | 'LIGHT' | 'DARK';
export type FontFamilyId = 'SYSTEM' | 'SEGOE_UI' | 'ARIAL' | 'VERDANA' | 'TAHOMA' | 'GEORGIA' | 'MONOSPACE';

export interface AppearanceSettings {
    colorScheme: ColorSchemeId;
    fontFamily: FontFamilyId;
    baseFontSize: number;
    windowFontSize: number;
    tabFontSize: number;
    formFontSize: number;
    diagramFontSize: number;
    tooltipFontSize: number;
    responsiveSizing: boolean;
}

export const FONT_SIZE_OPTIONS = [14, 16, 17, 18, 20, 22, 24, 28, 32] as const;

export const FONT_FAMILY_OPTIONS: { id: FontFamilyId; label: string; css: string }[] = [
    { id: 'SYSTEM', label: 'System UI', css: "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" },
    { id: 'SEGOE_UI', label: 'Segoe UI', css: "'Segoe UI', Arial, sans-serif" },
    { id: 'ARIAL', label: 'Arial', css: "Arial, Helvetica, sans-serif" },
    { id: 'VERDANA', label: 'Verdana', css: "Verdana, Geneva, sans-serif" },
    { id: 'TAHOMA', label: 'Tahoma', css: "Tahoma, Geneva, sans-serif" },
    { id: 'GEORGIA', label: 'Georgia', css: "Georgia, 'Times New Roman', serif" },
    { id: 'MONOSPACE', label: 'Monospace', css: "ui-monospace, Consolas, 'Courier New', monospace" },
];

export const DEFAULT_APPEARANCE_SETTINGS: AppearanceSettings = {
    colorScheme: 'SYSTEM',
    fontFamily: 'SYSTEM',
    baseFontSize: 18,
    windowFontSize: 18,
    tabFontSize: 16,
    formFontSize: 18,
    diagramFontSize: 18,
    tooltipFontSize: 18,
    responsiveSizing: true,
};

export const APPEARANCE_STORAGE_KEY = 'modeling-editor.appearance.v1';

export function fontFamilyCss(id: FontFamilyId): string {
    return FONT_FAMILY_OPTIONS.find(option => option.id === id)?.css ?? FONT_FAMILY_OPTIONS[0].css;
}

export function normalizeAppearanceSettings(value: Partial<AppearanceSettings> | null | undefined): AppearanceSettings {
    const fontFamily = FONT_FAMILY_OPTIONS.some(option => option.id === value?.fontFamily) ? value!.fontFamily! : DEFAULT_APPEARANCE_SETTINGS.fontFamily;
    const colorScheme: ColorSchemeId = value?.colorScheme === 'LIGHT' || value?.colorScheme === 'DARK' || value?.colorScheme === 'SYSTEM' ? value.colorScheme : DEFAULT_APPEARANCE_SETTINGS.colorScheme;
    return {
        colorScheme,
        fontFamily,
        baseFontSize: normalizeSize(value?.baseFontSize, DEFAULT_APPEARANCE_SETTINGS.baseFontSize),
        windowFontSize: normalizeSize(value?.windowFontSize, DEFAULT_APPEARANCE_SETTINGS.windowFontSize),
        tabFontSize: normalizeSize(value?.tabFontSize, DEFAULT_APPEARANCE_SETTINGS.tabFontSize),
        formFontSize: normalizeSize(value?.formFontSize, DEFAULT_APPEARANCE_SETTINGS.formFontSize),
        diagramFontSize: normalizeSize(value?.diagramFontSize, DEFAULT_APPEARANCE_SETTINGS.diagramFontSize),
        tooltipFontSize: normalizeSize(value?.tooltipFontSize, DEFAULT_APPEARANCE_SETTINGS.tooltipFontSize),
        responsiveSizing: typeof value?.responsiveSizing === 'boolean' ? value.responsiveSizing : DEFAULT_APPEARANCE_SETTINGS.responsiveSizing,
    };
}

function normalizeSize(value: number | undefined, fallback: number): number {
    return FONT_SIZE_OPTIONS.includes(value as (typeof FONT_SIZE_OPTIONS)[number]) ? value! : fallback;
}
