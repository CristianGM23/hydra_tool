import type { Edge, Node } from '@xyflow/react';

import { DEFAULT_CLASS_NODE_STYLE, DEFAULT_DATA_TYPE_NODE_STYLE, DEFAULT_ENUM_NODE_STYLE, resolveEdgeStyle, resolveNodeStyle } from '../visual-style/visualStyleUtils';
import type { VisualEdgeStyle, VisualNodeStyle } from '../visual-style/visualStyleTypes';
import type { MetaClass, MetaDataType, MetaEnum, MetaModel, MetaRelation, MetamodelConnectionKind, MetamodelDiagramDocument, MetamodelDiagramEdge, MetamodelHandleRole } from './types';
import type { MetamodelEdgeLabelPosition, MetamodelNodePort, MetamodelRoutePoint } from './routingTypes';

/**
 * Contiene los datos visuales de una clase.
 */
export interface MetamodelClassNodeData extends Record<string, unknown> {
    classDesign: MetaClass;
    relations: MetaRelation[];
    classNames: Record<string, string>;
    typeNames: Record<string, string>;
    relationshipMode?: boolean;
    readOnly?: boolean;
    resizeEnabled?: boolean;
    activeSourceHandleId?: string;
    onHandleClick?: (classId: string, handleId: string, role: MetamodelHandleRole) => void;
    visualStyle: VisualNodeStyle;
    ports: MetamodelNodePort[];
}

/**
 * Contiene los datos visuales de una enumeración.
 */
export interface MetamodelEnumNodeData extends Record<string, unknown> {
    enumDesign: MetaEnum;
    resizeEnabled?: boolean;
    visualStyle: VisualNodeStyle;
}

/**
 * Contiene los datos visuales de un tipo de datos personalizado.
 */
export interface MetamodelDataTypeNodeData extends Record<string, unknown> {
    dataTypeDesign: MetaDataType;
    resizeEnabled?: boolean;
    visualStyle: VisualNodeStyle;
}

/**
 * Contiene los datos visuales de una relación.
 */
export interface MetamodelRelationEdgeData extends Record<string, unknown> {
    kind: MetamodelConnectionKind;
    name: string;
    multiplicity: string;
    ownerClassId: string;
    visualStyle?: VisualEdgeStyle;
    routePoints: MetamodelRoutePoint[];
    labelPosition?: MetamodelEdgeLabelPosition;
}

/**
 * Construye los nodos visuales del metamodelo.
 *
 * @param draft contiene el metamodelo.
 * @param diagram contiene el layout guardado.
 * @param currentNodes contiene los nodos actuales.
 * @returns nodos sincronizados.
 */
export function buildMetamodelNodes(model: MetaModel, diagram: MetamodelDiagramDocument, currentNodes: Node[]): Node[] {
    const result: Node[] = [];
    const classNames = Object.fromEntries(model.classes.map(item => [item.id, item.name]));
    const typeNames: Record<string, string> = {};
    for (const metaEnum of model.enums) typeNames[`enum:${metaEnum.id}`] = metaEnum.name;
    for (const dataType of model.dataTypes) typeNames[`datatype:${dataType.id}`] = dataType.name;
    let index = 0;

    for (const classDesign of model.classes) {
        const currentNode = currentNodes.find(node => node.id === classDesign.id);
        const layoutNode = diagram.nodes.find(node => node.elementId === classDesign.id);
        const position = resolvePosition(index, currentNode, layoutNode);
        const width = resolveDimension(currentNode?.measured?.width, currentNode?.width, layoutNode?.width, 240);
        const relations = model.relations.filter(relation => relation.sourceTypeId === classDesign.id && (relation.kind === 'REFERENCE' || relation.kind === 'CONTAINMENT' || relation.kind === 'AGGREGATION'));
        const height = Math.max(resolveDimension(currentNode?.measured?.height, currentNode?.height, layoutNode?.height, calculateClassHeight(classDesign, relations.length)), calculateClassHeight(classDesign, relations.length));
        const visualStyle = resolveNodeStyle(layoutNode?.style, DEFAULT_CLASS_NODE_STYLE);
        const ports = readNodePorts(diagram, classDesign.id);
        result.push({ id: classDesign.id, type: 'metamodelClass', position, width, height, selected: currentNode?.selected ?? false, style: { width, height }, data: { classDesign, relations, classNames, typeNames, visualStyle, ports } satisfies MetamodelClassNodeData });
        index += 1;
    }

    for (const enumDesign of model.enums) {
        const currentNode = currentNodes.find(node => node.id === enumDesign.id);
        const layoutNode = diagram.nodes.find(node => node.elementId === enumDesign.id);
        const position = resolvePosition(index, currentNode, layoutNode);
        const width = resolveDimension(currentNode?.measured?.width, currentNode?.width, layoutNode?.width, 210);
        const height = resolveDimension(currentNode?.measured?.height, currentNode?.height, layoutNode?.height, calculateEnumHeight(enumDesign));
        const visualStyle = resolveNodeStyle(layoutNode?.style, DEFAULT_ENUM_NODE_STYLE);
        result.push({ id: enumDesign.id, type: 'metamodelEnum', position, width, height, selected: currentNode?.selected ?? false, style: { width, height }, data: { enumDesign, visualStyle } satisfies MetamodelEnumNodeData });
        index += 1;
    }

    for (const dataTypeDesign of model.dataTypes) {
        const currentNode = currentNodes.find(node => node.id === dataTypeDesign.id);
        const layoutNode = diagram.nodes.find(node => node.elementId === dataTypeDesign.id);
        const position = resolvePosition(index, currentNode, layoutNode);
        const width = resolveDimension(currentNode?.measured?.width, currentNode?.width, layoutNode?.width, 220);
        const height = resolveDimension(currentNode?.measured?.height, currentNode?.height, layoutNode?.height, 100);
        const visualStyle = resolveNodeStyle(layoutNode?.style, DEFAULT_DATA_TYPE_NODE_STYLE);
        result.push({ id: dataTypeDesign.id, type: 'metamodelDataType', position, width, height, selected: currentNode?.selected ?? false, style: { width, height }, data: { dataTypeDesign, visualStyle } satisfies MetamodelDataTypeNodeData });
        index += 1;
    }
    return result;
}

