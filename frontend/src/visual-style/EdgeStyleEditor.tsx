import { useState } from 'react';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';

import { CollapsibleStyleSection } from './CollapsibleStyleSection';
import { ColorEditor } from './ColorEditor';
import { FontFamilyOptions } from './NodeStyleEditor';
import type { VisualEdgeStyle } from './visualStyleTypes';


const LINE_STYLE_OPTIONS = [
    { value:'AUTO', label:'Automatic', description:'Uses the default style selected by the notation or representation.', category:'Automatic' },
    { value:'SOLID', label:'Solid', description:'Continuous line for ordinary connections.', category:'Explicit styles' },
    { value:'DASHED', label:'Dashed', description:'Segmented line often used for dependencies.', category:'Explicit styles' },
    { value:'DOTTED', label:'Dotted', description:'Dotted line for weak or informational links.', category:'Explicit styles' }
] as const;
const FONT_WEIGHT_OPTIONS = [
    { value:'400', label:'Normal', description:'Regular text weight.', category:'Standard' },
    { value:'500', label:'Medium', description:'Slightly emphasized text.', category:'Standard' },
    { value:'600', label:'Semibold', description:'Strong emphasis without full bold weight.', category:'Emphasis' },
    { value:'700', label:'Bold', description:'Maximum emphasis for labels.', category:'Emphasis' }
] as const;

interface EdgeStyleEditorProps {
    value: VisualEdgeStyle;
    onChange: (value: VisualEdgeStyle) => void;
}

/**
 * Edita una relación mediante secciones cerradas por defecto.
 */
export function EdgeStyleEditor({ value, onChange }: EdgeStyleEditorProps) {
    const [openSection, setOpenSection] = useState<string | null>(null);
    const toggle = (sectionId: string) => setOpenSection(current => current === sectionId ? null : sectionId);

    return (
        <section className="visual-style-editor">
            <div className="visual-style-heading"><h3>Style customization</h3><span>Open one section to edit it</span></div>
            <CollapsibleStyleSection description="Color, width and line pattern" id="edge-connection" onToggle={toggle} open={openSection === 'edge-connection'} title="Connection">
                <ColorEditor label="Connection color" value={value.strokeColor} onChange={strokeColor => onChange({ ...value, strokeColor })} />
                <div className="visual-style-number-grid">
                    <label>Connection width<input min={0.5} step={0.25} type="number" value={value.strokeWidth} onChange={event => onChange({ ...value, strokeWidth: Number(event.target.value) })} /></label>
                    <label>Line style<HydraAdaptiveSelect ariaLabel="Line style" value={value.lineStyle} options={LINE_STYLE_OPTIONS} onChange={lineStyle => onChange({ ...value, lineStyle: lineStyle as VisualEdgeStyle['lineStyle'] })} /></label>
                </div>
            </CollapsibleStyleSection>
            <CollapsibleStyleSection description="Label color, family, size and weight" id="edge-label" onToggle={toggle} open={openSection === 'edge-label'} title="Label and font">
                <ColorEditor label="Label color" value={value.textColor} onChange={textColor => onChange({ ...value, textColor })} />
                <label>Font family<input list="visual-font-families" value={value.fontFamily} onChange={event => onChange({ ...value, fontFamily: event.target.value })} /></label>
                <FontFamilyOptions />
                <div className="visual-style-number-grid">
                    <label>Font size<input min={7} step={1} type="number" value={value.fontSize} onChange={event => onChange({ ...value, fontSize: Number(event.target.value) })} /></label>
                    <label>Font weight<HydraAdaptiveSelect ariaLabel="Edge label font weight" value={String(value.fontWeight)} options={FONT_WEIGHT_OPTIONS} onChange={fontWeight => onChange({ ...value, fontWeight: Number(fontWeight) })} /></label>
                </div>
            </CollapsibleStyleSection>
        </section>
    );
}
