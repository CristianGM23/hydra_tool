import { useMemo, useState } from 'react';
import { HydraButton } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { MetaAttribute, MetaClass, MetaEnum, MetaModel, MetaRelation, MetaTypeReference } from '../metamodel-designer/types';

interface TreeBadge {
    id: string;
    label: string;
}

interface TreeNode {
    id: string;
    label: string;
    kind?: 'ROOT' | 'CLASS' | 'ENUM' | 'ATTRIBUTE' | 'RELATION' | 'LITERAL' | 'EMPTY';
    badges?: TreeBadge[];
    children?: TreeNode[];
}

export function GrammarInferredMetamodelView({ metamodel, loading = false, error = '' }: { metamodel: MetaModel | null; loading?: boolean; error?: string; }) {
    const { t } = useHydraLanguage();
    const [technical, setTechnical] = useState(false);
    const tree = useMemo(() => metamodel === null ? null : buildMetamodelTree(metamodel, t), [metamodel, t]);
    if (metamodel === null) {
        return <section className="grammar-metamodel-preview empty"><strong>{t('Inferred metamodel')}</strong>{loading
            ? <><span className="grammar-visualization-spinner" aria-hidden="true" /><p>{t('Building the inferred metamodel from the current grammar…')}</p></>
            : error !== ''
                ? <p>{t('The inferred metamodel could not be built from the current draft. Review Problems for details.')}</p>
                : <p>{t('Open this view to infer the metamodel automatically from the current grammar. No compilation is required.')}</p>}</section>;
    }
    return <section className="grammar-metamodel-preview" aria-label={t('Inferred metamodel')}>
        <header className="grammar-diagram-toolbar"><div><strong>{t('Inferred metamodel')}</strong><span>{metamodel.classes.length} {t('classes')} · {metamodel.enums.length} {t('enumerations')} · {metamodel.relations.length} {t('relations')}</span></div><HydraButton type="button" onClick={() => setTechnical(value => !value)}>{t(technical ? 'Hide technical model' : 'View technical model')}</HydraButton></header>
        {technical
            ? <pre>{JSON.stringify(metamodel, null, 2)}</pre>
            : <div className="grammar-metamodel-tree-surface">
                <p className="grammar-metamodel-tree-copy">{t('The inferred metamodel is shown as a tree from the model root to classes, attributes, relations and literals.')}</p>
                <div className="grammar-metamodel-tree-scroll">
                    {tree !== null && <ul className="grammar-metamodel-tree-root"><MetamodelTreeNode node={tree} /></ul>}
                </div>
            </div>}
    </section>;
}

function buildMetamodelTree(metamodel: MetaModel, t: (value: string) => string): TreeNode {
    const classById = new Map(metamodel.classes.map(item => [item.id, item]));
    const className = new Map(metamodel.classes.map(item => [item.id, item.name]));
    const relationsBySource = new Map<string, MetaRelation[]>();
    const targetedClassIds = new Set<string>();
    for (const relation of metamodel.relations) {
        const current = relationsBySource.get(relation.sourceTypeId) ?? [];
        current.push(relation);
        relationsBySource.set(relation.sourceTypeId, current);
        if (classById.has(relation.targetTypeId)) {
            targetedClassIds.add(relation.targetTypeId);
        }
    }
    const rootClasses = metamodel.classes.filter(item => !targetedClassIds.has(item.id));
    const orderedRoots = rootClasses.length > 0 ? rootClasses : metamodel.classes;
    const rootChildren: TreeNode[] = orderedRoots.map(item => buildReachableClassNode(item, relationsBySource, classById, className, t, new Set<string>()));
    const enumChildren = metamodel.enums.map(item => buildEnumNode(item, t));
    return {
        id: 'root',
        label: t('Model root'),
        kind: 'ROOT',
        badges: [
            { id: 'classes', label: `${t('Classes')} ${metamodel.classes.length}` },
            { id: 'enums', label: `${t('Enumerations')} ${metamodel.enums.length}` },
            { id: 'relations', label: `${t('Relations')} ${metamodel.relations.length}` },
        ],
        children: [...rootChildren, ...enumChildren],
    };
}

