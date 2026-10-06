import { HydraButton } from '../design-system/HydraButton';
import { useMemo, useState } from 'react';
import type { MetaModel } from '../metamodel-designer/types';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import type { EditorDefinitionModel, EdgeMapping, NodeMapping, RepresentationDefinition } from './types';
import { EDGE_LINE_OPTIONS, NODE_SHAPE_OPTIONS, REPRESENTATION_KIND_OPTIONS } from './editorDefinitionPresentationOptions';

interface Props {
    model: EditorDefinitionModel;
    metamodel: MetaModel;
    onAddClassRepresentation: (classId: string, className: string, representationId: string) => void;
    onAddRelationRepresentation: (relationId: string, representationId: string) => void;
    onEditNodeRepresentation: (representationId: string, mappingId: string) => void;
    onEditEdgeRepresentation: (representationId: string, mappingId: string) => void;
    onCreateRepresentation: () => void;
}

type ElementTarget = { kind: 'CLASS' | 'RELATION'; id: string };
const NODE_KINDS = new Set<RepresentationDefinition['kind']>(['DIAGRAM', 'TREE', 'TABLE', 'MATRIX']);
const EDGE_KINDS = new Set<RepresentationDefinition['kind']>(['DIAGRAM', 'MATRIX']);

export function ElementRepresentationDesigner({ model, metamodel, onAddClassRepresentation, onAddRelationRepresentation, onEditNodeRepresentation, onEditEdgeRepresentation, onCreateRepresentation }: Props) {
    const { t } = useHydraLanguage();
    const firstTarget: ElementTarget | null = metamodel.classes[0] !== undefined ? { kind: 'CLASS', id: metamodel.classes[0].id } : metamodel.relations[0] !== undefined ? { kind: 'RELATION', id: metamodel.relations[0].id } : null;
    const [target, setTarget] = useState<ElementTarget | null>(firstTarget);
    const [filter, setFilter] = useState('');
    const normalized = filter.trim().toLowerCase();
    const classes = useMemo(() => metamodel.classes.filter(item => normalized === '' || [item.name, item.id, item.kind].some(value => value.toLowerCase().includes(normalized))), [metamodel.classes, normalized]);
    const relations = useMemo(() => metamodel.relations.filter(item => normalized === '' || [item.name, item.id, className(metamodel, item.sourceTypeId), className(metamodel, item.targetTypeId)].some(value => value.toLowerCase().includes(normalized))), [metamodel, normalized]);
    const selectedClass = target?.kind === 'CLASS' ? metamodel.classes.find(item => item.id === target.id) ?? null : null;
    const selectedRelation = target?.kind === 'RELATION' ? metamodel.relations.find(item => item.id === target.id) ?? null : null;

    return <section className="editor-element-representations" aria-label={t('Element representations')}>
        <header className="editor-element-representations-header">
            <div><h3>{t('Element representations')}</h3><p>{t('Define how each metamodel element appears in every editor view. The same class or relationship can use a different appearance in each representation.')}</p></div>
            <HydraButton type="button" onClick={onCreateRepresentation}>+ {t('New representation')}</HydraButton>
        </header>
        <div className="editor-element-representations-layout">
            <aside className="editor-element-representations-list">
                <label className="editor-element-representations-search"><span>{t('Find metamodel element')}</span><input value={filter} onChange={event => setFilter(event.target.value)} placeholder={t('Search classes and relationships…')} /></label>
                <section><header><strong>{t('Classes')}</strong><span>{metamodel.classes.length}</span></header>{classes.map(metaClass => {
                    const count = model.representations.filter(rep => rep.nodeMappings.some(mapping => mapping.targetClassId === metaClass.id)).length;
                    return <HydraButton type="button" aria-label={`${metaClass.name} ${t(classKindLabel(metaClass.kind))} ${count}/${compatibleNodeViews(model)}`} className={target?.kind === 'CLASS' && target.id === metaClass.id ? 'selected' : ''} key={metaClass.id} onClick={() => setTarget({ kind: 'CLASS', id: metaClass.id })}>
                        <span><strong>{metaClass.name}</strong><small>{t(classKindLabel(metaClass.kind))}</small></span><em>{count}/{compatibleNodeViews(model)}</em>
                    </HydraButton>;
                })}</section>
                <section><header><strong>{t('Relationships')}</strong><span>{metamodel.relations.length}</span></header>{relations.map(relation => {
                    const count = model.representations.filter(rep => rep.edgeMappings.some(mapping => mapping.relationId === relation.id)).length;
                    return <HydraButton type="button" aria-label={`${relation.name || relation.id} ${count}/${compatibleEdgeViews(model)} ${className(metamodel, relation.sourceTypeId)} → ${className(metamodel, relation.targetTypeId)}`} className={target?.kind === 'RELATION' && target.id === relation.id ? 'selected' : ''} key={relation.id} onClick={() => setTarget({ kind: 'RELATION', id: relation.id })}>
                        <span><strong>{relation.name || relation.id}</strong><small>{className(metamodel, relation.sourceTypeId)} → {className(metamodel, relation.targetTypeId)}</small></span><em>{count}/{compatibleEdgeViews(model)}</em>
                    </HydraButton>;
                })}</section>
            </aside>
            <main className="editor-element-representations-detail">
                {selectedClass !== null && <ClassRepresentationDetail model={model} classId={selectedClass.id} classNameValue={selectedClass.name} classKind={selectedClass.kind} onAdd={onAddClassRepresentation} onEdit={onEditNodeRepresentation} t={t} />}
                {selectedRelation !== null && <RelationRepresentationDetail model={model} metamodel={metamodel} relationId={selectedRelation.id} onAdd={onAddRelationRepresentation} onEdit={onEditEdgeRepresentation} t={t} />}
                {target === null && <div className="hydra-empty-state"><strong>{t('No metamodel elements')}</strong><span>{t('Add classes or relationships to the metamodel before defining their visual representation.')}</span></div>}
            </main>
        </div>
    </section>;
}

