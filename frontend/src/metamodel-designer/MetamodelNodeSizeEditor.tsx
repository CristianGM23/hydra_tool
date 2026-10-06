import { useState } from 'react';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';

import { ValidatedNumberInput } from './ValidatedNumberInput';
import { pixelsToUnit, unitToPixels } from './nodeSizeUtils';
import type { MetamodelSizeUnit } from './nodeSizeUtils';

interface MetamodelNodeSizeEditorProps {
    elementType: string;
    width: number;
    height: number;
    minimumWidth: number;
    minimumHeight: number;
    onChange: (width: number, height: number) => void;
}

/**
 * Ajusta la anchura y altura de una figura del metamodelo en píxeles o centímetros.
 * El diagrama conserva siempre los valores finales en píxeles para mantener compatibilidad con React Flow y los archivos existentes.
 */
export function MetamodelNodeSizeEditor(props: MetamodelNodeSizeEditorProps) {
    const [unit, setUnit] = useState<MetamodelSizeUnit>('PX');
    const width = pixelsToUnit(props.width, unit);
    const height = pixelsToUnit(props.height, unit);
    const minimumWidth = pixelsToUnit(props.minimumWidth, unit);
    const minimumHeight = pixelsToUnit(props.minimumHeight, unit);

    return (
        <section className="metamodel-geometry-editor metamodel-node-size-editor">
            <div className="metamodel-geometry-heading">
                <div><h3>Element size</h3><span>{props.elementType} dimensions</span></div>
                <label>Unit<HydraAdaptiveSelect ariaLabel="Size unit" value={unit} options={[
                    { value: 'PX', label: 'Pixels', description: 'Uses exact canvas pixels and matches the values persisted by React Flow.', category: 'Screen units' },
                    { value: 'CM', label: 'Centimetres', description: 'Shows a physical-size approximation while HYDRA continues storing pixels internally.', category: 'Physical units' }
                ]} onChange={next => setUnit(next as MetamodelSizeUnit)} /></label>
            </div>
            <div className="metamodel-geometry-number-grid">
                <ValidatedNumberInput decimals={unit === 'CM' ? 3 : 2} helpText={`Minimum ${minimumWidth.toFixed(unit === 'CM' ? 2 : 0)} ${unit.toLowerCase()}`} label={`Width (${unit.toLowerCase()})`} min={minimumWidth} value={width} onChange={value => props.onChange(unitToPixels(value, unit), props.height)} />
                <ValidatedNumberInput decimals={unit === 'CM' ? 3 : 2} helpText={`Minimum ${minimumHeight.toFixed(unit === 'CM' ? 2 : 0)} ${unit.toLowerCase()}`} label={`Height (${unit.toLowerCase()})`} min={minimumHeight} value={height} onChange={value => props.onChange(props.width, unitToPixels(value, unit))} />
            </div>
            <p className="metamodel-field-help">The selected unit only affects editing. The stored diagram uses pixels so existing layouts remain compatible.</p>
        </section>
    );
}
