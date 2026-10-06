import { HydraButton } from '../design-system/HydraButton';
import type { MetaModel } from '../metamodel-designer/types';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import type { RepresentationDefinition } from './types';

interface Props {
    representation: RepresentationDefinition;
    metamodel: MetaModel;
    onAddClass: (classId: string, className: string) => void;
    onAddRelation: (relationId: string) => void;
    onEditNode: (mappingId: string) => void;
    onEditEdge: (mappingId: string) => void;
}

/** High-level entry point from semantic metamodel elements to their visual mappings. */
export function RepresentationElementMappingPanel({ representation, metamodel, onAddClass, onAddRelation, onEditNode, onEditEdge }: Props) {
    const { t } = useHydraLanguage();
    const supportsNodes = ['DIAGRAM', 'TREE', 'TABLE', 'MATRIX'].includes(representation.kind);
    const supportsEdges = ['DIAGRAM', 'MATRIX'].includes(representation.kind);
    return <>
        {supportsNodes ? <section className="hydra-designer-section editor-conditional-section editor-representation-elements"><header><div><h4>{t('Elements shown in this view')}</h4><p>{t('Choose any metamodel class or interface. If it is already shown, open its visual settings directly; otherwise add its representation first.')}</p></div></header>
            {metamodel.classes.length === 0 ? <div className="editor-context-note"><strong>{t('No metamodel classes available')}</strong><span>{t('Add or import classes in the metamodel before defining their appearance.')}</span></div> : <div className="class-tool-list editor-representation-element-list">{metamodel.classes.map(metaClass => {
                const mapping = representation.nodeMappings.find(item => item.targetClassId === metaClass.id);
                const concrete = metaClass.kind === 'CLASS';
                return <HydraButton type="button" className={mapping === undefined ? '' : 'configured'} key={metaClass.id} onClick={() => mapping === undefined ? onAddClass(metaClass.id, metaClass.name) : onEditNode(mapping.id)}>
                    <span><strong>{metaClass.name || metaClass.id}</strong><small>{t(metaClass.kind === 'CLASS' ? 'Concrete class' : metaClass.kind === 'ABSTRACT_CLASS' ? 'Abstract class' : metaClass.kind === 'INTERFACE' ? 'Interface' : 'Metamodel element')}</small></span>
                    <em>{mapping === undefined ? `+ ${t('Show in this view')}` : t('Edit appearance')}</em>{!concrete && mapping === undefined && <small>{t('Appearance only; no creation tool will be generated.')}</small>}
                </HydraButton>;
            })}</div>}
        </section> : <section className="editor-context-note"><strong>{t('No node mappings required')}</strong><span>{t(representation.kind === 'FORM' ? 'A form representation is configured through generated forms and fields.' : 'This representation type does not use graphical node mappings.')}</span></section>}
        {supportsEdges && <section className="hydra-designer-section editor-conditional-section editor-representation-elements"><header><div><h4>{t('Relationships shown in this view')}</h4><p>{t('Choose a metamodel relationship to show it, or open its connection appearance when it is already configured.')}</p></div></header>
            {metamodel.relations.length === 0 ? <div className="editor-context-note"><strong>{t('No metamodel relationships available')}</strong><span>{t('Add relationships to the metamodel before defining connection appearance.')}</span></div> : <div className="class-tool-list editor-representation-element-list">{metamodel.relations.map(relation => {
                const mapping = representation.edgeMappings.find(item => item.relationId === relation.id);
                return <HydraButton type="button" className={mapping === undefined ? '' : 'configured'} key={relation.id} onClick={() => mapping === undefined ? onAddRelation(relation.id) : onEditEdge(mapping.id)}>
                    <span><strong>{relation.name || relation.id}</strong><small>{className(metamodel, relation.sourceTypeId)} → {className(metamodel, relation.targetTypeId)}</small></span>
                    <em>{mapping === undefined ? `+ ${t('Show in this view')}` : t('Edit connection appearance')}</em>
                </HydraButton>;
            })}</div>}
        </section>}
    </>;
}

function className(metamodel: MetaModel, classId: string): string {
    return metamodel.classes.find(item => item.id === classId)?.name ?? classId;
}
