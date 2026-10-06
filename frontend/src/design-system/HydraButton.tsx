import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { useHydraLanguage } from '../i18n/useHydraLanguage';

export type HydraButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'quiet';
export type HydraButtonSize = 'small' | 'medium' | 'large';

export interface HydraButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
    variant?: HydraButtonVariant;
    size?: HydraButtonSize;
    icon?: ReactNode;
    loading?: boolean;
    compact?: boolean;
}

/** Botón común de HYDRA con variantes, tamaños y estados homogéneos. */
export const HydraButton = forwardRef<HTMLButtonElement, HydraButtonProps>(function HydraButton({
    variant = 'secondary',
    size = 'medium',
    icon,
    loading = false,
    compact = false,
    className = '',
    children,
    disabled,
    type = 'button',
    ...props
}: HydraButtonProps, ref) {
    const { t } = useHydraLanguage();
    const visibleChildren = typeof children === 'string' ? t(children) : children;
    const hasSimpleLabel = typeof visibleChildren === 'string' || typeof visibleChildren === 'number';
    const classes = [
        'hydra-ui-button',
        `variant-${variant}`,
        `size-${size}`,
        compact ? 'compact' : '',
        className
    ].filter(Boolean).join(' ');

    return <button
        {...props}
        ref={ref}
        aria-busy={loading || undefined}
        className={classes}
        disabled={disabled || loading}
        type={type}
    >
        {loading && <span aria-hidden="true" className="hydra-ui-button-spinner" />}
        {!loading && icon !== undefined && <span aria-hidden="true" className="hydra-ui-button-icon">{icon}</span>}
        {visibleChildren !== undefined && (hasSimpleLabel
            ? <span className="hydra-ui-button-label">{visibleChildren}</span>
            : visibleChildren)}
    </button>;
});
