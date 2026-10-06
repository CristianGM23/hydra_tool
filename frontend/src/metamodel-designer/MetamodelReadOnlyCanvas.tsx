import { Background, Controls, MiniMap, ReactFlow, ViewportPortal, applyNodeChanges } from '@xyflow/react';
import type { Edge, Node, NodeChange, ReactFlowInstance } from '@xyflow/react';
import { memo, useEffect, useMemo, useRef, useState } from 'react';

import { buildMetamodelEdges, buildMetamodelNodes } from './diagram';
import { MetamodelClassNode } from './MetamodelClassNode';
import { MetamodelDataTypeNode } from './MetamodelDataTypeNode';
import { MetamodelEnumNode } from './MetamodelEnumNode';
import { MetamodelRelationLayer } from './MetamodelRelationLayer';
import { metamodelElkLayout } from './metamodelElkLayout';
import type { MetaClass, MetaModel, MetaRelation, MetamodelDiagramDocument, MetamodelSelection, Multiplicity } from './types';
import './metamodel-designer.css';

interface Props {
    model: MetaModel;
    revisionKey?: string;
    hideDependencies?: boolean;
    allowNodeResize?: boolean;
    onSelectionChange?: (selection: MetamodelSelection) => void;
}

const nodeTypes = {
    metamodelClass: memo(MetamodelClassNode),
    metamodelDataType: memo(MetamodelDataTypeNode),
    metamodelEnum: memo(MetamodelEnumNode),
};

/**
 * Read-only projection that deliberately reuses the exact node and relationship renderers
 * of the canonical Metamodel Designer. It owns only transient viewport/drag state; the
 * inferred metamodel remains the semantic source of truth.
 */
export function MetamodelReadOnlyCanvas({ model, revisionKey = '', hideDependencies = false, allowNodeResize = false, onSelectionChange }: Props) {
    const diagram = useMemo(() => { void revisionKey; return createInferredMetamodelDiagram(model); }, [model, revisionKey]);
    const [nodes, setNodes] = useState<Node[]>(() => markProjectionNodes(buildMetamodelNodes(model, diagram, []), allowNodeResize));
    const [selection, setSelection] = useState<MetamodelSelection>({ kind: 'MODEL', id: model.id });
    const flowRef = useRef<ReactFlowInstance<Node> | null>(null);

    useEffect(() => {
        const frame = window.requestAnimationFrame(() => {
            setNodes(current => markProjectionNodes(buildMetamodelNodes(model, diagram, current), allowNodeResize));
            const nextSelection: MetamodelSelection = { kind: 'MODEL', id: model.id };
            setSelection(nextSelection);
            onSelectionChange?.(nextSelection);
            void flowRef.current?.fitView({ padding: 0.18, maxZoom: 1, duration: 0 });
        });
        return () => window.cancelAnimationFrame(frame);
    }, [allowNodeResize, diagram, model, onSelectionChange]);

    const edges = useMemo(() => buildMetamodelEdges(model, diagram, nodes), [diagram, model, nodes]);

    function changeNodes(changes: NodeChange<Node>[]) {
        // Dragging is always transient. Resizing is transient only when explicitly enabled by
        // the projection owner; otherwise automatic React Flow dimension changes are ignored so
        // inferred semantic content remains the sole source of the initial node geometry.
        const applicable = allowNodeResize ? changes : changes.filter(change => change.type !== 'dimensions');
        if (applicable.length > 0) setNodes(current => applyNodeChanges(applicable, current));
    }

    function select(next: MetamodelSelection) {
        setSelection(next);
        onSelectionChange?.(next);
        if (next.kind === 'CLASS' || next.kind === 'ENUM' || next.kind === 'DATA_TYPE') {
            setNodes(current => current.map(node => ({ ...node, selected: node.id === next.id })));
        }
    }

    return <div className="metamodel-canvas inferred-metamodel-shared-canvas" data-renderer="metamodel-designer">
        <ReactFlow<Node, Edge>
            className="metamodel-flow metamodel-select-mode inferred-metamodel-flow"
            edges={[]}
            elementsSelectable
            fitView
            fitViewOptions={{ padding: 0.18, maxZoom: 1 }}
            nodeTypes={nodeTypes}
            nodes={nodes}
            nodesConnectable={false}
            nodesDraggable
            onInit={instance => { flowRef.current = instance; }}
            onNodeClick={(_event, node) => select(selectionForNode(node))}
            onNodesChange={changeNodes}
            panOnDrag={[1, 2]}
            selectionOnDrag={false}
        >
            <ViewportPortal>
                <MetamodelRelationLayer
                    edges={edges}
                    focusSelected={false}
                    hideDependencies={hideDependencies}
                    nodes={nodes}
                    onSelect={select}
                    selection={selection}
                />
            </ViewportPortal>
            <MiniMap />
            <Controls />
            <Background />
        </ReactFlow>
    </div>;
}

