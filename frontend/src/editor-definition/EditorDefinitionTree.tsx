import { HydraButton } from '../design-system/HydraButton';
import { useMemo, useState } from 'react';
import { edgeMappingDisplayName } from './editorSemanticEdge';
import type { MetaClass, MetaModel } from '../metamodel-designer/types';
import { detectMetamodelRoots } from './metamodelRootDetection';
import type { EditorDefinitionModel, EditorDefinitionSelection } from './types';

interface Props {
    model: EditorDefinitionModel;
    metamodel: MetaModel;
    selection: EditorDefinitionSelection;
    selectedRepresentationId: string;
    filter?: string;
    interactive?: boolean;
    showAddActions?: boolean;
    detailLevel?: 'ESSENTIAL' | 'FULL';
    onSelect?: (selection: Exclude<EditorDefinitionSelection, null>, representationId?: string) => void;
    onInspect?: (item: EditorDefinitionTreeInspection) => void;
    onAddClassMapping?: (classId: string, className: string) => void;
    onAddRelationMapping?: (relationId: string) => void;
}

export interface EditorDefinitionTreeInspection {
    id: string;
    label: string;
    detail?: string;
    badge?: string;
    selection?: Exclude<EditorDefinitionSelection, null>;
    representationId?: string;
}

interface TreeNode extends EditorDefinitionTreeInspection {
    addKind?: 'CLASS' | 'RELATION';
    addId?: string;
    addLabel?: string;
    disabled?: boolean;
    children?: TreeNode[];
}

export function EditorDefinitionTree({ model, metamodel, selection, selectedRepresentationId, filter = '', interactive = true, showAddActions = interactive, detailLevel = 'FULL', onSelect, onInspect, onAddClassMapping, onAddRelationMapping }: Props) {
    const nodes = useMemo(() => { const built = buildTree(model, metamodel, selectedRepresentationId); return detailLevel === 'ESSENTIAL' ? simplifyTree(built) : built; }, [detailLevel, metamodel, model, selectedRepresentationId]);
    const [expanded, setExpanded] = useState<Set<string>>(() => new Set(['editor-root', 'semantic-root', 'semantic-classes', 'representations-root', 'palette-root', 'forms-root']));
    const normalizedFilter = filter.trim().toLowerCase();

    const requiredExpanded = useMemo(() => {
        const required = new Set<string>(['editor-root', 'representations-root']);
        if (detailLevel === 'FULL') required.add('semantic-available-mappings');
        if (selectedRepresentationId !== '') required.add(`representation-tree:${selectedRepresentationId}`);
        if (selection?.kind === 'NODE_MAPPING') required.add(`nodes:${selectedRepresentationId}`);
        if (selection?.kind === 'EDGE_MAPPING') required.add(`edges:${selectedRepresentationId}`);
        if (selection?.kind === 'PALETTE_GROUP' || selection?.kind === 'TOOL') required.add('palette-root');
        if (selection?.kind === 'FORM') required.add('forms-root');
        return required;
    }, [detailLevel, selectedRepresentationId, selection]);
    const effectiveExpanded = useMemo(() => new Set([...expanded, ...requiredExpanded]), [expanded, requiredExpanded]);

    function toggle(id: string): void {
        setExpanded(current => {
            const next = new Set(current);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });
    }

    function matches(node: TreeNode): boolean {
        if (normalizedFilter === '') return true;
        return [node.label, node.detail ?? '', node.badge ?? ''].some(value => value.toLowerCase().includes(normalizedFilter)) || (node.children ?? []).some(matches);
    }

    function selected(node: TreeNode): boolean {
        return node.selection !== undefined && selection?.kind === node.selection.kind && selection.id === node.selection.id;
    }

    function render(node: TreeNode, depth: number): React.ReactNode {
        if (!matches(node)) return null;
        const children = (node.children ?? []).filter(matches);
        const hasChildren = children.length > 0;
        const open = normalizedFilter !== '' || effectiveExpanded.has(node.id);
        return <li key={node.id}>
            <div className={`editor-definition-tree-row${selected(node) ? ' selected' : ''}`} style={{ paddingLeft: `${8 + depth * 16}px` }}>
                <HydraButton className="editor-definition-tree-main" type="button" onClick={() => {
                    onInspect?.({ id: node.id, label: node.label, detail: node.detail, badge: node.badge, selection: node.selection, representationId: node.representationId });
                    if (node.selection !== undefined && interactive) onSelect?.(node.selection, node.representationId);
                    if (hasChildren) toggle(node.id);
                }} title={node.detail === undefined ? node.label : `${node.label} · ${node.detail}`}>
                    <span className="editor-definition-tree-expander">{hasChildren ? (open ? '▾' : '▸') : '·'}</span>
                    <span className="editor-definition-tree-icon">{treeIcon(node)}</span>
                    <span className="editor-definition-tree-text"><strong>{node.label}</strong>{node.detail !== undefined && <small>{node.detail}</small>}</span>
                    {node.badge !== undefined && <span className="editor-definition-tree-badge">{node.badge}</span>}
                </HydraButton>
                {showAddActions && node.addKind === 'CLASS' && <HydraButton type="button" className="editor-definition-tree-add" disabled={node.disabled} aria-label={`Add ${node.addLabel ?? node.label} node mapping`} onClick={() => onAddClassMapping?.(node.addId ?? '', node.addLabel ?? node.label)}>+</HydraButton>}
                {showAddActions && node.addKind === 'RELATION' && <HydraButton type="button" className="editor-definition-tree-add" disabled={node.disabled} aria-label={`Add ${node.addLabel ?? node.label} edge mapping`} onClick={() => onAddRelationMapping?.(node.addId ?? '')}>+</HydraButton>}
            </div>
            {hasChildren && open && <ul>{children.map(child => render(child, depth + 1))}</ul>}
        </li>;
    }

    return <ul className="editor-definition-tree" aria-label="Editor definition tree">{nodes.map(node => render(node, 0))}</ul>;
}