function buildReachableClassNode(
    item: MetaClass,
    relationsBySource: Map<string, MetaRelation[]>,
    classById: Map<string, MetaClass>,
    className: Map<string, string>,
    t: (value: string) => string,
    visited: Set<string>,
): TreeNode {
    const nextVisited = new Set(visited);
    nextVisited.add(item.id);
    const outgoingRelations = relationsBySource.get(item.id) ?? [];
    const attributes = item.attributes.map(attribute => buildAttributeNode(attribute, className));
    const relationChildren = outgoingRelations.map(relation => buildRelationNode(relation, relationsBySource, classById, className, t, nextVisited));
    const children = [...attributes, ...relationChildren];
    return {
        id: item.id,
        kind: 'CLASS',
        label: `${item.name} · ${t(item.kind)}`,
        badges: [
            { id: `${item.id}-attributes-count`, label: `${t('Attributes')} ${item.attributes.length}` },
            { id: `${item.id}-relations-count`, label: `${t('Relations')} ${outgoingRelations.length}` },
        ],
        children: children.length > 0 ? children : [{ id: `${item.id}-empty`, kind: 'EMPTY', label: t('No visible features') }],
    };
}

function buildAttributeNode(attribute: MetaAttribute, className: Map<string, string>): TreeNode {
    return {
        id: attribute.id,
        kind: 'ATTRIBUTE',
        label: `${attribute.name}: ${typeName(attribute.type, className)} ${multiplicity(attribute.multiplicity.lowerBound, attribute.multiplicity.upperBound)}`,
    };
}

function buildRelationNode(
    relation: MetaRelation,
    relationsBySource: Map<string, MetaRelation[]>,
    classById: Map<string, MetaClass>,
    className: Map<string, string>,
    t: (value: string) => string,
    visited: Set<string>,
): TreeNode {
    const targetClass = classById.get(relation.targetTypeId);
    const canExpandTarget = targetClass !== undefined && !visited.has(targetClass.id);
    const nextVisited = new Set(visited);
    if (targetClass !== undefined) {
        nextVisited.add(targetClass.id);
    }
    return {
        id: relation.id,
        kind: 'RELATION',
        label: `${relation.name || t('relation')} → ${className.get(relation.targetTypeId) ?? relation.targetTypeId}${relation.targetMultiplicity ? ` ${multiplicity(relation.targetMultiplicity.lowerBound, relation.targetMultiplicity.upperBound)}` : ''}`,
        children: canExpandTarget
            ? [buildReachableClassNode(targetClass, relationsBySource, classById, className, t, nextVisited)]
            : undefined,
    };
}

function buildEnumNode(item: MetaEnum, t: (value: string) => string): TreeNode {
    return {
        id: item.id,
        kind: 'ENUM',
        label: `${item.name} · ${t('ENUM')}`,
        badges: [{ id: `${item.id}-literal-count`, label: `${t('Literals')} ${item.literals.length}` }],
        children: item.literals.length === 0
            ? [{ id: `${item.id}-empty`, kind: 'EMPTY', label: t('No literals') }]
            : item.literals.map(literal => ({ id: literal.id, kind: 'LITERAL', label: literal.name })),
    };
}

function MetamodelTreeNode({ node }: { node: TreeNode; }) {
    return <li className={`grammar-metamodel-tree-item kind-${(node.kind ?? 'EMPTY').toLowerCase()}`}>
        <div className="grammar-metamodel-tree-node">
            <strong>{node.label}</strong>
            {node.badges && node.badges.length > 0 && <span className="grammar-metamodel-tree-badges">{node.badges.map(badge => <span key={badge.id} className="grammar-metamodel-tree-badge">{badge.label}</span>)}</span>}
        </div>
        {node.children && node.children.length > 0 && <ul className="grammar-metamodel-tree-children">{node.children.map(child => <MetamodelTreeNode key={child.id} node={child} />)}</ul>}
    </li>;
}

function typeName(reference: MetaTypeReference, names: Map<string, string>): string {
    return names.get(reference.targetId) ?? (reference.externalName || reference.targetId);
}

function multiplicity(lower: number, upper: number | null): string { return `[${lower}..${upper === null ? '*' : upper}]`; }
