import { HydraButton } from '../design-system/HydraButton';
import { useMemo, type DragEvent } from 'react';
import { HydraVirtualList } from '../design-system/HydraVirtualList';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import { findNodeStyles } from '../notation/notationUtils';
import type { NotationDefinition, PaletteEntry } from '../types/editor';

export const MODEL_PALETTE_DRAG_TYPE = 'application/x-hydra-model-palette-item';
export interface ModelPaletteDragPayload { entry: PaletteEntry; nodeStyleId?: string; }

interface PaletteProps {
    entries: PaletteEntry[];
    notation?: NotationDefinition | null;
    onCreate: (entry: PaletteEntry, nodeStyleId?: string) => void;
}

function startDrag(event: DragEvent<HTMLButtonElement>, payload: ModelPaletteDragPayload): void {
    event.dataTransfer.setData(MODEL_PALETTE_DRAG_TYPE, JSON.stringify(payload));
    event.dataTransfer.effectAllowed = 'copy';
}

/** Muestra clases/representaciones y permite añadirlas por clic o arrastrándolas al punto exacto del lienzo. */
export function Palette({ entries, notation = null, onCreate }: PaletteProps) {
    const { t } = useHydraLanguage();
    const items = useMemo(() => entries.flatMap(entry => {
        const styles = findNodeStyles(notation, entry.classId);
        if(styles.length === 0) return [{ key: entry.classId, entry, nodeStyleId: undefined as string | undefined, subtitle: entry.packageName }];
        return styles.map(style => ({ key: `${entry.classId}:${style.id}`, entry, nodeStyleId: style.id, subtitle: style.representationName || entry.packageName }));
    }), [entries, notation]);
    if (entries.length === 0) return <aside className="palette"><h2>{t('Palette')}</h2><p>{t('No elements available')}</p></aside>;
    return <aside className="palette" aria-label={t('Model element palette')}>
        <h2>{t('Palette')}</h2>
        <p className="palette-help">{t('Drag a card to place it exactly on the canvas, or click to add it automatically.')}</p>
        <HydraVirtualList items={items} itemHeight={58} height={520} threshold={300} ariaLabel={t('Available model elements')} className="model-palette-items"
            itemKey={item => item.key} renderItem={item => <HydraButton type="button" draggable
                onDragStart={event => startDrag(event, { entry: item.entry, nodeStyleId: item.nodeStyleId })}
                onClick={() => item.nodeStyleId === undefined ? onCreate(item.entry) : onCreate(item.entry, item.nodeStyleId)}>
                <strong>{item.entry.className}</strong><small>{item.subtitle}</small>
            </HydraButton>} />
    </aside>;
}
