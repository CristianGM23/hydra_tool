import type { ReactNode } from 'react';
import { DesignerDropdown } from './DesignerDropdown';
import { HydraButton } from '../design-system';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import type { DiagramSelectionArrangement } from './diagramSelection';
import { measureHydraDiagramOperation } from '../diagram/performanceTelemetry';
import './diagram-interaction-toolbar.css';

export type DiagramInteractionMode = 'SELECT' | 'PAN' | 'CONNECT';

export interface DiagramSelectionActionsProps {
    elementCount: number;
    relationCount: number;
    onArrange: (arrangement: DiagramSelectionArrangement) => void;
    onClear: () => void;
    onDeleteElements: () => void;
    onDeleteRelations: () => void;
}

interface DiagramInteractionToolbarProps {
    mode: DiagramInteractionMode;
    onModeChange: (mode: DiagramInteractionMode) => void;
    allowConnect?: boolean;
    canUndo?: boolean;
    canRedo?: boolean;
    onUndo?: () => void;
    onRedo?: () => void;
    onDuplicate?: () => void;
    onDelete?: () => void;
    onAutoLayout?: () => void;
    onElkLayout?: () => void;
    onFit?: () => void;
    selection?: DiagramSelectionActionsProps | null;
    trailing?: ReactNode;
    /** Stable identifier used only for local performance diagnostics. */
    performanceId?: string;
}

/**
 * Barra de interacción común de los diagramadores.
 *
 * Las herramientas se presentan como categorías desplegables para mantener el
 * lienzo limpio. Selección, historial, edición, layout y acciones colectivas
 * siguen siendo independientes del documento persistido.
 */
export function DiagramInteractionToolbar({ mode, onModeChange, allowConnect = false, canUndo, canRedo, onUndo, onRedo,
        onDuplicate, onDelete, onAutoLayout, onElkLayout, onFit, selection, trailing, performanceId = 'diagram' }: DiagramInteractionToolbarProps) {
    const { t } = useHydraLanguage();
    const help = mode === 'PAN'
        ? 'Drag the background to move the canvas.'
        : mode === 'CONNECT'
            ? 'Choose a source connector and then a compatible target connector.'
            : 'Drag the background to select an area. Switch to Pan when you want to move the viewport.';
    return <section aria-label={t('Diagram tools')} className="hydra-diagram-toolbox">
        <div className="hydra-diagram-toolbox-main">
            <div className="hydra-diagram-pointer-modes" role="group" aria-label={t("Pointer")}>
                <HydraButton aria-pressed={mode === 'SELECT'} className={mode === 'SELECT' ? 'active' : ''} type="button" onClick={() => onModeChange('SELECT')}>{t("Select")}</HydraButton>
                <HydraButton aria-pressed={mode === 'PAN'} className={mode === 'PAN' ? 'active' : ''} type="button" onClick={() => onModeChange('PAN')}>{t("Pan")}</HydraButton>
                {allowConnect && <HydraButton aria-pressed={mode === 'CONNECT'} className={mode === 'CONNECT' ? 'active connect' : 'connect'} type="button" onClick={() => onModeChange('CONNECT')}>{t("Connect")}</HydraButton>}
            </div>
            {(onUndo !== undefined || onRedo !== undefined) && <DiagramToolGroup label={t("History")}>
                {onUndo !== undefined && <HydraButton disabled={canUndo === false} type="button" onClick={onUndo}>{t("Undo")}</HydraButton>}
                {onRedo !== undefined && <HydraButton disabled={canRedo === false} type="button" onClick={onRedo}>{t("Redo")}</HydraButton>}
            </DiagramToolGroup>}
            {(onDuplicate !== undefined || onDelete !== undefined) && <DiagramToolGroup label={t("Edit")}>
                {onDuplicate !== undefined && <HydraButton type="button" onClick={onDuplicate}>{t("Duplicate")}</HydraButton>}
                {onDelete !== undefined && <HydraButton className="danger" type="button" onClick={onDelete}>{t("Delete")}</HydraButton>}
            </DiagramToolGroup>}
            {(onAutoLayout !== undefined || onElkLayout !== undefined || onFit !== undefined) && <DiagramToolGroup label={t("Canvas")}>
                {onAutoLayout !== undefined && <HydraButton type="button" onClick={() => measureHydraDiagramOperation(performanceId, 'layout', onAutoLayout)}>{t("Auto layout")}</HydraButton>}
                {onElkLayout !== undefined && <HydraButton type="button" onClick={() => measureHydraDiagramOperation(performanceId, 'layout', onElkLayout)}>{t("ELK-style layout")}</HydraButton>}
                {onFit !== undefined && <HydraButton type="button" onClick={onFit}>{t("Fit")}</HydraButton>}
            </DiagramToolGroup>}
            {trailing !== undefined && <div className="hydra-diagram-toolbox-trailing">{trailing}</div>}
            <span className="hydra-diagram-toolbox-help">{t(help)}</span>
        </div>
        {selection !== null && selection !== undefined && (selection.elementCount > 1 || selection.relationCount > 0)
            && <DiagramSelectionActions {...selection} performanceId={performanceId} />}
    </section>;
}

