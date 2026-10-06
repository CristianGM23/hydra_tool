import type { NodeProps } from '@xyflow/react';
import { HydraNodeResizer } from '../designer-ui/HydraNodeResizer';
import type { MetamodelDataTypeNodeData } from './diagram';

export function MetamodelDataTypeNode(props: NodeProps) {
    const data = props.data as MetamodelDataTypeNodeData;
    const dataType = data.dataTypeDesign;
    return (
        <div className="metamodel-data-type-node" style={{ background: data.visualStyle.fillColor, borderColor: data.visualStyle.strokeColor, borderRadius: data.visualStyle.borderRadius, borderWidth: data.visualStyle.strokeWidth, color: data.visualStyle.textColor, fontFamily: data.visualStyle.fontFamily, fontSize: data.visualStyle.fontSize }}>
            <HydraNodeResizer visible={props.selected && data.resizeEnabled !== false} minHeight={90} minWidth={180} />
            <header className="metamodel-data-type-header" style={{ background: data.visualStyle.headerColor, fontWeight: data.visualStyle.fontWeight }}>
                <span className="metamodel-stereotype">«datatype»</span><strong>{dataType.name}</strong>
            </header>
        </div>
    );
}
