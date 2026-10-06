import { DesignerHelpedField } from './DesignerFieldHelp';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { HydraConnectionStylePreview } from './HydraConnectionStylePreview';
import { useHydraLanguage } from '../i18n';
import { HydraSelect } from '../design-system';
import {
    DEFAULT_HYDRA_SOURCE_PORT, DEFAULT_HYDRA_TARGET_PORT, HYDRA_CONNECTION_TERMINATOR_DEFINITIONS,
    HYDRA_SOURCE_PORT_IDS, HYDRA_TARGET_PORT_IDS
} from './HydraConnectionVisual';
import type {
    HydraConnectionLineStyle, HydraConnectionRouteStyle, HydraConnectionTerminator, HydraPersistedConnectionAppearance
} from './HydraConnectionVisual';

export interface HydraEditableConnectionVisual extends HydraPersistedConnectionAppearance {
    sourceHandle?: string;
    targetHandle?: string;
    label?: string;
    note?: string;
}

interface Props { value:HydraEditableConnectionVisual; onChange:(next:HydraEditableConnectionVisual)=>void; labelEditable?:boolean; }

/** High-level connection editor shared by Events, Event Flow and Gamification. */
export function HydraConnectionVisualEditor({value,onChange,labelEditable=true}:Props) {
    const {t}=useHydraLanguage();
    const update=<K extends keyof HydraEditableConnectionVisual>(key:K,next:HydraEditableConnectionVisual[K])=>onChange({...value,[key]:next});
    return <section className="hydra-connection-visual-editor">
        <header className="hydra-connection-visual-heading"><div><h4>{t('Connection appearance')}</h4><p>{t('Configure the line and both endpoints independently. Markers are optional at either end and do not change the semantic relationship.')}</p></div><span>{t('Visual only')}</span></header>
        <div className="hydra-connection-visual-intro">
            <HydraConnectionStylePreview value={{...value, connectionLabel:value.label}} label="Live connection preview" />
            <div className="hydra-connection-visual-guide"><strong>{t('Start with the meaning, then refine the look')}</strong><p>{t('Choose the route and line first. Add endpoint markers only when they communicate useful meaning. A connection may remain completely unmarked at both ends.')}</p></div>
        </div>
        <div className="hydra-connection-style-summary" aria-label={t('Connection style summary')}>
            <span><strong>{t('Route')}</strong>{t(routeLabel(value.routeStyle??'ORTHOGONAL'))}</span>
            <span><strong>{t('Line')}</strong>{t(lineLabel(value.lineStyle??'SOLID'))} · {value.lineWidth??2} {t('pixels')}</span>
            <span><strong>{t('Source end')}</strong>{t(terminatorLabel(value.sourceTerminator??'NONE'))}</span>
            <span><strong>{t('Target end')}</strong>{t(terminatorLabel(value.targetTerminator??'ARROW_CLOSED'))}</span>
        </div>
        <div className="hydra-designer-fields">
            {labelEditable&&<DesignerHelpedField label="Connection label" targetId="hydra-connection-label" help="Visible text drawn over the connection. It explains the link without changing its source, target or model relation." example="Validation succeeded"><input id="hydra-connection-label" value={value.label??''} onChange={event=>update('label',event.target.value)}/></DesignerHelpedField>}
            <DesignerHelpedField label="Source port" targetId="hydra-connection-source-port" help="Point on the source element where the connection starts. During resize the port follows the element live, using the same geometry behavior as the Model Editor." example="Right · source"><HydraSelect id="hydra-connection-source-port" ariaLabel="Source port" value={value.sourceHandle||DEFAULT_HYDRA_SOURCE_PORT} options={HYDRA_SOURCE_PORT_IDS.map(id=>({value:id,label:portLabel(id,t),description:'Anchor point on the source element.'}))} onChange={next=>update('sourceHandle',next)} /></DesignerHelpedField>
            <DesignerHelpedField label="Target port" targetId="hydra-connection-target-port" help="Point on the target element where the connection ends. Choose another side when the diagram reads more clearly from a different direction." example="Left · target"><HydraSelect id="hydra-connection-target-port" ariaLabel="Target port" value={value.targetHandle||DEFAULT_HYDRA_TARGET_PORT} options={HYDRA_TARGET_PORT_IDS.map(id=>({value:id,label:portLabel(id,t),description:'Anchor point on the target element.'}))} onChange={next=>update('targetHandle',next)} /></DesignerHelpedField>
            <DesignerHelpedField label="Route" targetId="hydra-connection-route" help="Controls how the connection travels between elements. Orthogonal is easiest to scan in structured diagrams; Smooth and Straight are useful for lighter views." example="Orthogonal"><HydraSelect id="hydra-connection-route" ariaLabel="Route" value={value.routeStyle??'ORTHOGONAL'} options={[{value:'ORTHOGONAL',label:'Orthogonal',description:'Uses right-angle segments for structured diagrams.'},{value:'SMOOTH',label:'Smooth curve',description:'Uses a curved route for lighter visual layouts.'},{value:'STRAIGHT',label:'Straight',description:'Connects source and target with one direct segment.'}]} onChange={next=>update('routeStyle',next as HydraConnectionRouteStyle)} /></DesignerHelpedField>
            <DesignerHelpedField label="Line pattern" targetId="hydra-connection-line" help="Choose the visual rhythm of the connection. Hidden keeps the semantic relation while removing the visible line." example="Solid"><HydraSelect id="hydra-connection-line" ariaLabel="Line pattern" value={value.lineStyle??'SOLID'} options={(['SOLID','DASHED','DOTTED','LONG_DASH','DASH_DOT','NONE'] as HydraConnectionLineStyle[]).map(item=>({value:item,label:lineLabel(item),description:item==='NONE'?'Keeps the semantic connection while hiding the visible line.':'Visual line pattern used for this connection.'}))} onChange={next=>update('lineStyle',next as HydraConnectionLineStyle)} /></DesignerHelpedField>
            <DesignerHelpedField label="Line color" targetId="hydra-connection-color" help="Main connection color. Endpoint markers can inherit it or use their own fill and border colors." example="#334155"><input id="hydra-connection-color" type="color" value={value.lineColor??'#334155'} onChange={event=>update('lineColor',event.target.value)}/></DesignerHelpedField>
            <DesignerHelpedField label="Line width" targetId="hydra-connection-width" help="Visible thickness of the connection. Endpoint sizes are controlled separately." example="2"><input id="hydra-connection-width" type="number" min={0.5} max={16} step={0.5} value={value.lineWidth??2} onChange={event=>update('lineWidth',clamp(Number(event.target.value),.5,16,2))}/></DesignerHelpedField>
            <DesignerHelpedField label="Line opacity" targetId="hydra-connection-opacity" help="Transparency of the connection from 0.1 to 1. Use lower values to de-emphasize secondary links without hiding them." example="1"><input id="hydra-connection-opacity" type="number" min={0.1} max={1} step={0.05} value={value.lineOpacity??1} onChange={event=>update('lineOpacity',clamp(Number(event.target.value),.1,1,1))}/></DesignerHelpedField>
            <DesignerHelpedField label="Line border color" targetId="hydra-connection-border-color" help="Optional outline behind the connection line. It improves contrast over busy diagrams and can be disabled with border width 0." example="#FFFFFF"><input id="hydra-connection-border-color" type="color" value={value.lineBorderColor??'#FFFFFF'} onChange={event=>update('lineBorderColor',event.target.value)}/></DesignerHelpedField>
            <DesignerHelpedField label="Line border width" targetId="hydra-connection-border-width" help="Extra outline around the connection line. Use 0 for no separate border." example="0"><input id="hydra-connection-border-width" type="number" min={0} max={8} step={0.5} value={value.lineBorderWidth??0} onChange={event=>update('lineBorderWidth',clamp(Number(event.target.value),0,8,0))}/></DesignerHelpedField>
        </div>
        <div className="hydra-connection-end-grid"><ConnectionEndEditor side="source" value={value} onChange={onChange}/><ConnectionEndEditor side="target" value={value} onChange={onChange}/></div>
        <DesignerHelpedField className="hydra-designer-wide" label="Connection note" targetId="hydra-connection-note" help="Optional explanation stored with the diagram. It documents why a visual style or route was chosen without modifying the executable semantic relation." example="Highlighted after the quality gate."><textarea id="hydra-connection-note" value={value.note??''} onChange={event=>update('note',event.target.value)}/></DesignerHelpedField>
    </section>;
}

