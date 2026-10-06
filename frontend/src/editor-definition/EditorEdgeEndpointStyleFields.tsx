import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { DesignerHelpedField } from '../designer-ui/DesignerFieldHelp';
import { HYDRA_CONNECTION_TERMINATOR_DEFINITIONS } from '../designer-ui/HydraConnectionVisual';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import type { EditorEdgeNotationStyle } from './editorNotationStyle';

const TERMINATOR_OPTIONS = HYDRA_CONNECTION_TERMINATOR_DEFINITIONS;

interface Props {
    endpoint:'SOURCE'|'TARGET';
    style:EditorEdgeNotationStyle;
    update:(key:string,value:string|number|boolean)=>void;
}

/** Endpoint editor shared by both ends of an editor-definition connection. */
export function EditorEdgeEndpointStyleFields({ endpoint, style, update }: Props) {
    const { t } = useHydraLanguage();
    const source=endpoint==='SOURCE';
    const prefix=source?'source':'target';
    const title=t(source?'Source end':'Target end');
    const decoration=source?style.sourceDecoration:style.targetDecoration;
    const fill=source?style.sourceDecorationFillColor:style.targetDecorationFillColor;
    const border=source?style.sourceDecorationBorderColor:style.targetDecorationBorderColor;
    const borderWidth=source?style.sourceDecorationBorderWidth:style.targetDecorationBorderWidth;
    const size=source?style.sourceDecorationSize:style.targetDecorationSize;
    return <section className="editor-edge-end-style" aria-label={title}>
        <header><div><strong>{title}</strong><small>{t(decoration==='NONE'?'No marker at this end.':'Shape, fill and outline are independent from the line.')}</small></div></header>
        <div className="hydra-designer-fields">
            <DesignerHelpedField className="hydra-designer-wide" label="End shape" targetId={`${prefix}-decoration`} help="Choose the geometric marker at this end. Source and target are independent, and both ends may use No end marker." example={source?'Open diamond':'Filled arrow'} options={TERMINATOR_OPTIONS.map(({value,label,description})=>({value,label,description}))}>
                <HydraAdaptiveSelect id={`${prefix}-decoration`} ariaLabel={`${title} shape`} value={decoration} options={TERMINATOR_OPTIONS} onChange={value=>update(`${prefix}Decoration`,value)} />
            </DesignerHelpedField>
            {decoration!=='NONE'&&<>
                <DesignerHelpedField label="Marker fill" targetId={`${prefix}-decoration-fill`} help="Interior color of the endpoint marker. Open shapes can use the canvas or another contrasting color." example="#FFFFFF"><input id={`${prefix}-decoration-fill`} type="color" value={fill} onChange={event=>update(`${prefix}DecorationFillColor`,event.target.value)} /></DesignerHelpedField>
                <DesignerHelpedField label="Marker border" targetId={`${prefix}-decoration-border`} help="Outline color of the endpoint marker. It may inherit the visual language of the line or intentionally contrast with it." example="#334155"><input id={`${prefix}-decoration-border`} type="color" value={border} onChange={event=>update(`${prefix}DecorationBorderColor`,event.target.value)} /></DesignerHelpedField>
                <DesignerHelpedField label="Marker border width" targetId={`${prefix}-decoration-border-width`} help="Thickness of the endpoint outline. Use 0 for a marker without a separate outline." example="1.5"><input id={`${prefix}-decoration-border-width`} type="number" min={0} max={8} step={0.5} value={borderWidth} onChange={event=>update(`${prefix}DecorationBorderWidth`,Math.max(0,Math.min(8,Number(event.target.value)||0)))} /></DesignerHelpedField>
                <DesignerHelpedField label="Marker size" targetId={`${prefix}-decoration-size`} help="Scales only this endpoint marker without changing the connection line width." example="1"><input id={`${prefix}-decoration-size`} type="number" min={0.5} max={2.5} step={0.1} value={size} onChange={event=>update(`${prefix}DecorationSize`,Math.max(.5,Math.min(2.5,Number(event.target.value)||1)))} /></DesignerHelpedField>
            </>}
        </div>
    </section>;
}
