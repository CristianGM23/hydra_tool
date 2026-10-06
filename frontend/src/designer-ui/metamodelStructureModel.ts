import type { DesignerStructureNode } from './DesignerStructureTree';
import type { MetaClass, MetaModel, MetaRelation } from '../metamodel-designer/types';
import { detectMetamodelRoots } from '../editor-definition/metamodelRootDetection';
import type { MetamodelStructureSelection } from './metamodelStructureTypes';

export function buildMetamodelStructure(metamodel: MetaModel): DesignerStructureNode<MetamodelStructureSelection>[] {
    const roots = detectMetamodelRoots(metamodel);
    const classNodes = metamodel.classes.map(metaClass => classNode(metaClass, metamodel, roots.primaryClassId));
    const relationNodes = metamodel.relations.map(relation => relationNode(relation, metamodel));
    const rootData = semantic('MODEL', metamodel.id, metamodel.name, metamodel.id, 'METAMODEL');
    return [{
        id: 'semantic-model-root', label: metamodel.name, detail: metamodel.id, badge: 'METAMODEL', data: rootData,
        children: [
            {
                id: 'semantic-root-proposal',
                label: `Automatic root: ${semanticName(metamodel, roots.primaryClassId)}`,
                detail: rootReason(roots.reason),
                badge: 'AUTO ROOT',
                data: semantic('CLASS', roots.primaryClassId, semanticName(metamodel, roots.primaryClassId), rootReason(roots.reason), 'AUTO ROOT')
            },
            { id: 'semantic-classes-root', label: 'Classes and interfaces', badge: String(classNodes.length), children: classNodes },
            { id: 'semantic-relations-root', label: 'Relations', badge: String(relationNodes.length), children: relationNodes },
            {
                id: 'semantic-enums-root', label: 'Enumerations', badge: String(metamodel.enums.length), children: metamodel.enums.map(item => ({
                    id: `semantic-enum:${item.id}`, label: item.name, detail: item.id, badge: 'ENUM', data: semantic('ENUM', item.id, item.name, item.id, 'ENUM'),
                    children: item.literals.map(literal => ({ id: `semantic-literal:${literal.id}`, label: literal.name, detail: String(literal.value), badge: 'LITERAL', data: { ...semantic('LITERAL', literal.id, literal.name, String(literal.value), 'LITERAL'), ownerId: item.id } }))
                }))
            },
            {
                id: 'semantic-datatypes-root', label: 'Data types', badge: String(metamodel.dataTypes.length), children: metamodel.dataTypes.map(item => ({
                    id: `semantic-datatype:${item.id}`, label: item.name, detail: item.id, badge: 'TYPE', data: semantic('DATA_TYPE', item.id, item.name, item.id, 'TYPE')
                }))
            }
        ]
    }];
}

function classNode(metaClass: MetaClass, metamodel: MetaModel, rootId: string): DesignerStructureNode<MetamodelStructureSelection> {
    const outgoing = metamodel.relations.filter(relation => relation.sourceTypeId === metaClass.id);
    const incoming = metamodel.relations.filter(relation => relation.targetTypeId === metaClass.id);
    const badge = [metaClass.kind.replaceAll('_', ' '), metaClass.id === rootId ? 'AUTO ROOT' : ''].filter(Boolean).join(' · ');
    return {
        id: `semantic-class:${metaClass.id}`, label: metaClass.name, detail: metaClass.id, badge,
        data: semantic('CLASS', metaClass.id, metaClass.name, metaClass.id, badge),
        children: [
            {
                id: `semantic-attributes:${metaClass.id}`, label: 'Attributes', badge: String(metaClass.attributes.length), children: metaClass.attributes.map(attribute => ({
                    id: `semantic-attribute:${attribute.id}`, label: attribute.name, detail: `${typeLabel(attribute.type)} ${multiplicity(attribute.multiplicity.lowerBound, attribute.multiplicity.upperBound)}`, badge: 'ATTRIBUTE', data: { ...semantic('ATTRIBUTE', attribute.id, attribute.name, typeLabel(attribute.type), 'ATTRIBUTE'), ownerId: metaClass.id }
                }))
            },
            {
                id: `semantic-operations:${metaClass.id}`, label: 'Operations', badge: String(metaClass.operations.length), children: metaClass.operations.map(operation => ({
                    id: `semantic-operation:${operation.id}`, label: operation.name, detail: operation.returnType === null ? 'No return value' : typeLabel(operation.returnType), badge: cleanLabel(operation.behaviorKind), data: { ...semantic('OPERATION', operation.id, operation.name, operation.returnType === null ? 'No return value' : typeLabel(operation.returnType), cleanLabel(operation.behaviorKind)), ownerId: metaClass.id },
                    children: operation.parameters.map(parameter => ({ id: `semantic-parameter:${parameter.id}`, label: parameter.name, detail: typeLabel(parameter.type), badge: 'PARAMETER', data: { ...semantic('PARAMETER', parameter.id, parameter.name, typeLabel(parameter.type), 'PARAMETER'), ownerId: operation.id } }))
                }))
            },
            { id: `semantic-outgoing:${metaClass.id}`, label: 'Outgoing relations', badge: String(outgoing.length), children: outgoing.map((relation, index) => relationNode(relation, metamodel, `outgoing:${metaClass.id}:${index}`)) },
            { id: `semantic-incoming:${metaClass.id}`, label: 'Incoming relations', badge: String(incoming.length), children: incoming.map((relation, index) => relationNode(relation, metamodel, `incoming:${metaClass.id}:${index}`)) }
        ]
    };
}

