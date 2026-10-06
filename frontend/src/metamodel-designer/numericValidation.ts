/**
 * Configura las restricciones aplicadas a un número escrito por el usuario.
 */
export interface RealNumberConstraints {
    min?: number;
    max?: number;
    integer?: boolean;
}

/**
 * Contiene el resultado de validar un número introducido como texto.
 */
export type RealNumberValidation = { valid: true; value: number } | { valid: false; message: string };

/**
 * Valida números naturales o reales sin aceptar notación exponencial, infinitos ni texto adicional.
 * Se admite punto o coma como separador decimal.
 *
 * @param text contiene el valor escrito.
 * @param constraints contiene límites opcionales.
 * @returns resultado de la validación.
 */
export function validateRealNumber(text: string, constraints: RealNumberConstraints = {}): RealNumberValidation {
    const normalizedText = text.trim().replace(',', '.');
    const realPattern = /^-?(?:\d+(?:\.\d+)?|\.\d+)$/;
    const naturalPattern = /^\d+$/;

    if(normalizedText === '') {
        return { valid: false, message: 'Enter a numeric value.' };
    }

    if(constraints.integer === true ? !naturalPattern.test(normalizedText) : !realPattern.test(normalizedText)) {
        return { valid: false, message: constraints.integer === true ? 'Enter a natural number.' : 'Enter a natural or real number.' };
    }

    const value = Number(normalizedText);

    if(!Number.isFinite(value)) {
        return { valid: false, message: 'Enter a finite number.' };
    }

    if(constraints.min !== undefined && value < constraints.min) {
        return { valid: false, message: `The value must be at least ${constraints.min}.` };
    }

    if(constraints.max !== undefined && value > constraints.max) {
        return { valid: false, message: `The value must be at most ${constraints.max}.` };
    }

    return { valid: true, value };
}

/**
 * Formatea un número para mostrarlo en un campo editable sin introducir ruido decimal.
 *
 * @param value contiene el número.
 * @param decimals contiene el máximo de decimales.
 * @returns texto legible.
 */
export function formatEditableNumber(value: number, decimals = 3): string {
    if(!Number.isFinite(value)) {
        return '0';
    }

    return Number(value.toFixed(decimals)).toString();
}
