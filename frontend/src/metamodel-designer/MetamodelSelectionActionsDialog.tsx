import { HydraButton } from '../design-system/HydraButton';
import { HydraWindowControls } from '../design-system';
import type { CanvasSelectionArrangement } from './canvasSelection';

interface MetamodelSelectionActionsDialogProps {
    elementCount: number;
    relationCount: number;
    canDistribute: boolean;
    onArrange: (arrangement: CanvasSelectionArrangement) => void;
    onClear: () => void;
    onDeleteElements: () => void;
    onDeleteRelations: () => void;
}

/**
 * Muestra acciones útiles para una selección realizada mediante una zona.
 */
export function MetamodelSelectionActionsDialog(props: MetamodelSelectionActionsDialogProps) {
    return (
        <section aria-label="Selected metamodel elements" className="metamodel-selection-actions" role="dialog">
            <header>
                <div>
                    <strong>{props.elementCount} element(s) selected</strong>
                    <span>{props.relationCount} internal relation(s)</span>
                </div>
                <HydraWindowControls closeLabel="Clear area selection" onClose={props.onClear} />
            </header>
            <p>Alignment keeps a configurable margin. Tree layout follows containment relations from the root to its descendants.</p>
            <div className="metamodel-selection-actions-grid">
                <HydraButton onClick={() => props.onArrange('ALIGN_LEFT')} type="button">Vertical line</HydraButton>
                <HydraButton onClick={() => props.onArrange('ALIGN_TOP')} type="button">Horizontal line</HydraButton>
                <HydraButton disabled={!props.canDistribute} onClick={() => props.onArrange('DISTRIBUTE_HORIZONTAL')} type="button">Distribute horizontally</HydraButton>
                <HydraButton disabled={!props.canDistribute} onClick={() => props.onArrange('DISTRIBUTE_VERTICAL')} type="button">Distribute vertically</HydraButton>
                <HydraButton disabled={props.elementCount < 2} onClick={() => props.onArrange('TREE')} type="button">Tree layout</HydraButton>
                <HydraButton disabled={props.relationCount === 0} onClick={props.onDeleteRelations} type="button">Delete selected relations</HydraButton>
                <HydraButton className="danger" onClick={props.onDeleteElements} type="button">Delete selected elements</HydraButton>
            </div>
        </section>
    );
}