function buildTree(model: EditorDefinitionModel, metamodel: MetaModel, selectedRepresentationId: string): TreeNode[] {
    const detected = detectMetamodelRoots(metamodel);
    const selectedRepresentation = model.representations.find(item => item.id === selectedRepresentationId) ?? model.representations[0];
    const configuredRoots = new Set(model.representations.flatMap(item => item.rootClassIds));
    const mappedClasses = new Set(selectedRepresentation?.nodeMappings.map(item => item.targetClassId) ?? []);
    const mappedRelations = new Set(selectedRepresentation?.edgeMappings.filter(item => item.semanticEdge === undefined).map(item => item.relationId) ?? []);

    const classNodes = metamodel.classes.map(metaClass => classNode(metaClass, metamodel, detected.primaryClassId, configuredRoots, mappedClasses));
    const semantic: TreeNode = {
        id: 'semantic-root', label: metamodel.name, detail: metamodel.id, badge: 'METAMODEL', children: [
            { id: 'semantic-root-detected', label: className(metamodel, detected.primaryClassId), detail: rootReason(detected.reason), badge: 'AUTO ROOT' },
            { id: 'semantic-classes', label: 'Classes and interfaces', badge: String(metamodel.classes.length), children: classNodes },
            { id: 'semantic-enums', label: 'Enumerations', badge: String(metamodel.enums.length), children: metamodel.enums.map(item => ({ id: `enum:${item.id}`, label: item.name, detail: item.id, badge: 'ENUM', children: item.literals.map(literal => ({ id: `literal:${literal.id}`, label: literal.name, detail: String(literal.value), badge: 'LITERAL' })) })) },
            { id: 'semantic-datatypes', label: 'Data types', badge: String(metamodel.dataTypes.length), children: metamodel.dataTypes.map(item => ({ id: `datatype:${item.id}`, label: item.name, detail: item.id, badge: 'TYPE' })) }
        ]
    };

    const representations: TreeNode = {
        id: 'representations-root', label: 'Visual representations', badge: String(model.representations.length), children: model.representations.map(representation => ({
            id: `representation-tree:${representation.id}`, label: representation.name, detail: representation.id, badge: representation.kind,
            selection: { kind: 'REPRESENTATION', id: representation.id }, representationId: representation.id,
            children: [
                { id: `roots:${representation.id}`, label: 'Semantic roots', badge: String(representation.rootClassIds.length), children: representation.rootClassIds.map(id => ({ id: `root:${representation.id}:${id}`, label: className(metamodel, id), detail: id, badge: 'ROOT' })) },
                { id: `nodes:${representation.id}`, label: 'Node mappings', badge: String(representation.nodeMappings.length), children: representation.nodeMappings.map(mapping => ({ id: `node-mapping-tree:${mapping.id}`, label: mapping.representationName, detail: `${className(metamodel, mapping.targetClassId)} · ${mapping.id}`, badge: mapping.container ? 'CONTAINER' : mapping.shape, selection: { kind: 'NODE_MAPPING', id: mapping.id }, representationId: representation.id })) },
                { id: `edges:${representation.id}`, label: 'Edge mappings', badge: String(representation.edgeMappings.length), children: representation.edgeMappings.map(mapping => ({ id: `edge-mapping-tree:${mapping.id}`, label: edgeMappingDisplayName(metamodel, mapping), detail: `${className(metamodel, mapping.sourceClassId)} → ${className(metamodel, mapping.targetClassId)} · ${mapping.id}`, badge: mapping.lineType, selection: { kind: 'EDGE_MAPPING', id: mapping.id }, representationId: representation.id })) },
                { id: `layers:${representation.id}`, label: 'Visual layers', badge: String(representation.layers?.length ?? 0), children: (representation.layers ?? []).map(layer => ({ id: `layer-tree:${layer.id}`, label: layer.name, detail: `${layer.nodeMappingIds.length} node mapping(s) · ${layer.edgeMappingIds.length} edge mapping(s) · ${layer.toolIds.length} tool(s)`, badge: layer.defaultLayer ? 'DEFAULT' : layer.activeByDefault ? 'ACTIVE' : 'OPTIONAL', representationId: representation.id })) },
                { id: `filters:${representation.id}`, label: 'Visual filters', badge: String(representation.filters?.length ?? 0), children: (representation.filters ?? []).map(filter => ({ id: `filter-tree:${filter.id}`, label: filter.name, detail: filter.externalCondition || filter.conditionExpressionId || 'No executable condition', badge: filter.conditionExpressionId !== '' ? 'EXECUTABLE' : 'REVIEW', representationId: representation.id })) }
            ]
        }))
    };

    const palette: TreeNode = {
        id: 'palette-root', label: 'Tool palette', badge: String(model.palette.tools.length), children: model.palette.groups.map(group => ({
            id: `palette-group-tree:${group.id}`, label: group.name, detail: group.id, badge: 'GROUP', selection: { kind: 'PALETTE_GROUP', id: group.id },
            children: group.toolIds.map(toolId => model.palette.tools.find(tool => tool.id === toolId)).filter(isDefined).map(tool => ({ id: `tool-tree:${group.id}:${tool.id}`, label: tool.label, detail: tool.id, badge: tool.kind, selection: { kind: 'TOOL', id: tool.id } }))
        }))
    };
    const ungrouped = model.palette.tools.filter(tool => !model.palette.groups.some(group => group.toolIds.includes(tool.id)));
    if (ungrouped.length > 0) palette.children?.push({ id: 'palette-ungrouped', label: 'Ungrouped tools', badge: String(ungrouped.length), children: ungrouped.map(tool => ({ id: `tool-tree:ungrouped:${tool.id}`, label: tool.label, detail: tool.id, badge: tool.kind, selection: { kind: 'TOOL', id: tool.id } })) });

    const forms: TreeNode = {
        id: 'forms-root', label: 'Generated forms', badge: String(model.forms.length), children: model.forms.map(form => ({
            id: `form-tree:${form.id}`, label: form.name, detail: `${className(metamodel, form.targetClassId)} · ${form.id}`, badge: 'FORM', selection: { kind: 'FORM', id: form.id },
            children: form.sections.map(section => ({ id: `form-section-tree:${form.id}:${section.id}`, label: section.title, detail: section.id, badge: `${section.columns} COL`, children: section.fields.map(field => ({ id: `form-field-tree:${field.id}`, label: field.label, detail: field.featureId, badge: field.widget })) }))
        }))
    };

    const editor: TreeNode = { id: 'editor-root', label: model.name, detail: model.id, badge: 'EDITOR', children: [semantic, representations, palette, forms] };

    if (selectedRepresentation !== undefined) {
        semantic.children?.push({
            id: 'semantic-available-mappings', label: `Available in ${selectedRepresentation.name}`, badge: 'ADD', children: [
                ...metamodel.classes.filter(item => item.kind === 'CLASS').map(item => ({ id: `available-class:${item.id}`, label: item.name, detail: item.id, badge: mappedClasses.has(item.id) ? 'MAPPED' : 'CLASS', addKind: 'CLASS' as const, addId: item.id, addLabel: item.name, disabled: mappedClasses.has(item.id) })),
                ...metamodel.relations.map(item => ({ id: `available-relation:${item.id}`, label: item.name || item.id, detail: `${className(metamodel, item.sourceTypeId)} → ${className(metamodel, item.targetTypeId)}`, badge: mappedRelations.has(item.id) ? 'MAPPED' : item.kind, addKind: 'RELATION' as const, addId: item.id, addLabel: item.name || item.id, disabled: mappedRelations.has(item.id) }))
            ]
        });
    }
    return [editor];
}

