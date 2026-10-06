import { useId, type InputHTMLAttributes, type ReactNode } from 'react';
import { HydraTooltip } from './HydraTooltip';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import { HydraFieldOptionHelpContext } from './HydraFieldOptionHelpContext';
import { fieldGuidance, shouldEnrichFieldHelp } from './HydraFieldGuidance';

export interface HydraFormFieldOptionHelp { value: string; label: string; description: string; }

export interface HydraFormFieldProps {
    label: string;
    targetId: string;
    help?: string;
    example?: string;
    required?: boolean;
    error?: string;
    hint?: string;
    options?: readonly HydraFormFieldOptionHelp[];
    children: ReactNode;
    className?: string;
    width?: 'compact' | 'normal' | 'wide' | 'full';
    labelRowClassName?: string;
    requiredMarkerClassName?: string;
    tooltipTriggerClassName?: string;
}

/** Campo de formulario común con ayuda, validación y espaciado homogéneos. */
export function HydraFormField({ label, targetId, help, example, required = false, error, hint, options = [], children, className = '', width = 'normal', labelRowClassName = '', requiredMarkerClassName = '', tooltipTriggerClassName = '' }: HydraFormFieldProps) {
    const { t, language } = useHydraLanguage();
    const visibleLabel = t(label);
    const widthClass = width === 'normal' ? '' : ` hydra-field-${width}`;
    const guidance = help !== undefined && shouldEnrichFieldHelp(help) ? fieldGuidance(label, help, language, options.length > 0) : null;
    return <div data-hydra-field-id={targetId} data-hydra-error-location={error === undefined ? undefined : 'form'} className={`hydra-ui-form-field${error === undefined ? '' : ' invalid'}${widthClass}${className === '' ? '' : ` ${className}`}`}>
        <div className={`hydra-ui-form-label-row${labelRowClassName === '' ? '' : ` ${labelRowClassName}`}`}>
            <label htmlFor={targetId} title={visibleLabel}>{visibleLabel}</label>
            {required && <span aria-label={t("Required field")} className={`hydra-ui-required${requiredMarkerClassName === '' ? '' : ` ${requiredMarkerClassName}`}`}>*</span>}
            {help !== undefined && <HydraTooltip
                className="hydra-field-tooltip"
                label={`${t('Information about')} ${visibleLabel}`}
                title={`${t('About')} ${visibleLabel}`}
                triggerClassName={tooltipTriggerClassName}
                onOpenChange={open => { if (open) document.getElementById(targetId)?.focus(); }}
            >
                <p>{t(help)}</p>
                {guidance !== null && <section className="hydra-ui-field-guidance">
                    <div><strong>{t('How to choose')}</strong><p>{guidance.choose}</p></div>
                    <div><strong>{t('What changes')}</strong><p>{guidance.impact}</p></div>
                    {guidance.options !== undefined && <div><strong>{t('Use the current context')}</strong><p>{guidance.options}</p></div>}
                </section>}
                {example !== undefined && example !== '' && <div className="hydra-ui-tooltip-example"><span>{t('Example')}</span><code>{example}</code></div>}
                {options.length > 0 && <div className="hydra-ui-option-help"><strong>{t('Available values')}</strong><dl>{options.map(option => <div key={option.value}><dt>{t(option.label)}</dt><dd>{t(option.description)}</dd></div>)}</dl></div>}
            </HydraTooltip>}
        </div>
        <div className="hydra-ui-form-control"><HydraFieldOptionHelpContext.Provider value={options.length > 0}>{children}</HydraFieldOptionHelpContext.Provider></div>
        {hint !== undefined && error === undefined && <small className="hydra-ui-form-hint">{t(hint)}</small>}
        {error !== undefined && <small className="hydra-ui-form-error" id={`${targetId}-error`} role="alert"><strong>{t('Error in this field')}:</strong> {t(error)}</small>}
    </div>;
}

export interface HydraCheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'checked'> {
    label: string;
    checked: boolean;
    description?: string;
}

export function HydraCheckbox({ label, checked, description, className = '', id, 'aria-label': ariaLabel, 'aria-describedby': ariaDescribedBy, ...props }: HydraCheckboxProps) {
    const { t } = useHydraLanguage();
    const visibleLabel = t(label);
    const generatedId = useId().replaceAll(':', '');
    const inputId = id ?? `hydra-checkbox-${generatedId}`;
    const descriptionId = description === undefined ? undefined : `${inputId}-description`;
    const describedBy = [ariaDescribedBy, descriptionId].filter(Boolean).join(' ') || undefined;

    return <label className={`hydra-ui-checkbox${className === '' ? '' : ` ${className}`}`} htmlFor={inputId}>
        <input {...props} aria-describedby={describedBy} aria-label={ariaLabel === undefined ? visibleLabel : t(String(ariaLabel))} checked={checked} id={inputId} type="checkbox" />
        <span><strong>{visibleLabel}</strong>{description !== undefined && <small id={descriptionId}>{t(description)}</small>}</span>
    </label>;
}

export function HydraRequiredFieldsLegend() {
    const { t } = useHydraLanguage();
    return <p className="hydra-ui-required-legend"><span aria-hidden="true">*</span> {t('Required field')}</p>;
}
