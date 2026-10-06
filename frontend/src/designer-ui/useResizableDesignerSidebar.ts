import { useMemo } from 'react';
import type { CSSProperties } from 'react';
import { clampHydraPanelWidth, useHydraResizablePanel } from '../design-system';
import type { HydraResizablePanelOptions, HydraResizablePanelState } from '../design-system';

export interface ResizableDesignerSidebarOptions extends HydraResizablePanelOptions {
    cssVariableName?: `--${string}`;
    separatorClassName?: string;
}

export interface ResizableDesignerSidebarState {
    width: number;
    layoutStyle: CSSProperties;
    separatorProps: HydraResizablePanelState['separatorProps'] & { className: string };
}

export const clampDesignerSidebarWidth = clampHydraPanelWidth;

/** Adaptador histórico: centraliza el comportamiento en HydraResizablePanel. */
export function useResizableDesignerSidebar(options: ResizableDesignerSidebarOptions): ResizableDesignerSidebarState {
    const state = useHydraResizablePanel(options);
    const cssVariableName = options.cssVariableName ?? '--hydra-designer-sidebar-width';
    const layoutStyle = useMemo(() => ({ [cssVariableName]: `${state.width}px` }) as CSSProperties, [cssVariableName, state.width]);
    return {
        width: state.width,
        layoutStyle,
        separatorProps: { ...state.separatorProps, className: options.separatorClassName ?? 'hydra-designer-sidebar-resizer' }
    };
}