function simplifyTree(nodes: TreeNode[]): TreeNode[] {
    function simplify(node: TreeNode): TreeNode | null {
        if (node.id === 'semantic-enums' || node.id === 'semantic-datatypes' || node.id === 'semantic-available-mappings') return null;
        if (node.id.startsWith('class:')) return { ...node, detail: undefined, children: undefined };
        if (node.id.startsWith('form-field-tree:')) return null;
        const children=(node.children ?? []).map(simplify).filter((item): item is TreeNode => item !== null);
        return { ...node, detail: node.id === 'editor-root' || node.id === 'semantic-root' ? undefined : node.detail, children: children.length === 0 ? undefined : children };
    }
    return nodes.map(simplify).filter((item): item is TreeNode => item !== null);
}

function classNode(metaClass: MetaClass, metamodel: MetaModel, detectedRootId: string, configuredRoots: Set<string>, mappedClasses: Set<string>): TreeNode {
    const outgoing = metamodel.relations.filter(item => item.sourceTypeId === metaClass.id);
    const incoming = metamodel.relations.filter(item => item.targetTypeId === metaClass.id);
    const badges = [metaClass.kind, metaClass.id === detectedRootId ? 'AUTO ROOT' : '', configuredRoots.has(metaClass.id) ? 'ROOT' : '', mappedClasses.has(metaClass.id) ? 'MAPPED' : ''].filter(Boolean).join(' · ');
    return {
        id: `class:${metaClass.id}`, label: metaClass.name, detail: metaClass.id, badge: badges,
        children: [
            { id: `attributes:${metaClass.id}`, label: 'Attributes', badge: String(metaClass.attributes.length), children: metaClass.attributes.map(attribute => ({ id: `attribute:${attribute.id}`, label: attribute.name, detail: typeLabel(attribute.type), badge: multiplicityLabel(attribute.multiplicity.lowerBound, attribute.multiplicity.upperBound) })) },
            { id: `operations:${metaClass.id}`, label: 'Operations', badge: String(metaClass.operations.length), children: metaClass.operations.map(operation => ({ id: `operation:${operation.id}`, label: operation.name, detail: `${operation.parameters.map(parameter => `${parameter.name}: ${typeLabel(parameter.type)}`).join(', ')}${operation.returnType === null ? '' : ` → ${typeLabel(operation.returnType)}`}`, badge: operation.behaviorKind, children: operation.parameters.map(parameter => ({ id: `parameter:${parameter.id}`, label: parameter.name, detail: typeLabel(parameter.type), badge: 'PARAM' })) })) },
            { id: `outgoing-relations:${metaClass.id}`, label: 'Outgoing relations', badge: String(outgoing.length), children: outgoing.map(relation => ({ id: `outgoing:${metaClass.id}:${relation.id}`, label: relation.name || relation.id, detail: `→ ${className(metamodel, relation.targetTypeId)}`, badge: relation.kind })) },
            { id: `incoming-relations:${metaClass.id}`, label: 'Incoming relations', badge: String(incoming.length), children: incoming.map(relation => ({ id: `incoming:${metaClass.id}:${relation.id}`, label: relation.name || relation.id, detail: `← ${className(metamodel, relation.sourceTypeId)}`, badge: relation.kind })) }
        ]
    };
}

