import { useState } from 'react';
import { HydraButton, HydraWindowControls } from '../design-system';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import type { DiagramSelectionArrangement } from '../designer-ui/diagramSelection';

interface Props {
    elementCount:number;
    relationCount:number;
    disabled:boolean;
    canPreviewCode:boolean;
    onArrange:(arrangement:DiagramSelectionArrangement)=>void;
    onElkArrange:()=>void;
    onPreviewCode:()=>void;
    onRotate:(deltaDegrees:number)=>void;
    onClear:()=>void;
    onDelete:()=>void;
}

/** Acciones contextuales del editor de modelos para la selección activa. */
export function EditorRuntimeSelectionActions(props:Props) {
    const { t } = useHydraLanguage();
    const [customRotation,setCustomRotation]=useState('45');
    const parsedRotation=Number(customRotation.replace(',','.'));
    const validRotation=Number.isFinite(parsedRotation)&&parsedRotation!==0&&Math.abs(parsedRotation)<=3600;
    const canArrange=props.elementCount>=2;
    const canDistribute=props.elementCount>=3;
    return <section aria-label={t('Selected model elements')} className="editor-runtime-selection-actions" role="dialog">
        <header>
            <div><strong>{props.elementCount} {t('element(s) selected')}</strong><span>{props.relationCount} {t('internal relation(s)')}</span></div>
            <HydraWindowControls closeLabel={t('Clear area selection')} onClose={props.onClear}/>
        </header>
        <p>{t('Use the selected model elements without leaving the canvas. Layout changes affect only the active selection and preserve its containers.')}</p>
        <div className="editor-runtime-selection-actions-grid">
            <HydraButton size="small" disabled={props.disabled||!props.canPreviewCode} onClick={props.onPreviewCode}>{t('Preview code')}</HydraButton>
            <HydraButton size="small" disabled={props.disabled||!canArrange} onClick={props.onElkArrange}>{t('ELK-style selection')}</HydraButton>
            <HydraButton size="small" disabled={props.disabled||!canArrange} onClick={()=>props.onArrange('ALIGN_LEFT')}>{t('Vertical line')}</HydraButton>
            <HydraButton size="small" disabled={props.disabled||!canArrange} onClick={()=>props.onArrange('ALIGN_TOP')}>{t('Horizontal line')}</HydraButton>
            <HydraButton size="small" disabled={props.disabled||!canDistribute} onClick={()=>props.onArrange('DISTRIBUTE_HORIZONTAL')}>{t('Distribute horizontally')}</HydraButton>
            <HydraButton size="small" disabled={props.disabled||!canDistribute} onClick={()=>props.onArrange('DISTRIBUTE_VERTICAL')}>{t('Distribute vertically')}</HydraButton>
        </div>
        <fieldset className="editor-runtime-rotation-actions">
            <legend>{t('Rotate selection')}</legend>
            <div className="editor-runtime-rotation-presets">
                {[90,180,270].map(degrees=><HydraButton key={degrees} size="small" disabled={props.disabled} onClick={()=>props.onRotate(degrees)}>{degrees}°</HydraButton>)}
            </div>
            <label><span>{t('Custom degrees')}</span><input aria-label={t('Custom rotation degrees')} type="number" min="-3600" max="3600" step="1" value={customRotation} onChange={event=>setCustomRotation(event.target.value)}/></label>
            <HydraButton size="small" disabled={props.disabled||!validRotation} onClick={()=>props.onRotate(parsedRotation)}>{t('Rotate')}</HydraButton>
            <small>{t('Positive values rotate clockwise; negative values rotate counter-clockwise. The angle is normalized and saved with the diagram.')}</small>
        </fieldset>
        <div className="editor-runtime-selection-footer"><HydraButton size="small" variant="danger" disabled={props.disabled} onClick={props.onDelete}>{t('Delete selected')}</HydraButton></div>
    </section>;
}
