import { useState } from 'react';

import { hexToRgb, normalizeHexColor, rgbToHex } from './visualStyleUtils';

interface ColorEditorProps {
    label: string;
    value: string;
    onChange: (value: string) => void;
}

/**
 * Permite elegir un color mediante selector, hexadecimal o RGB.
 *
 * @param label contiene la etiqueta visible.
 * @param value contiene el color actual.
 * @param onChange informa del nuevo color.
 * @returns editor de color reutilizable.
 */
export function ColorEditor(props: ColorEditorProps) {
    const normalizedValue = normalizeHexColor(props.value, '#000000');
    return <ColorEditorSession key={normalizedValue} {...props} normalizedValue={normalizedValue} />;
}

function ColorEditorSession({ label, onChange, normalizedValue }: ColorEditorProps & { normalizedValue: string }) {
    const [hexText, setHexText] = useState(normalizedValue);
    const rgb = hexToRgb(normalizedValue) ?? { red: 0, green: 0, blue: 0 };


    /**
     * Confirma el texto hexadecimal.
     *
     * @returns no devuelve ningún valor.
     */
    function commitHex(): void {
        const normalized = normalizeHexColor(hexText, '');

        if (normalized === '') {
            setHexText(normalizedValue);
            return;
        }

        setHexText(normalized);
        onChange(normalized);
    }

    /**
     * Modifica un componente RGB.
     *
     * @param component identifica el componente.
     * @param componentValue contiene el nuevo valor.
     * @returns no devuelve ningún valor.
     */
    function updateRgb(component: 'RED' | 'GREEN' | 'BLUE', componentValue: number): void {
        let red = rgb.red;
        let green = rgb.green;
        let blue = rgb.blue;

        if (component === 'RED') {
            red = componentValue;
        } else if (component === 'GREEN') {
            green = componentValue;
        } else {
            blue = componentValue;
        }

        onChange(rgbToHex(red, green, blue));
    }

    return (
        <fieldset className="visual-color-editor">
            <legend>{label}</legend>
            <div className="visual-color-main-row">
                <input aria-label={`${label} palette`} type="color" value={normalizedValue} onChange={event => onChange(event.target.value.toUpperCase())} />
                <input aria-label={`${label} hexadecimal`} value={hexText} onBlur={commitHex} onChange={event => setHexText(event.target.value)} onKeyDown={event => {
                    if (event.key === 'Enter') {
                        event.preventDefault();
                        commitHex();
                    }
                }} />
            </div>
            <div className="visual-color-rgb-row">
                <label>R<input max={255} min={0} type="number" value={rgb.red} onChange={event => updateRgb('RED', Number(event.target.value))} /></label>
                <label>G<input max={255} min={0} type="number" value={rgb.green} onChange={event => updateRgb('GREEN', Number(event.target.value))} /></label>
                <label>B<input max={255} min={0} type="number" value={rgb.blue} onChange={event => updateRgb('BLUE', Number(event.target.value))} /></label>
            </div>
        </fieldset>
    );
}
