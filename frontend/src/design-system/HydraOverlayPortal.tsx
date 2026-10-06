import { forwardRef, type CSSProperties, type HTMLAttributes, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

export type HydraOverlayLayer = 'FLOATING' | 'DROPDOWN' | 'MODAL_AUXILIARY' | 'TOOLTIP';

export interface HydraOverlayPortalProps extends Omit<HTMLAttributes<HTMLDivElement>, 'children'> {
    children: ReactNode;
    layer?: HydraOverlayLayer;
    style?: CSSProperties;
}

/**
 * Portal único para chrome flotante de HYDRA.
 *
 * Los overlays salen de contenedores con overflow y comparten una jerarquía de
 * capas semántica. Así un menú abierto dentro de un modal no puede quedar por
 * detrás de su propio formulario ni depender del z-index accidental del módulo.
 */
export const HydraOverlayPortal = forwardRef<HTMLDivElement, HydraOverlayPortalProps>(function HydraOverlayPortal(
    { children, className = '', layer = 'FLOATING', ...rest },
    ref,
) {
    if (typeof document === 'undefined') return null;
    const layerClass = `hydra-overlay-layer-${layer.toLowerCase().replace('_', '-')}`;
    return createPortal(
        <div {...rest} className={`hydra-overlay-portal ${layerClass}${className === '' ? '' : ` ${className}`}`} data-hydra-overlay-layer={layer} ref={ref}>{children}</div>,
        document.body,
    );
});
