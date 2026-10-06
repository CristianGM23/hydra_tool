import type { NodeProps } from '@xyflow/react';
import { HydraNodeResizer } from '../designer-ui/HydraNodeResizer';
import type { MetaClassKind, MetaOperation, MetaTypeReference, Multiplicity } from './types';
import type { MetamodelClassNodeData } from './diagram';
import { MetamodelHandles } from './MetamodelHandles';

export function MetamodelClassNode(props: NodeProps) {
    const data = props.data as MetamodelClassNodeData;
    const metaClass = data.classDesign;
    const relationshipMode = data.relationshipMode === true;
    return (
        <div className="metamodel-class-node" style={{ background: data.visualStyle.fillColor, borderColor: data.visualStyle.strokeColor, borderRadius: data.visualStyle.borderRadius, borderWidth: data.visualStyle.strokeWidth, color: data.visualStyle.textColor, fontFamily: data.visualStyle.fontFamily, fontSize: data.visualStyle.fontSize }}>
            <HydraNodeResizer visible={props.selected && !relationshipMode && data.resizeEnabled !== false} minHeight={110} minWidth={190} />
            <MetamodelHandles activeSourceHandleId={data.activeSourceHandleId ?? ''} isConnectable={props.isConnectable} ports={data.ports} relationshipMode={relationshipMode} onHandleClick={(handleId, role) => data.onHandleClick?.(metaClass.id, handleId, role)} />
            <header className="metamodel-class-header" style={{ background: data.visualStyle.headerColor, fontWeight: data.visualStyle.fontWeight }}>
                {buildStereotype(metaClass.kind) !== '' && <span className="metamodel-stereotype">{buildStereotype(metaClass.kind)}</span>}
                <strong>{metaClass.name}</strong>
            </header>
            <section className="metamodel-compartment">
                {metaClass.attributes.length === 0 && <span className="metamodel-empty-row">No attributes</span>}
                {metaClass.attributes.map(attribute => <div className="metamodel-feature-row" key={attribute.id}>{attribute.name} : {formatType(attribute.type, data.typeNames)} {formatMultiplicity(attribute.multiplicity)}</div>)}
            </section>
            <section className="metamodel-compartment">
                {data.relations.length === 0 && <span className="metamodel-empty-row">No references</span>}
                {data.relations.map(relation => <div className="metamodel-feature-row" key={relation.id}>{relation.name} : {data.classNames[relation.targetTypeId] ?? relation.targetTypeId} {formatMultiplicity(relation.targetMultiplicity)}</div>)}
            </section>
            <section className="metamodel-compartment metamodel-operation-compartment">
                {metaClass.operations.length === 0 && <span className="metamodel-empty-row">No operations</span>}
                {metaClass.operations.map(operation => <div className="metamodel-feature-row" key={operation.id}>{formatOperation(operation, data.typeNames, data.classNames)}</div>)}
            </section>
        </div>
    );
}

function buildStereotype(kind: MetaClassKind): string {
    if (kind === 'INTERFACE') return '«interface»';
    if (kind === 'ABSTRACT_CLASS') return '«abstract»';
    return '';
}
function formatType(type: MetaTypeReference, typeNames: Record<string, string>): string {
    if (type.kind === 'EXTERNAL') return type.externalName;
    const key = `${type.kind.toLowerCase()}:${type.targetId}`;
    return typeNames[key] ?? type.targetId;
}
function formatMultiplicity(value: Multiplicity | null): string {
    if (value === null) return '';
    const upper = value.upperBound === null ? '*' : String(value.upperBound);
    return value.upperBound !== null && value.lowerBound === value.upperBound ? `[${upper}]` : `[${value.lowerBound}..${upper}]`;
}

function formatOperation(operation: MetaOperation, typeNames: Record<string, string>, classNames: Record<string, string>): string {
    const parameters = operation.parameters.map(parameter => `${parameter.name}: ${formatOperationType(parameter.type, typeNames, classNames)}${formatMultiplicity(parameter.multiplicity)}`).join(', ');
    const result = operation.returnType === null ? 'void' : `${formatOperationType(operation.returnType, typeNames, classNames)}${formatMultiplicity(operation.returnMultiplicity)}`;
    const behavior = operation.behaviorKind === 'UNSPECIFIED' ? '' : ` {${operation.behaviorKind.toLowerCase()}}`;
    return `${operation.name}(${parameters}) : ${result}${behavior}`;
}
function formatOperationType(type: MetaTypeReference, typeNames: Record<string, string>, classNames: Record<string, string>): string {
    if(type.kind === 'CLASS') return classNames[type.targetId] ?? type.targetId;
    return formatType(type, typeNames);
}