export function buildMetamodelEdges(model: MetaModel, diagram: MetamodelDiagramDocument, nodes: Node[] = []): Edge[] {
    const result: Edge[] = [];
    const classIds = new Set(model.classes.map(item => item.id));
    for (const relation of model.relations) {
        if (!classIds.has(relation.sourceTypeId) || !classIds.has(relation.targetTypeId)) continue;
        const layoutEdge = findDiagramEdge(diagram, relation.id);
        const handles = resolveConnectionHandles(diagram, nodes, relation.id, relation.sourceTypeId, relation.targetTypeId);
        const multiplicity = (relation.kind === 'REFERENCE' || relation.kind === 'CONTAINMENT' || relation.kind === 'AGGREGATION') && relation.targetMultiplicity !== null
            ? formatMultiplicity(relation.targetMultiplicity.lowerBound, relation.targetMultiplicity.upperBound)
            : '';
        result.push(createVisualEdge(relation.id, relation.sourceTypeId, relation.targetTypeId, handles.sourceHandle, handles.targetHandle, relation.kind, relation.name, multiplicity, relation.sourceTypeId, resolveEdgeStyle(layoutEdge?.style), layoutEdge?.routePoints ?? [], layoutEdge?.labelPosition));
    }
    return result;
}

/**
 * Convierte nodos y conexiones en un documento de layout.
 *
 * @param metamodelId identifica el metamodelo.
 * @param nodes contiene los nodos visuales.
 * @param edges contiene las relaciones visuales.
 * @returns documento persistible.
 */
export function buildMetamodelDiagramDocument(metamodelId: string, nodes: Node[], edges: Edge[]): MetamodelDiagramDocument {
    return {
        metamodelId,
        nodes: nodes.map(node => ({ elementId: node.id, x: node.position.x, y: node.position.y, width: readNodeDimension(node.measured?.width, node.width, node.style?.width, 200), height: readNodeDimension(node.measured?.height, node.height, node.style?.height, 100), style: readNodeVisualStyle(node) })),
        edges: edges.map(edge => buildDiagramEdge(edge))
    };
}

/**
 * Crea un documento vacío para un metamodelo nuevo.
 *
 * @param metamodelId identifica el metamodelo provisional.
 * @returns documento vacío.
 */
export function createEmptyMetamodelDiagram(metamodelId: string): MetamodelDiagramDocument {
    return { metamodelId, nodes: [], edges: [] };
}

/**
 * Construye el identificador visual de una herencia o implementación.
 *
 * @param sourceClassId identifica la subclase o implementación.
 * @param targetClassId identifica la superclase o interfaz.
 * @returns identificador estable.
 */
export function buildInheritanceRelationId(sourceClassId: string, targetClassId: string): string {
    return `inheritance:${sourceClassId}:${targetClassId}`;
}

/**
 * Construye el identificador visual de una dependencia.
 *
 * @param id contiene el identificador único.
 * @returns identificador estable.
 */
