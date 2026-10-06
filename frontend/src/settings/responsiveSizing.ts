import type { AppearanceSettings } from './appearanceSettings';

export interface ViewportSize {
    width: number;
    height: number;
}

export interface ResponsiveAppearanceMetrics {
    scale: number;
    baseFontSize: number;
    windowFontSize: number;
    tabFontSize: number;
    formFontSize: number;
    diagramFontSize: number;
    tooltipFontSize: number;
    toolbarHeight: number;
    controlMinHeight: number;
    controlPaddingHorizontal: number;
    controlPaddingVertical: number;
    checkboxSize: number;
    panelPadding: number;
    layoutGap: number;
}

const REFERENCE_WIDTH = 1440;
const REFERENCE_HEIGHT = 900;
const MINIMUM_SCALE = 0.78;
const MAXIMUM_SCALE = 1.18;

/**
 * Calcula un factor conservador que adapta la interfaz al espacio realmente
 * disponible sin ignorar los tamaños elegidos por el usuario.
 */
export function calculateResponsiveScale(viewport: ViewportSize, enabled: boolean): number {
    if(!enabled) return 1;
    const width = Number.isFinite(viewport.width) && viewport.width > 0 ? viewport.width : REFERENCE_WIDTH;
    const height = Number.isFinite(viewport.height) && viewport.height > 0 ? viewport.height : REFERENCE_HEIGHT;
    const widthScale = width / REFERENCE_WIDTH;
    const heightScale = height / REFERENCE_HEIGHT;
    return round(clamp(Math.min(widthScale, heightScale), MINIMUM_SCALE, MAXIMUM_SCALE), 3);
}

/**
 * Deriva tamaños efectivos para fuentes, controles y espaciados. El contrato
 * mantiene límites explícitos para impedir interfaces ilegibles o desbordadas.
 */
export function responsiveAppearanceMetrics(settings: AppearanceSettings, viewport: ViewportSize): ResponsiveAppearanceMetrics {
    const scale = calculateResponsiveScale(viewport, settings.responsiveSizing);
    return {
        scale,
        baseFontSize: scaled(settings.baseFontSize, scale, 12, 36),
        windowFontSize: scaled(settings.windowFontSize, scale, 12, 36),
        tabFontSize: scaled(settings.tabFontSize, scale, 11, 32),
        formFontSize: scaled(settings.formFontSize, scale, 12, 36),
        diagramFontSize: scaled(settings.diagramFontSize, scale, 11, 36),
        tooltipFontSize: scaled(settings.tooltipFontSize, scale, 12, 36),
        toolbarHeight: scaled(76, scale, 62, 92),
        controlMinHeight: scaled(38, scale, 32, 48),
        controlPaddingHorizontal: scaled(14, scale, 10, 18),
        controlPaddingVertical: scaled(7, scale, 5, 10),
        checkboxSize: scaled(21, scale, 18, 25),
        panelPadding: scaled(16, scale, 10, 22),
        layoutGap: scaled(12, scale, 8, 18),
    };
}

function scaled(value: number, scale: number, minimum: number, maximum: number): number {
    return clamp(Math.round(value * scale), minimum, maximum);
}

function clamp(value: number, minimum: number, maximum: number): number {
    return Math.min(maximum, Math.max(minimum, value));
}

function round(value: number, decimals: number): number {
    const factor = 10 ** decimals;
    return Math.round(value * factor) / factor;
}