function ConnectionEndEditor({side,value,onChange}:{side:'source'|'target';value:HydraEditableConnectionVisual;onChange:(next:HydraEditableConnectionVisual)=>void}) {
    const {t}=useHydraLanguage(),source=side==='source',title=source?'Source end':'Target end',prefix=source?'sourceTerminator':'targetTerminator';
    const marker=(source?value.sourceTerminator:value.targetTerminator)??(source?'NONE':'ARROW_CLOSED');
    const fill=(source?value.sourceTerminatorFillColor:value.targetTerminatorFillColor)??value.lineColor??'#334155';
    const border=(source?value.sourceTerminatorBorderColor:value.targetTerminatorBorderColor)??value.lineColor??'#334155';
    const borderWidth=(source?value.sourceTerminatorBorderWidth:value.targetTerminatorBorderWidth)??1.5,size=(source?value.sourceTerminatorSize:value.targetTerminatorSize)??1;
    const patch=(name:string,next:string|number)=>onChange({...value,[name]:next});
    return <section className="hydra-connection-end-card"><header><div><h5>{t(title)}</h5><p>{t(source?'Style the point where the connection leaves the source element.':'Style the point where the connection reaches the target element.')}</p></div><span>{marker==='NONE'?t('No marker'):t('Marker')}</span></header>
        <DesignerHelpedField label="End shape" targetId={`hydra-${side}-terminator`} help="Choose the geometric marker for this end. None is a first-class option, so connections may have no markers at either end." example="Open diamond"><HydraAdaptiveSelect id={`hydra-${side}-terminator`} ariaLabel={t('End shape')} value={marker} options={HYDRA_CONNECTION_TERMINATOR_DEFINITIONS.map(item=>({value:item.value,label:t(item.label),description:t(item.description),category:t(item.category)}))} onChange={next=>patch(prefix,next as HydraConnectionTerminator)} /></DesignerHelpedField>
        {marker!=='NONE'&&<><DesignerHelpedField label="Marker fill" targetId={`hydra-${side}-terminator-fill`} help="Interior color of filled markers. Open markers keep their interior transparent." example="#334155"><input id={`hydra-${side}-terminator-fill`} type="color" value={fill} onChange={event=>patch(`${prefix}FillColor`,event.target.value)}/></DesignerHelpedField>
        <DesignerHelpedField label="Marker border" targetId={`hydra-${side}-terminator-border`} help="Outline color used independently from the connection line." example="#334155"><input id={`hydra-${side}-terminator-border`} type="color" value={border} onChange={event=>patch(`${prefix}BorderColor`,event.target.value)}/></DesignerHelpedField>
        <DesignerHelpedField label="Marker border width" targetId={`hydra-${side}-terminator-border-width`} help="Outline thickness of the endpoint marker. It may be lighter or stronger than the connection itself." example="1.5"><input id={`hydra-${side}-terminator-border-width`} type="number" min={0} max={8} step={0.5} value={borderWidth} onChange={event=>patch(`${prefix}BorderWidth`,clamp(Number(event.target.value),0,8,1.5))}/></DesignerHelpedField>
        <DesignerHelpedField label="Marker size" targetId={`hydra-${side}-terminator-size`} help="Relative marker size. 1 keeps the standard size; smaller or larger values adapt the endpoint to the visual hierarchy." example="1"><input id={`hydra-${side}-terminator-size`} type="number" min={0.5} max={2.5} step={0.1} value={size} onChange={event=>patch(`${prefix}Size`,clamp(Number(event.target.value),.5,2.5,1))}/></DesignerHelpedField></>}
    </section>;
}

function portLabel(id:string,t:(value:string)=>string):string{const [side]=id.split('-');return `${t(side.charAt(0).toUpperCase()+side.slice(1))} · ${t(id.endsWith('source')?'source':'target')}`;}
function routeLabel(value:HydraConnectionRouteStyle):string{return value==='SMOOTH'?'Smooth curve':value==='STRAIGHT'?'Straight':'Orthogonal';}
function lineLabel(value:HydraConnectionLineStyle):string{return value==='DASHED'?'Dashed':value==='DOTTED'?'Dotted':value==='LONG_DASH'?'Long dash':value==='DASH_DOT'?'Dash dot':value==='NONE'?'Hidden line':'Solid';}
function terminatorLabel(value:HydraConnectionTerminator):string{return HYDRA_CONNECTION_TERMINATOR_DEFINITIONS.find(item=>item.value===value)?.label??value;}
function clamp(value:number,min:number,max:number,fallback:number):number{return Number.isFinite(value)?Math.max(min,Math.min(max,value)):fallback;}
