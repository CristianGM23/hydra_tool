import type { Node } from '@xyflow/react';

/**
 * Elimina la selección visual de todas las figuras del lienzo.
 * Devuelve la misma colección cuando ya no hay ninguna figura seleccionada.
 */
export function clearMetamodelNodeSelection(nodes: Node[]): Node[] {
    if(!nodes.some(node => node.selected === true)) {
        return nodes;
    }

    return nodes.map(node => node.selected === true ? { ...node, selected: false } : node);
}
