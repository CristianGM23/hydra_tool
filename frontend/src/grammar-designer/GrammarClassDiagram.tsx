import { useMemo, useState } from 'react';
import { HydraButton } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { MetamodelReadOnlyCanvas } from '../metamodel-designer/MetamodelReadOnlyCanvas';
import type { MetaModel, MetaRelation, MetamodelSelection } from '../metamodel-designer/types';
import type { GrammarClassDiagramProjection, GrammarDiagramSourceRef } from './types';
import { buildGrammarDiagramView, DEFAULT_GRAMMAR_DIAGRAM_FILTERS, type GrammarDiagramFilters, type GrammarDiagramFocus } from './grammarClassDiagramModel';
import { downloadGrammarClassDiagram, type GrammarDiagramExportFormat } from './grammarClassDiagramExport';

interface Props {
    projection: GrammarClassDiagramProjection | null;
    loading?: boolean;
    error?: string;
    onNavigate: (source: GrammarDiagramSourceRef) => void;
}

/**
 * Shows the inferred grammar metamodel using the exact same canonical renderers used by
 * the Metamodel Designer. This intentionally avoids maintaining a second UML renderer.
 */
export function GrammarClassDiagram({ projection, loading = false, error = '', onNavigate }: Props) {
    const { t } = useHydraLanguage();
    const [filters, setFilters] = useState<GrammarDiagramFilters>(DEFAULT_GRAMMAR_DIAGRAM_FILTERS);
    const view = useMemo(() => projection === null ? null : buildGrammarDiagramView(projection, filters), [projection, filters]);
    const model = useMemo(() => projection === null ? null : filteredInferredMetamodel(projection.inferredMetamodel, filters), [filters, projection]);

    if (projection === null || model === null) return <section className="grammar-class-diagram empty"><strong>{t('Inferred class diagram')}</strong>{loading
        ? <><span className="grammar-visualization-spinner" aria-hidden="true"/><span>{t('Building the class diagram from the current grammar…')}</span></>
        : error !== '' ? <span>{t('The class diagram could not be built from the current draft. Review Problems for details.')}</span>
            : <span>{t('Open this view to build the class diagram automatically from the current grammar. No compilation is required.')}</span>}</section>;

    const exportDiagram = (format: GrammarDiagramExportFormat) => { if (view !== null) void downloadGrammarClassDiagram(view, format, `${projection.grammarId || 'grammar'}-inferred-metamodel`); };
    const navigateSelection = (selection: MetamodelSelection) => {
        if (selection.kind === 'RELATION') {
            const edge = projection.edges.find(item => item.id === selection.id);
            if (edge !== undefined) onNavigate(edge.source);
            return;
        }
        if (selection.kind === 'CLASS' || selection.kind === 'ENUM' || selection.kind === 'DATA_TYPE') {
            const node = projection.nodes.find(item => item.id === selection.id || item.semanticId === selection.id);
            if (node !== undefined) onNavigate(node.source);
        }
    };

    return <section className="grammar-class-diagram" aria-label={t('Inferred class diagram')}>
        <header className="grammar-diagram-toolbar">
            <div><strong>{t('Inferred class diagram')}</strong><span>{statusLabel(projection.status,t)} · {projection.nodes.length} {t('classifiers')} · {projection.edges.length} {t('relations')}</span></div>
            <div className="grammar-diagram-filters">
                <label>{t('Focus')}<select value={filters.focus} onChange={event => setFilters(current => ({ ...current, focus: event.target.value as GrammarDiagramFocus }))}><option value="ALL">{t('All')}</option><option value="CONTAINMENT">{t('Containment')}</option><option value="REFERENCES">{t('References')}</option><option value="INHERITANCE">{t('Inheritance')}</option></select></label>
                <label><input type="checkbox" checked={filters.showAttributes} onChange={event => setFilters(current => ({ ...current, showAttributes: event.target.checked }))}/>{t('Attributes')}</label>
                <label><input type="checkbox" checked={filters.showReferences} onChange={event => setFilters(current => ({ ...current, showReferences: event.target.checked }))}/>{t('References')}</label>
                <label><input type="checkbox" checked={filters.showEnums} onChange={event => setFilters(current => ({ ...current, showEnums: event.target.checked }))}/>{t('Enums')}</label>
                <label><input type="checkbox" checked={filters.showDataTypes} onChange={event => setFilters(current => ({ ...current, showDataTypes: event.target.checked }))}/>{t('Data types')}</label>
                <details className="grammar-diagram-export-menu"><summary>{t('Download diagram')}</summary><div>
                    {(['png','svg','jpeg','drawio','plantuml'] as const).map(format => <HydraButton compact type="button" key={format} onClick={event => { event.currentTarget.closest('details')?.removeAttribute('open'); exportDiagram(format); }}>{format === 'drawio' ? t('draw.io') : format === 'plantuml' ? 'PlantUML' : format.toUpperCase()}</HydraButton>)}
                </div></details>
            </div>
        </header>
        <div className="grammar-diagram-canvas shared-metamodel-renderer">
            {loading && <div className="grammar-visualization-loading" role="status"><span className="grammar-visualization-spinner" aria-hidden="true"/><span>{t('Updating the diagram from the current grammar…')}</span></div>}
            {model.classes.length + model.enums.length + model.dataTypes.length === 0
                ? <div className="grammar-diagram-empty">{t('No inferred classifiers are available for this draft.')}</div>
                : <MetamodelReadOnlyCanvas model={model} revisionKey={projection.projectionHash} allowNodeResize onSelectionChange={navigateSelection}/>} 
        </div>
    </section>;
}

function filteredInferredMetamodel(model: MetaModel, filters: GrammarDiagramFilters): MetaModel {
    const relations = model.relations.filter(relation => relationVisible(relation, filters));
    return {
        ...model,
        classes: model.classes.map(item => ({ ...item, attributes: filters.showAttributes ? item.attributes : [] })),
        enums: filters.showEnums ? model.enums : [],
        dataTypes: filters.showDataTypes ? model.dataTypes : [],
        relations,
    };
}

function relationVisible(relation: MetaRelation, filters: GrammarDiagramFilters): boolean {
    const inheritance = relation.kind === 'INHERITANCE' || relation.kind === 'IMPLEMENTATION';
    const containment = relation.kind === 'CONTAINMENT' || relation.kind === 'AGGREGATION';
    const reference = relation.kind === 'REFERENCE' || relation.kind === 'DEPENDENCY';
    if (!filters.showReferences && !inheritance) return false;
    if (filters.focus === 'CONTAINMENT') return containment;
    if (filters.focus === 'REFERENCES') return reference;
    if (filters.focus === 'INHERITANCE') return inheritance;
    return true;
}

function statusLabel(status: GrammarClassDiagramProjection['status'], t: (key: string) => string): string {
    if (status === 'COMPLETE') return t('Complete inference');
    if (status === 'PARTIAL') return t('Partial inference');
    if (status === 'LAST_KNOWN_GOOD') return t('Last valid inference');
    if (status === 'EMPTY') return t('No inferred structure');
    return t('Inference failed');
}