export function buildDependencyRelationId(id: string): string {
    return `dependency:${id}`;
}

/**
 * Extrae los extremos de una relación de herencia.
 *
 * @param relationId contiene el identificador.
 * @returns extremos o null.
 */
export function parseInheritanceRelationId(relationId: string): { sourceClassId: string; targetClassId: string } | null {
    const parts = relationId.split(':');

    if (parts.length !== 3 || parts[0] !== 'inheritance') {
        return null;
    }

    return { sourceClassId: parts[1], targetClassId: parts[2] };
}

/**
 * Indica si un identificador representa una dependencia visual.
 *
 * @param relationId contiene el identificador.
 * @returns true cuando es una dependencia.
 */
export function isDependencyRelationId(relationId: string): boolean {
    return relationId.startsWith('dependency:');
}

/**
 * Formatea una cardinalidad Ecore.
 *
 * @param lowerBound contiene el mínimo.
 * @param upperBound contiene el máximo.
 * @returns cardinalidad visible.
 */
export function formatMultiplicity(lowerBound: number, upperBound: number | null): string {
    const upperText = upperBound === null ? '*' : String(upperBound);
    if (upperBound !== null && lowerBound === upperBound) return `[${upperText}]`;
    return `[${lowerBound}..${upperText}]`;
}

/**
 * Selecciona puntos de origen y destino adecuados según la posición de dos clases.
 *
 * @param nodes contiene los nodos visuales.
 * @param sourceId identifica la clase de origen.
 * @param targetId identifica la clase de destino.
 * @returns identificadores de los puntos recomendados.
 */
export function chooseConnectionHandles(nodes: Node[], sourceId: string, targetId: string): { sourceHandle: string; targetHandle: string } {
    const sourceNode = nodes.find(node => node.id === sourceId);
    const targetNode = nodes.find(node => node.id === targetId);

    if (sourceNode === undefined || targetNode === undefined) {
        return { sourceHandle: 'right-source', targetHandle: 'left-target' };
    }

    if (sourceId === targetId) {
        return { sourceHandle: 'right-source', targetHandle: 'bottom-target' };
    }

    const sourceCenter = readNodeCenter(sourceNode);
    const targetCenter = readNodeCenter(targetNode);
    const horizontalDistance = targetCenter.x - sourceCenter.x;
    const verticalDistance = targetCenter.y - sourceCenter.y;

    if (Math.abs(horizontalDistance) >= Math.abs(verticalDistance)) {
        if (horizontalDistance >= 0) {
            return { sourceHandle: 'right-source', targetHandle: 'left-target' };
        }

        return { sourceHandle: 'left-source', targetHandle: 'right-target' };
    }

    if (verticalDistance >= 0) {
        return { sourceHandle: 'bottom-source', targetHandle: 'top-target' };
    }

    return { sourceHandle: 'top-source', targetHandle: 'bottom-target' };
}

/**
 * Crea una relación visual de React Flow.
 *
 * @param id identifica la relación.
 * @param source identifica el origen.
 * @param target identifica el destino.
 * @param sourceHandle identifica el punto de origen.
 * @param targetHandle identifica el punto de destino.
 * @param kind contiene el tipo visual.
 * @param name contiene la etiqueta.
 * @param multiplicity contiene la cardinalidad.
 * @param ownerClassId identifica la clase propietaria.
 * @returns relación visual.
 */
function createVisualEdge(id: string, source: string, target: string, sourceHandle: string, targetHandle: string, kind: MetamodelConnectionKind, name: string, multiplicity: string, ownerClassId: string, visualStyle: VisualEdgeStyle, routePoints: MetamodelRoutePoint[], labelPosition: MetamodelEdgeLabelPosition | undefined): Edge {
    return {
        id,
        type: 'metamodelRelation',
        source,
        target,
        sourceHandle,
        targetHandle,
        interactionWidth: 22,
        selectable: true,
        zIndex: 5,
        data: { kind, name, multiplicity, ownerClassId, visualStyle, routePoints, labelPosition } satisfies MetamodelRelationEdgeData
    };
}

/**
 * Convierte una relación visual en información persistible.
 *
 * @param edge contiene la relación visual.
 * @returns relación del documento de layout.
 */
function buildDiagramEdge(edge: Edge): MetamodelDiagramEdge {
    const data = edge.data as MetamodelRelationEdgeData | undefined;
    return { relationId: edge.id, sourceHandle: edge.sourceHandle ?? '', targetHandle: edge.targetHandle ?? '', kind: data?.kind, sourceClassId: edge.source, targetClassId: edge.target, label: data?.name ?? '', style: data?.visualStyle, routePoints: data?.routePoints ?? [], labelPosition: data?.labelPosition };
}