function relationNode(relation: MetaRelation, metamodel: MetaModel, context = ''): DesignerStructureNode<MetamodelStructureSelection> {
    const label = relation.name || cleanIdentifier(relation.id);
    const detail = `${semanticName(metamodel, relation.sourceTypeId)} → ${semanticName(metamodel, relation.targetTypeId)}`;
    const id = context === '' ? `semantic-relation:${relation.id}` : `semantic-relation:${relation.id}:${context}`;
    return { id, label, detail, badge: cleanLabel(relation.kind), data: { ...semantic('RELATION', relation.id, label, detail, cleanLabel(relation.kind)), treeNodeId:id } };
}

function semantic(kind: MetamodelStructureSelection['kind'], id: string, label: string, detail: string, badge: string): MetamodelStructureSelection {
    return { kind, id, label, detail, badge };
}

export function semanticNodeId(selection: MetamodelStructureSelection): string {
    if (selection.treeNodeId !== undefined) return selection.treeNodeId;
    const prefix = ({ MODEL: 'semantic-model-root', CLASS: 'semantic-class', ATTRIBUTE: 'semantic-attribute', OPERATION: 'semantic-operation', PARAMETER: 'semantic-parameter', RELATION: 'semantic-relation', ENUM: 'semantic-enum', LITERAL: 'semantic-literal', DATA_TYPE: 'semantic-datatype' } as const)[selection.kind];
    return selection.kind === 'MODEL' ? prefix : `${prefix}:${selection.id}`;
}

export function semanticName(metamodel: MetaModel, id: string): string {
    return metamodel.classes.find(item => item.id === id)?.name
        ?? metamodel.enums.find(item => item.id === id)?.name
        ?? metamodel.dataTypes.find(item => item.id === id)?.name
        ?? cleanIdentifier(id || 'Unresolved');
}

function typeLabel(type: { kind: string; targetId: string; externalName: string }): string { return semanticLabel(type.externalName || type.targetId || type.kind); }
function multiplicity(lower: number, upper: number | null): string { return `[${lower}..${upper === null ? '*' : upper}]`; }
function semanticLabel(value: string): string { return cleanIdentifier(value).replaceAll('_', ' '); }
function cleanIdentifier(value: string): string { return value.split(/[.:/]/).filter(Boolean).at(-1) ?? value; }
function cleanLabel(value: string): string { return value.toLowerCase().split('_').map(part => `${part.charAt(0).toUpperCase()}${part.slice(1)}`).join(' '); }
export function friendlyKind(kind: MetamodelStructureSelection['kind']): string { return cleanLabel(kind); }
function rootReason(reason: ReturnType<typeof detectMetamodelRoots>['reason']): string { return ({ EXPLICIT_ANNOTATION: 'Explicit root annotation', NO_INCOMING_CONTAINMENT: 'No incoming containment relation', CONTAINMENT_OWNER: 'Owns contained elements', FIRST_CONCRETE_CLASS: 'First concrete class', NO_CONCRETE_CLASS: 'No concrete class available' } as const)[reason]; }
