import { useState } from 'react';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';

import { CollapsibleStyleSection } from './CollapsibleStyleSection';
import { ColorEditor } from './ColorEditor';
import type { VisualNodeStyle } from './visualStyleTypes';


const FONT_WEIGHT_OPTIONS = [
    { value:'400', label:'Normal', description:'Regular text weight.', category:'Standard' },
    { value:'500', label:'Medium', description:'Slightly emphasized text.', category:'Standard' },
    { value:'600', label:'Semibold', description:'Strong emphasis without full bold weight.', category:'Emphasis' },
    { value:'700', label:'Bold', description:'Maximum emphasis for node text.', category:'Emphasis' }
] as const;

interface NodeStyleEditorProps {
    value: VisualNodeStyle;
    onChange: (value: VisualNodeStyle) => void;
}

/**
 * Edita un estilo de figura mediante secciones cerradas por defecto.
 */
export function NodeStyleEditor({ value, onChange }: NodeStyleEditorProps) {
    const [openSection, setOpenSection] = useState<string | null>(null);
    const toggle = (sectionId: string) => setOpenSection(current => current === sectionId ? null : sectionId);

    return (
        <section className="visual-style-editor">
            <div className="visual-style-heading"><h3>Style customization</h3><span>Open one section to edit it</span></div>
            <CollapsibleStyleSection description="Body and header background" id="node-fill" onToggle={toggle} open={openSection === 'node-fill'} title="Fill colors">
                <ColorEditor label="Body color" value={value.fillColor} onChange={fillColor => onChange({ ...value, fillColor })} />
                <ColorEditor label="Header color" value={value.headerColor} onChange={headerColor => onChange({ ...value, headerColor })} />
            </CollapsibleStyleSection>
            <CollapsibleStyleSection description="Color, width and corner radius" id="node-border" onToggle={toggle} open={openSection === 'node-border'} title="Border">
                <ColorEditor label="Border color" value={value.strokeColor} onChange={strokeColor => onChange({ ...value, strokeColor })} />
                <div className="visual-style-number-grid">
                    <label>Border width<input min={0.5} step={0.5} type="number" value={value.strokeWidth} onChange={event => onChange({ ...value, strokeWidth: Number(event.target.value) })} /></label>
                    <label>Corner radius<input min={0} step={1} type="number" value={value.borderRadius} onChange={event => onChange({ ...value, borderRadius: Number(event.target.value) })} /></label>
                </div>
            </CollapsibleStyleSection>
            <CollapsibleStyleSection description="Text color, family, size and weight" id="node-font" onToggle={toggle} open={openSection === 'node-font'} title="Text and font">
                <ColorEditor label="Text color" value={value.textColor} onChange={textColor => onChange({ ...value, textColor })} />
                <label>Font family<input list="visual-font-families" value={value.fontFamily} onChange={event => onChange({ ...value, fontFamily: event.target.value })} /></label>
                <FontFamilyOptions />
                <div className="visual-style-number-grid">
                    <label>Font size<input min={7} step={1} type="number" value={value.fontSize} onChange={event => onChange({ ...value, fontSize: Number(event.target.value) })} /></label>
                    <label>Font weight<HydraAdaptiveSelect ariaLabel="Node font weight" value={String(value.fontWeight)} options={FONT_WEIGHT_OPTIONS} onChange={fontWeight => onChange({ ...value, fontWeight: Number(fontWeight) })} /></label>
                </div>
            </CollapsibleStyleSection>
        </section>
    );
}

/**
 * Define fuentes habituales y permite escribir una fuente distinta.
 */
export function FontFamilyOptions() {
    return (
        <datalist id="visual-font-families">
            <option value="Arial" />
            <option value="Calibri" />
            <option value="Cambria" />
            <option value="Consolas" />
            <option value="Courier New" />
            <option value="Georgia" />
            <option value="Inter" />
            <option value="Roboto" />
            <option value="Segoe UI" />
            <option value="Tahoma" />
            <option value="Times New Roman" />
            <option value="Verdana" />
        </datalist>
    );
}
