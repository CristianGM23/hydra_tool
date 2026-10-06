import type { EventBindingModel, EventDefinitionModel } from './types';
import type { EventFormSelection } from './EventDesignerParts';

/**
 * Selecciona el primer elemento editable disponible de los contratos de eventos.
 *
 * @param definitions contiene las definiciones de tipos de evento.
 * @param bindings contiene las vinculaciones entre eventos.
 * @returns primera selección válida o `null` cuando no hay elementos.
 */
export function firstEventSelection(definitions: EventDefinitionModel, bindings: EventBindingModel): EventFormSelection {
    const firstType = definitions.eventTypes[0];
    if (firstType !== undefined) return { kind: 'EVENT', id: firstType.id };
    const firstBinding = bindings.bindings[0];
    return firstBinding === undefined ? null : { kind: 'BINDING', id: firstBinding.id };
}

/**
 * Fusiona dos colecciones conservando un único elemento por identificador estable.
 *
 * @param current contiene los elementos existentes.
 * @param imported contiene los elementos importados que prevalecen por identificador.
 * @returns colección fusionada sin identificadores duplicados.
 */
export function mergeByStableId<T extends { id: string }>(current: readonly T[], imported: readonly T[]): T[] {
    const byId = new Map(current.map(item => [item.id, item]));
    for (const item of imported) byId.set(item.id, item);
    return [...byId.values()];
}
