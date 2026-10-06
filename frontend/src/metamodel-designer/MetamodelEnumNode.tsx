import type { NodeProps } from '@xyflow/react';
import { HydraNodeResizer } from '../designer-ui/HydraNodeResizer';

import type { MetamodelEnumNodeData } from './diagram';

/**
 * Representa una EEnum como una tarjeta UML redimensionable.
 *
 * @param props contiene el nodo de React Flow.
 * @returns representación visual de la enumeración.
 */
export function MetamodelEnumNode(props: NodeProps) {
    const data = props.data as MetamodelEnumNodeData;
    const enumDesign = data.enumDesign;

    return (
        <div className="metamodel-enum-node" style={{ background: data.visualStyle.fillColor, borderColor: data.visualStyle.strokeColor, borderRadius: data.visualStyle.borderRadius, borderWidth: data.visualStyle.strokeWidth, color: data.visualStyle.textColor, fontFamily: data.visualStyle.fontFamily, fontSize: data.visualStyle.fontSize }}>
            <HydraNodeResizer visible={props.selected && data.resizeEnabled !== false} minHeight={90} minWidth={170} />
            <header className="metamodel-enum-header" style={{ background: data.visualStyle.headerColor, fontWeight: data.visualStyle.fontWeight }}>
                <span className="metamodel-stereotype">«enumeration»</span>
                <strong>{enumDesign.name}</strong>
            </header>
            <section className="metamodel-compartment">
                {enumDesign.literals.map(literal => <div className="metamodel-feature-row" key={literal.id}>{literal.name} = {literal.value}</div>)}
            </section>
        </div>
    );
}