/**
 * Lee el estilo visual completo almacenado en los datos de un nodo.
 *
 * @param node contiene el nodo visual.
 * @returns estilo del nodo o undefined.
 */
function readNodeVisualStyle(node: Node): VisualNodeStyle | undefined {
    const data = node.data as { visualStyle?: VisualNodeStyle };
    return data.visualStyle;
}

/**
 * Busca los metadatos guardados de una relación.
 *
 * @param diagram contiene el documento visual.
 * @param relationId identifica la relación.
 * @returns relación guardada o undefined.
 */
function findDiagramEdge(diagram: MetamodelDiagramDocument, relationId: string): MetamodelDiagramEdge | undefined {
    return diagram.edges.find(edge => edge.relationId === relationId);
}

/**
 * Resuelve puntos válidos para una relación.
 *
 * @param diagram contiene el layout.
 * @param nodes contiene los nodos.
 * @param relationId identifica la relación.
 * @param sourceId identifica el origen.
 * @param targetId identifica el destino.
 * @returns puntos de origen y destino.
 */
function resolveConnectionHandles(diagram: MetamodelDiagramDocument, nodes: Node[], relationId: string, sourceId: string, targetId: string): { sourceHandle: string; targetHandle: string } {
    const layoutEdge = findDiagramEdge(diagram, relationId);
    const storedSourceHandle = readSourceHandle(layoutEdge?.sourceHandle);
    const storedTargetHandle = readTargetHandle(layoutEdge?.targetHandle);

    if (storedSourceHandle !== undefined && storedTargetHandle !== undefined) {
        return { sourceHandle: storedSourceHandle, targetHandle: storedTargetHandle };
    }

    return chooseConnectionHandles(nodes, sourceId, targetId);
}

/**
 * Resuelve la posición inicial de un nodo.
 *
 * @param index contiene la posición secuencial.
 * @param currentNode contiene el nodo actual.
 * @param layoutNode contiene el nodo guardado.
 * @returns posición seleccionada.
 */
function resolvePosition(index: number, currentNode: Node | undefined, layoutNode: MetamodelDiagramDocument['nodes'][number] | undefined): { x: number; y: number } {
    if (layoutNode !== undefined) {
        return { x: layoutNode.x, y: layoutNode.y };
    }

    if (currentNode !== undefined) {
        return currentNode.position;
    }

    const column = index % 3;
    const row = Math.floor(index / 3);
    return { x: 80 + column * 310, y: 70 + row * 230 };
}

/**
 * Resuelve una dimensión entre varios valores.
 *
 * @param measured contiene la dimensión medida.
 * @param direct contiene la dimensión directa.
 * @param stored contiene la dimensión guardada.
 * @param fallback contiene el valor alternativo.
 * @returns dimensión válida.
 */
function resolveDimension(measured: number | undefined, direct: number | undefined, stored: number | undefined, fallback: number): number {
    if (stored !== undefined && Number.isFinite(stored) && stored > 0) {
        return stored;
    }

    if (measured !== undefined && Number.isFinite(measured) && measured > 0) {
        return measured;
    }

    if (direct !== undefined && Number.isFinite(direct) && direct > 0) {
        return direct;
    }

    return fallback;
}

/**
 * Lee una dimensión de un nodo React Flow.
 *
 * @param measured contiene el valor medido.
 * @param direct contiene el valor directo.
 * @param styleValue contiene el valor CSS.
 * @param fallback contiene el valor alternativo.
 * @returns dimensión válida.
 */
function readNodeDimension(measured: number | undefined, direct: number | undefined, styleValue: string | number | undefined, fallback: number): number {
    if (measured !== undefined && Number.isFinite(measured) && measured > 0) {
        return measured;
    }

    if (direct !== undefined && Number.isFinite(direct) && direct > 0) {
        return direct;
    }

    if (typeof styleValue === 'number' && Number.isFinite(styleValue) && styleValue > 0) {
        return styleValue;
    }

    if (typeof styleValue === 'string') {
        const parsedValue = Number.parseFloat(styleValue);

        if (Number.isFinite(parsedValue) && parsedValue > 0) {
            return parsedValue;
        }
    }

    return fallback;
}

/**
 * Calcula una altura inicial para una clase.
 *
 * @param classDesign contiene la clase.
 * @returns altura recomendada.
 */
function calculateClassHeight(classDesign: MetaClass, relationCount: number): number {
    return Math.max(152, 104 + classDesign.attributes.length * 22 + relationCount * 22 + classDesign.operations.length * 22);
}