function DiagramToolGroup({ label, children, summary }: { label: string; children: ReactNode; summary?: ReactNode }) {
    return <div className="hydra-diagram-tool-group"><span aria-hidden="true">{label}</span><DesignerDropdown label={label} summary={summary}>{children}</DesignerDropdown></div>;
}

/** Acciones compactas para una selección múltiple de un diagrama. */
export function DiagramSelectionActions(props: DiagramSelectionActionsProps & { performanceId?: string }) {
    const { t } = useHydraLanguage();
    const canArrange = props.elementCount >= 2;
    const canDistribute = props.elementCount >= 3;
    return <div aria-label={t("Selection")} className="hydra-diagram-selection-actions">
        <div className="hydra-diagram-selection-summary"><strong>{t("Selection")}</strong><span>{props.elementCount} {t("element(s)")}</span><span>{props.relationCount} {t("relation(s)")}</span></div>
        <DiagramToolGroup label={t("Align")}>
            <HydraButton disabled={!canArrange} type="button" onClick={() => measureHydraDiagramOperation(props.performanceId ?? 'diagram', 'layout', () => props.onArrange('ALIGN_LEFT'))}>{t("Vertical line")}</HydraButton>
            <HydraButton disabled={!canArrange} type="button" onClick={() => measureHydraDiagramOperation(props.performanceId ?? 'diagram', 'layout', () => props.onArrange('ALIGN_TOP'))}>{t("Horizontal line")}</HydraButton>
        </DiagramToolGroup>
        <DiagramToolGroup label={t("Distribute")}>
            <HydraButton disabled={!canDistribute} type="button" onClick={() => measureHydraDiagramOperation(props.performanceId ?? 'diagram', 'layout', () => props.onArrange('DISTRIBUTE_HORIZONTAL'))}>{t("Distribute horizontally")}</HydraButton>
            <HydraButton disabled={!canDistribute} type="button" onClick={() => measureHydraDiagramOperation(props.performanceId ?? 'diagram', 'layout', () => props.onArrange('DISTRIBUTE_VERTICAL'))}>{t("Distribute vertically")}</HydraButton>
        </DiagramToolGroup>
        <DiagramToolGroup label={t("Selection")}>
            <HydraButton disabled={props.relationCount === 0} type="button" onClick={props.onDeleteRelations}>{t("Delete selected relations")}</HydraButton>
            <HydraButton className="danger" disabled={props.elementCount === 0} type="button" onClick={props.onDeleteElements}>{t("Delete selected elements")}</HydraButton>
            <HydraButton type="button" onClick={props.onClear}>{t("Clear")}</HydraButton>
        </DiagramToolGroup>
    </div>;
}