function treeIcon(node: TreeNode): string {
    if (node.badge?.includes('EDITOR')) return 'E';
    if (node.badge?.includes('METAMODEL')) return 'M';
    if (node.badge?.includes('CONTAINER')) return '▣';
    if (node.badge?.includes('ROOT')) return '◆';
    if (node.badge?.includes('FORM')) return 'F';
    if (node.badge?.includes('TOOL') || node.selection?.kind === 'TOOL') return 'T';
    if (node.selection?.kind === 'NODE_MAPPING') return 'N';
    if (node.selection?.kind === 'EDGE_MAPPING') return 'R';
    return '•';
}

function className(metamodel: MetaModel, id: string): string { return metamodel.classes.find(item => item.id === id)?.name ?? (id || 'Unknown class'); }
function typeLabel(type: { kind: string; targetId: string; externalName: string }): string { return type.externalName || type.targetId || type.kind; }
function multiplicityLabel(lower: number, upper: number | null): string { return `[${lower}..${upper === null ? '*' : upper}]`; }
function rootReason(reason: ReturnType<typeof detectMetamodelRoots>['reason']): string { return ({ EXPLICIT_ANNOTATION: 'Explicit annotation', NO_INCOMING_CONTAINMENT: 'No incoming containment', CONTAINMENT_OWNER: 'Containment owner', FIRST_CONCRETE_CLASS: 'First concrete class', NO_CONCRETE_CLASS: 'No concrete class' } as const)[reason]; }

function isDefined<T>(value: T | undefined): value is T { return value !== undefined; }