/**
 * Calcula una altura inicial para una enumeración.
 *
 * @param enumDesign contiene la enumeración.
 * @returns altura recomendada.
 */
function calculateEnumHeight(enumDesign: MetaEnum): number {
    return Math.max(100, 58 + enumDesign.literals.length * 22);
}

/**
 * Convierte puntos antiguos de origen al formato actual.
 *
 * @param value contiene el identificador guardado.
 * @returns identificador compatible o undefined.
 */
function readSourceHandle(value: string | undefined): string | undefined {
    return normalizeStoredHandle(value, 'SOURCE');
}

/**
 * Convierte un punto guardado en un identificador de destino.
 *
 * @param value contiene el identificador guardado.
 * @returns identificador compatible o undefined.
 */
function readTargetHandle(value: string | undefined): string | undefined {
    return normalizeStoredHandle(value, 'TARGET');
}

/**
 * Conserva puertos dinámicos y adapta los identificadores históricos.
 *
 * @param value contiene el identificador.
 * @param role contiene el papel del puerto.
 * @returns identificador de React Flow.
 */
function normalizeStoredHandle(value: string | undefined, role: MetamodelHandleRole): string | undefined {
    if (value === undefined || value === '') {
        return undefined;
    }

    const suffix = role === 'SOURCE' ? '-source' : '-target';

    if (value.endsWith('-source') || value.endsWith('-target')) {
        return value;
    }

    if (/^(top|right|bottom|left)-\d+(\.\d+)?$/.test(value)) {
        return `${value}${suffix}`;
    }

    if (value.startsWith('top-') || value === 'top') {
        return `top${suffix}`;
    }

    if (value.startsWith('right-') || value === 'right') {
        return `right${suffix}`;
    }

    if (value.startsWith('bottom-') || value === 'bottom') {
        return `bottom${suffix}`;
    }

    if (value.startsWith('left-') || value === 'left') {
        return `left${suffix}`;
    }

    return value;
}

/**
 * Extrae los puertos dinámicos utilizados por un nodo.
 *
 * @param diagram contiene el diagrama.
 * @param elementId identifica el nodo.
 * @returns puertos únicos y ordenados.
 */
function readNodePorts(diagram: MetamodelDiagramDocument, elementId: string): MetamodelNodePort[] {
    const result = new Map<string, MetamodelNodePort>();

    for (const edge of diagram.edges) {
        if (edge.sourceClassId === elementId) {
            const port = parsePort(edge.sourceHandle, 'SOURCE');
            result.set(port.id, port);
        }

        if (edge.targetClassId === elementId) {
            const port = parsePort(edge.targetHandle, 'TARGET');
            result.set(port.id, port);
        }
    }

    return [...result.values()].sort((first, second) => first.side.localeCompare(second.side) || first.percent - second.percent || first.role.localeCompare(second.role));
}

/**
 * Convierte un identificador persistido en un puerto visible.
 *
 * @param value contiene el identificador.
 * @param role contiene el papel.
 * @returns puerto normalizado.
 */
function parsePort(value: string, role: MetamodelHandleRole): MetamodelNodePort {
    const normalized = normalizeStoredHandle(value, role) ?? (role === 'SOURCE' ? 'right-source' : 'left-target');
    const sideMatch = normalized.match(/^(top|right|bottom|left)/);
    const percentMatch = normalized.match(/-(\d+(?:\.\d+)?)(?:-source|-target)?$/);
    const side = (sideMatch?.[1] ?? 'right') as MetamodelNodePort['side'];
    let percent = Number(percentMatch?.[1] ?? 50);

    if (!Number.isFinite(percent)) {
        percent = 50;
    }

    if (percentMatch === null) {
        if (normalized === 'top-source' || normalized === 'right-source') {
            percent = 25;
        } else if (normalized === 'bottom-source' || normalized === 'left-source') {
            percent = 75;
        } else if (normalized === 'top-target' || normalized === 'right-target') {
            percent = 75;
        } else if (normalized === 'bottom-target' || normalized === 'left-target') {
            percent = 25;
        }
    }

    return { id: normalized, side, percent, role };
}

/**
 * Calcula el centro aproximado de un nodo.
 *
 * @param node contiene el nodo visual.
 * @returns coordenadas del centro.
 */
function readNodeCenter(node: Node): { x: number; y: number } {
    const width = readNodeDimension(node.measured?.width, node.width, node.style?.width, 220);
    const height = readNodeDimension(node.measured?.height, node.height, node.style?.height, 120);
    return { x: node.position.x + width / 2, y: node.position.y + height / 2 };
}
