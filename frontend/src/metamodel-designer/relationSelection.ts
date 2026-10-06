/** Selector utilizado por los elementos SVG que permiten seleccionar una relación. */
export const METAMODEL_RELATION_INTERACTION_SELECTOR = '[data-metamodel-relation-interaction="true"], .metamodel-rendered-relation';

/** Elementos del lienzo que no deben iniciar una selección rectangular. */
const NON_BACKGROUND_SELECTOR = '.react-flow__node, .react-flow__controls, .react-flow__minimap, .metamodel-canvas-interaction-toolbar, .metamodel-selection-actions';

/**
 * Indica si el objetivo pertenece a una relación o a su etiqueta.
 */
export function isMetamodelRelationInteractionTarget(target: EventTarget | null): boolean {
    return target instanceof Element && target.closest(METAMODEL_RELATION_INTERACTION_SELECTOR) !== null;
}

/**
 * Indica si una pulsación pertenece realmente al fondo seleccionable del lienzo.
 * Las relaciones se excluyen para que su evento llegue al editor de propiedades.
 */
export function isAreaSelectionBackgroundTarget(target: EventTarget | null): boolean {
    if(!(target instanceof Element)) {
        return false;
    }

    if(isMetamodelRelationInteractionTarget(target)) {
        return false;
    }

    if(target.closest(NON_BACKGROUND_SELECTOR) !== null) {
        return false;
    }

    return target.closest('.react-flow__pane') !== null;
}

/**
 * Indica si una interacción dentro del lienzo debe retirar el foco de una relación.
 * Los elementos que forman parte de la propia relación conservan la selección para permitir
 * editar su línea, etiqueta y puntos ortogonales.
 */
export function shouldClearMetamodelRelationFocus(relationSelected: boolean, target: EventTarget | null): boolean {
    return relationSelected && target instanceof Element && !isMetamodelRelationInteractionTarget(target);
}