function ClassRepresentationDetail({ model, classId, classNameValue, classKind, onAdd, onEdit, t }: { model: EditorDefinitionModel; classId: string; classNameValue: string; classKind: string; onAdd: Props['onAddClassRepresentation']; onEdit: Props['onEditNodeRepresentation']; t: (value: string) => string }) {
    const configured = model.representations.filter(rep => rep.nodeMappings.some(mapping => mapping.targetClassId === classId)).length;
    return <>
        <header className="editor-element-detail-heading"><div><span>{t('Metamodel class')}</span><h4>{classNameValue}</h4><p>{t('Choose the views where this element appears, then edit its shape, image, size, label, container behavior and conditional styles.')}</p></div><strong>{configured} {t('configured view(s)')}</strong></header>
        {classKind !== 'CLASS' && <div className="editor-context-note"><strong>{t('Inherited representation')}</strong><span>{t('This abstract or interface mapping can provide the appearance inherited by compatible concrete classes. No creation tool is generated for an abstract element.')}</span></div>}
        <div className="editor-element-view-grid">{model.representations.map(rep => {
            const mapping = rep.nodeMappings.find(item => item.targetClassId === classId);
            return <NodeViewCard key={rep.id} representation={rep} mapping={mapping} supported={NODE_KINDS.has(rep.kind)} onAdd={() => onAdd(classId, classNameValue, rep.id)} onEdit={id => onEdit(rep.id, id)} t={t} />;
        })}</div>
        {model.representations.length === 0 && <div className="hydra-empty-state"><strong>{t('No editor representations')}</strong><span>{t('Create a representation first, then choose how this metamodel element appears in it.')}</span></div>}
    </>;
}

function RelationRepresentationDetail({ model, metamodel, relationId, onAdd, onEdit, t }: { model: EditorDefinitionModel; metamodel: MetaModel; relationId: string; onAdd: Props['onAddRelationRepresentation']; onEdit: Props['onEditEdgeRepresentation']; t: (value: string) => string }) {
    const relation = metamodel.relations.find(item => item.id === relationId);
    if (relation === undefined) return null;
    const configured = model.representations.filter(rep => rep.edgeMappings.some(mapping => mapping.relationId === relationId)).length;
    return <>
        <header className="editor-element-detail-heading"><div><span>{t('Metamodel relationship')}</span><h4>{relation.name || relation.id}</h4><p>{className(metamodel, relation.sourceTypeId)} → {className(metamodel, relation.targetTypeId)} · {t('Choose the views where this relationship appears, then edit routing, line style, endpoints, label and conditional styles.')}</p></div><strong>{configured} {t('configured view(s)')}</strong></header>
        <div className="editor-element-view-grid">{model.representations.map(rep => {
            const mapping = rep.edgeMappings.find(item => item.relationId === relationId);
            return <EdgeViewCard key={rep.id} representation={rep} mapping={mapping} supported={EDGE_KINDS.has(rep.kind)} onAdd={() => onAdd(relationId, rep.id)} onEdit={id => onEdit(rep.id, id)} t={t} />;
        })}</div>
    </>;
}