function createInferredMetamodelDiagram(model: MetaModel): MetamodelDiagramDocument {
    const relationBySource = new Map<string, MetaRelation[]>();
    for (const relation of model.relations) {
        const current = relationBySource.get(relation.sourceTypeId) ?? [];
        current.push(relation);
        relationBySource.set(relation.sourceTypeId, current);
    }
    const nodes = [
        ...model.classes.map((item, index) => {
            const size = inferredClassSize(item, relationBySource.get(item.id) ?? [], model);
            return { elementId: item.id, x: 60 + (index % 4) * 330, y: 60 + Math.floor(index / 4) * 230, width: size.width, height: size.height };
        }),
        ...model.enums.map((item, index) => ({ elementId: item.id, x: 60 + ((model.classes.length + index) % 4) * 330, y: 60 + Math.floor((model.classes.length + index) / 4) * 230, width: Math.max(220, Math.min(520, 76 + Math.max(item.name.length, ...item.literals.map(literal => literal.name.length), 12) * 7.2)), height: Math.max(100, 66 + item.literals.length * 22) })),
        ...model.dataTypes.map((item, index) => ({ elementId: item.id, x: 60 + ((model.classes.length + model.enums.length + index) % 4) * 330, y: 60 + Math.floor((model.classes.length + model.enums.length + index) / 4) * 230, width: Math.max(220, Math.min(440, 76 + item.name.length * 7.2)), height: 100 })),
    ];
    const edges = model.relations.map(relation => ({ relationId: relation.id, sourceHandle: '', targetHandle: '', kind: relation.kind, sourceClassId: relation.sourceTypeId, targetClassId: relation.targetTypeId, label: relation.name, routePoints: [] }));
    return metamodelElkLayout(model, { metamodelId: model.id, nodes, edges }, 'LEFT_TO_RIGHT');
}

function markProjectionNodes(nodes: Node[], allowNodeResize: boolean): Node[] {
    return nodes.map(node => ({
        ...node,
        data: { ...node.data, readOnly: true, resizeEnabled: allowNodeResize }
    }));
}


function inferredClassSize(item: MetaClass, relations: MetaRelation[], model: MetaModel): { width: number; height: number } {
    const classNames = new Map(model.classes.map(value => [value.id, value.name]));
    const enumNames = new Map(model.enums.map(value => [value.id, value.name]));
    const dataTypeNames = new Map(model.dataTypes.map(value => [value.id, value.name]));
    const rows: string[] = [item.name];
    for (const attribute of item.attributes) {
        const type = attribute.type.kind === 'CLASS' ? classNames.get(attribute.type.targetId)
            : attribute.type.kind === 'ENUM' ? enumNames.get(attribute.type.targetId)
                : attribute.type.kind === 'DATATYPE' ? dataTypeNames.get(attribute.type.targetId)
                    : attribute.type.kind === 'EXTERNAL' ? attribute.type.externalName : attribute.type.targetId;
        rows.push(`${attribute.name} : ${type || attribute.type.kind} ${multiplicity(attribute.multiplicity)}`);
    }
    for (const relation of relations) rows.push(`${relation.name} : ${classNames.get(relation.targetTypeId) ?? relation.targetTypeId} ${multiplicity(relation.targetMultiplicity)}`);
    for (const operation of item.operations) rows.push(`${operation.name}(${operation.parameters.map(parameter => parameter.name).join(', ')})`);
    const longest = rows.reduce((value, row) => Math.max(value, row.length), 0);
    const width = Math.max(280, Math.min(720, Math.ceil(56 + longest * 7.4)));
    const usableChars = Math.max(24, Math.floor((width - 28) / 7));
    const rowUnits = (values: string[]) => values.reduce((total, value) => total + Math.max(1, Math.ceil(value.length / usableChars)), 0);
    const attributeRows = rows.slice(1, 1 + item.attributes.length);
    const relationRows = rows.slice(1 + item.attributes.length, 1 + item.attributes.length + relations.length);
    const operationRows = rows.slice(1 + item.attributes.length + relations.length);
    const compartmentHeight = (values: string[]) => Math.max(34, 12 + Math.max(1, rowUnits(values)) * 22);
    const height = 48 + compartmentHeight(attributeRows) + compartmentHeight(relationRows) + compartmentHeight(operationRows) + 4;
    return { width, height };
}

function multiplicity(value: Multiplicity | null): string {
    if (value === null) return '';
    const upper = value.upperBound === null ? '*' : String(value.upperBound);
    return value.upperBound !== null && value.lowerBound === value.upperBound ? `[${upper}]` : `[${value.lowerBound}..${upper}]`;
}

function selectionForNode(node: Node): MetamodelSelection {
    if (node.type === 'metamodelEnum') return { kind: 'ENUM', id: node.id };
    if (node.type === 'metamodelDataType') return { kind: 'DATA_TYPE', id: node.id };
    return { kind: 'CLASS', id: node.id };
}
