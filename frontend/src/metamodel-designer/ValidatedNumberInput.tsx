import { useId, useState } from 'react';

import { formatEditableNumber, validateRealNumber } from './numericValidation';
import type { RealNumberConstraints } from './numericValidation';

interface ValidatedNumberInputProps extends RealNumberConstraints {
    label: string;
    value: number;
    decimals?: number;
    disabled?: boolean;
    helpText?: string;
    onChange: (value: number) => void;
}

/**
 * Permite escribir un número y evita que valores vacíos, texto, infinitos o notación exponencial lleguen al estado de la aplicación.
 * Los valores válidos proceden siempre de la propiedad externa; solo se conserva localmente el texto inválido para poder explicárselo al usuario.
 *
 * @param props contiene el valor, los límites y la operación de actualización.
 * @returns campo numérico validado.
 */
export function ValidatedNumberInput(props: ValidatedNumberInputProps) {
    const inputId = useId();
    const [invalidText, setInvalidText] = useState<string | null>(null);
    const [error, setError] = useState('');
    const text = invalidText ?? formatEditableNumber(props.value, props.decimals);

    function update(rawValue: string): void {
        const validation = validateRealNumber(rawValue, { min: props.min, max: props.max, integer: props.integer });

        if(validation.valid === false) {
            setInvalidText(rawValue);
            setError(validation.message);
            return;
        }

        setInvalidText(null);
        setError('');
        props.onChange(validation.value);
    }

    function restoreInvalidValue(): void {
        if(invalidText === null) {
            return;
        }

        setInvalidText(null);
        setError('');
    }

    return (
        <label className="metamodel-validated-number-field" htmlFor={inputId}>
            <span>{props.label}</span>
            <input aria-label={props.label} aria-describedby={error === '' ? undefined : `${inputId}-error`} aria-invalid={error !== ''} disabled={props.disabled} id={inputId} inputMode={props.integer === true ? 'numeric' : 'decimal'} type="text" value={text} onBlur={restoreInvalidValue} onChange={event => update(event.target.value)} />
            {props.helpText !== undefined && error === '' && <small>{props.helpText}</small>}
            {error !== '' && <small className="metamodel-number-error" id={`${inputId}-error`} role="alert">{error}</small>}
        </label>
    );
}
