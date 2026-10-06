import type { ReactNode } from 'react';
import { HydraFormField, HydraRequiredFieldsLegend } from '../design-system';
import './form-field-help.css';

export interface FormFieldOptionHelp { value: string; label: string; description: string; }

interface FormFieldHelpProps {
    label: string;
    targetId: string;
    help: string;
    example?: string;
    required?: boolean;
    children: ReactNode;
    className?: string;
    options?: FormFieldOptionHelp[];
}

/** Adaptador histórico: los formularios generales usan HydraFormField. */
export function FormFieldHelp({ label, targetId, help, example = '', required = false, children, className = '', options = [] }: FormFieldHelpProps) {
    return <HydraFormField
        className={`app-helped-field${className === '' ? '' : ` ${className}`}`}
        example={example}
        help={help}
        label={label}
        options={options}
        required={required}
        targetId={targetId}
    >{children}</HydraFormField>;
}

export function RequiredFieldsLegend() {
    return <div className="app-required-legend"><HydraRequiredFieldsLegend /></div>;
}