function NodeViewCard({ representation, mapping, supported, onAdd, onEdit, t }: { representation: RepresentationDefinition; mapping?: NodeMapping; supported: boolean; onAdd: () => void; onEdit: (mappingId: string) => void; t: (value: string) => string }) {
    return <article className={`editor-element-view-card${mapping === undefined ? '' : ' configured'}`}>
        <header><div><strong>{representation.name}</strong><small>{t(representationKindLabel(representation.kind))}</small></div><span>{mapping === undefined ? t('Not shown in this view') : t('Shown in this view')}</span></header>
        {mapping !== undefined ? <><dl><div><dt>{t('Appearance')}</dt><dd>{t(nodeShapeLabel(mapping.shape))}</dd></div><div><dt>{t('Size')}</dt><dd>{mapping.defaultWidth} × {mapping.defaultHeight}</dd></div><div><dt>{t('Container')}</dt><dd>{mapping.container ? t('Yes') : t('No')}</dd></div><div><dt>{t('Conditional styles')}</dt><dd>{mapping.conditionalStyles?.length ?? 0}</dd></div></dl><HydraButton type="button" onClick={() => onEdit(mapping.id)}>{t('Edit element appearance')}</HydraButton></> : supported ? <><p>{t('This element is not shown in this view yet.')}</p><HydraButton type="button" onClick={onAdd}>+ {t('Show in this view')}</HydraButton></> : <p>{t('This view type does not use element node representations.')}</p>}
    </article>;
}

function EdgeViewCard({ representation, mapping, supported, onAdd, onEdit, t }: { representation: RepresentationDefinition; mapping?: EdgeMapping; supported: boolean; onAdd: () => void; onEdit: (mappingId: string) => void; t: (value: string) => string }) {
    return <article className={`editor-element-view-card${mapping === undefined ? '' : ' configured'}`}>
        <header><div><strong>{representation.name}</strong><small>{t(representationKindLabel(representation.kind))}</small></div><span>{mapping === undefined ? t('Not shown in this view') : t('Shown in this view')}</span></header>
        {mapping !== undefined ? <><dl><div><dt>{t('Routing')}</dt><dd>{t(lineTypeLabel(mapping.lineType))}</dd></div><div><dt>{t('Conditional styles')}</dt><dd>{mapping.conditionalStyles?.length ?? 0}</dd></div></dl><HydraButton type="button" onClick={() => onEdit(mapping.id)}>{t('Edit relationship appearance')}</HydraButton></> : supported ? <><p>{t('This relationship is not shown in this view yet.')}</p><HydraButton type="button" onClick={onAdd}>+ {t('Show in this view')}</HydraButton></> : <p>{t('This view type does not use relationship connections.')}</p>}
    </article>;
}

function compatibleNodeViews(model: EditorDefinitionModel): number { return model.representations.filter(rep => NODE_KINDS.has(rep.kind)).length; }
function compatibleEdgeViews(model: EditorDefinitionModel): number { return model.representations.filter(rep => EDGE_KINDS.has(rep.kind)).length; }
function className(metamodel: MetaModel, id: string): string { return metamodel.classes.find(item => item.id === id)?.name ?? id; }
function classKindLabel(kind: string): string { return ({ CLASS: 'Concrete class', ABSTRACT_CLASS: 'Abstract class', INTERFACE: 'Interface' } as Record<string, string>)[kind] ?? kind; }
function representationKindLabel(kind: RepresentationDefinition['kind']): string { return REPRESENTATION_KIND_OPTIONS.find(option => option.value === kind)?.label ?? kind; }
function nodeShapeLabel(shape: NodeMapping['shape']): string { return NODE_SHAPE_OPTIONS.find(option => option.value === shape)?.label ?? shape; }
function lineTypeLabel(lineType: EdgeMapping['lineType']): string { return EDGE_LINE_OPTIONS.find(option => option.value === lineType)?.label ?? lineType; }
