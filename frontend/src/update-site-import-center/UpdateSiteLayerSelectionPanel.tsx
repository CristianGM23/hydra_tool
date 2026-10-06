import { useMemo, useState } from 'react';
import { HydraButton, HydraCheckbox, HydraFormField, HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { UpdateSiteConfidence, UpdateSiteImportAnalysis, UpdateSiteLayerSelection } from './types';
import { allLayerSelection, emptyLayerSelection, recommendedLayerSelection } from './updateSiteLayerSelection';

interface Props {
    analysis: UpdateSiteImportAnalysis;
    selection: UpdateSiteLayerSelection;
    onChange: (selection: UpdateSiteLayerSelection) => void;
}

type GroupId = 'PALETTES' | 'REPRESENTATIONS' | 'MAPPINGS' | 'SEMANTIC';

export function UpdateSiteLayerSelectionPanel({ analysis, selection, onChange }: Props) {
    const { t } = useHydraLanguage();
    const [filter, setFilter] = useState('');
    const [expanded, setExpanded] = useState<Set<GroupId>>(new Set(['PALETTES', 'REPRESENTATIONS', 'MAPPINGS', 'SEMANTIC']));
    const normalizedFilter = filter.trim().toLowerCase();
    const layers = analysis.recoveredLayers;
    const semantics = useMemo(() => analysis.semanticRecovery?.elements ?? [], [analysis.semanticRecovery?.elements]);
    const selectedCount = selection.paletteGroupIds.length + selection.paletteToolIds.length
        + selection.representationIds.length + selection.mappingIds.length + selection.semanticElementIds.length;

    const visible = useMemo(() => ({
        palettes: layers.palettes.filter(group => matches(group.label, group.sourcePath, normalizedFilter)
            || group.tools.some(tool => matches(tool.label, tool.targetElement, normalizedFilter))),
        representations: layers.representations.filter(item => matches(item.name, item.technology, normalizedFilter)),
        mappings: layers.mappings.filter(item => matches(item.semanticElement, item.visualElement, normalizedFilter)),
        semantics: semantics.filter(item => matches(item.label || item.id, item.purpose, normalizedFilter))
    }), [layers, normalizedFilter, semantics]);

    function replace(key: keyof UpdateSiteLayerSelection, ids: string[]): void {
        onChange({ ...selection, [key]: Array.from(new Set(ids)), explicit: true });
    }
    function toggle(key: keyof UpdateSiteLayerSelection, id: string, checked: boolean): void {
        const current = selection[key];
        if (!Array.isArray(current)) return;
        replace(key, checked ? [...current, id] : current.filter(value => value !== id));
    }
    function toggleGroup(groupId: string, checked: boolean): void {
        const group = layers.palettes.find(item => item.id === groupId);
        const toolIds = group?.tools.map(item => item.id) ?? [];
        onChange({
            ...selection,
            paletteGroupIds: Array.from(new Set(checked
                ? [...selection.paletteGroupIds, groupId]
                : selection.paletteGroupIds.filter(value => value !== groupId))),
            paletteToolIds: Array.from(new Set(checked
                ? [...selection.paletteToolIds, ...toolIds]
                : selection.paletteToolIds.filter(value => !toolIds.includes(value)))),
            explicit: true
        });
    }
    function toggleExpanded(id: GroupId): void {
        setExpanded(current => {
            const next = new Set(current);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });
    }

    return <section className="update-site-layer-selection">
        <header className="update-site-layer-selection-header">
            <div>
                <h4>{t('Recovered layer selection')}</h4>
                <p>{t('Select individual tools, mappings, representations and semantic capabilities before previewing the editor.')}</p>
            </div>
            <HydraTooltip label="About selective materialization" title="Selective materialization">
                <p>{t('Only checked drafts are converted into editable HYDRA editor artifacts.')}</p>
                <p>{t('Recommended selection excludes unrepresentable semantics and drafts with explicit diagnostics.')}</p>
            </HydraTooltip>
        </header>
        <div className="update-site-selection-toolbar">
            <HydraFormField label={t('Filter recovered layers')} targetId="layer-selection-filter"
                help={t('Searches labels, target elements, technologies and source paths without changing the selection.')}>
                <input id="layer-selection-filter" value={filter} onChange={event => setFilter(event.target.value)} placeholder={t('Search tools, mappings or sources')} />
            </HydraFormField>
            <div className="update-site-selection-presets" aria-label={t('Layer selection presets')}>
                <HydraButton compact size="small" onClick={() => onChange(recommendedLayerSelection(analysis))}>{t('Recommended')}</HydraButton>
                <HydraButton compact size="small" onClick={() => onChange(allLayerSelection(analysis))}>{t('Select all')}</HydraButton>
                <HydraButton compact size="small" variant="quiet" onClick={() => onChange(emptyLayerSelection())}>{t('Clear')}</HydraButton>
            </div>
            <span className="update-site-selection-total">{selectedCount} {t('selected')}</span>
        </div>

        <div className="update-site-selection-groups">
            <SelectionGroup id="PALETTES" title={t('Palette groups and tools')}
                summary={`${selection.paletteToolIds.length} ${t('tool(s) selected')}`} open={expanded.has('PALETTES')}
                onToggle={() => toggleExpanded('PALETTES')}>
                {visible.palettes.map(group => {
                    const groupToolIds = group.tools.map(item => item.id);
                    const selectedTools = groupToolIds.filter(id => selection.paletteToolIds.includes(id));
                    const checked = selection.paletteGroupIds.includes(group.id) && selectedTools.length === groupToolIds.length;
                    return <article className="update-site-selectable-card" key={group.id}>
                        <HydraCheckbox checked={checked} label={group.label}
                            description={`${group.tools.length} ${t('tool(s)')} · ${group.sourcePath || t('source not recorded')}`}
                            onChange={event => toggleGroup(group.id, event.target.checked)} />
                        <div className="update-site-selectable-children">
                            {group.tools.filter(tool => matches(tool.label, tool.targetElement, normalizedFilter)).map(tool =>
                                <HydraCheckbox checked={selection.paletteToolIds.includes(tool.id)} key={tool.id}
                                    label={tool.label} description={`${t(tool.kind)} · ${t('target')} ${tool.targetElement || t('unresolved')} · ${t(confidenceLabel(tool.confidence))}`}
                                    onChange={event => {
                                        const checkedTool = event.target.checked;
                                        const nextToolIds = checkedTool
                                            ? Array.from(new Set([...selection.paletteToolIds, tool.id]))
                                            : selection.paletteToolIds.filter(value => value !== tool.id);
                                        const groupStillSelected = group.tools.some(candidate => nextToolIds.includes(candidate.id));
                                        onChange({
                                            ...selection,
                                            paletteGroupIds: checkedTool || groupStillSelected
                                                ? Array.from(new Set([...selection.paletteGroupIds, group.id]))
                                                : selection.paletteGroupIds.filter(value => value !== group.id),
                                            paletteToolIds: nextToolIds,
                                            explicit: true
                                        });
                                    }} />)}
                        </div>
                    </article>;
                })}
            </SelectionGroup>

            <SelectionGroup id="REPRESENTATIONS" title={t('Representations')}
                summary={`${selection.representationIds.length} ${t('selected')}`} open={expanded.has('REPRESENTATIONS')}
                onToggle={() => toggleExpanded('REPRESENTATIONS')}>
                {visible.representations.map(item => <SelectableDraft checked={selection.representationIds.includes(item.id)}
                    confidence={item.confidence} description={`${item.technology || t('Unknown technology')} · ${t(item.kind)} · ${item.sourcePath}`}
                    key={item.id} label={item.name} onChange={checked => toggle('representationIds', item.id, checked)} />)}
            </SelectionGroup>

            <SelectionGroup id="MAPPINGS" title={t('Semantic and visual mappings')}
                summary={`${selection.mappingIds.length} ${t('selected')}`} open={expanded.has('MAPPINGS')}
                onToggle={() => toggleExpanded('MAPPINGS')}>
                {visible.mappings.map(item => <SelectableDraft checked={selection.mappingIds.includes(item.id)}
                    confidence={item.confidence} description={`${t(item.mappingKind)} · ${item.semanticElement} → ${item.visualElement || t('visual element unresolved')}`}
                    key={item.id} label={item.id} warning={item.diagnostic}
                    onChange={checked => toggle('mappingIds', item.id, checked)} />)}
            </SelectionGroup>

            <SelectionGroup id="SEMANTIC" title={t('Generated editor semantics')}
                summary={`${selection.semanticElementIds.length} ${t('selected')}`} open={expanded.has('SEMANTIC')}
                onToggle={() => toggleExpanded('SEMANTIC')}>
                {visible.semantics.map(item => <SelectableDraft checked={selection.semanticElementIds.includes(item.id)}
                    confidence={item.confidence} description={`${t(human(item.kind))} · ${item.neutralTarget || t('No neutral target')}`}
                    disabled={!item.representable} key={item.id} label={item.label || item.id}
                    warning={!item.representable ? t('This extension is preserved for review but cannot be materialized automatically.') : item.warnings.map(t).join(' ')}
                    onChange={checked => toggle('semanticElementIds', item.id, checked)} />)}
            </SelectionGroup>
        </div>
    </section>;
}

function SelectionGroup({ id, title, summary, open, onToggle, children }: {
    id: GroupId; title: string; summary: string; open: boolean; onToggle: () => void; children: React.ReactNode;
}) {
    return <section className="update-site-selection-group" data-group={id}>
        <HydraButton aria-expanded={open} className="update-site-selection-group-toggle" onClick={onToggle} type="button">
            <span aria-hidden="true">{open ? '▾' : '▸'}</span><strong>{title}</strong><small>{summary}</small>
        </HydraButton>
        {open && <div className="update-site-selection-group-content">{children}</div>}
    </section>;
}

function SelectableDraft({ checked, label, description, confidence, warning = '', disabled = false, onChange }: {
    checked: boolean; label: string; description: string; confidence: UpdateSiteConfidence;
    warning?: string; disabled?: boolean; onChange: (checked: boolean) => void;
}) {
    return <article className={`update-site-selectable-card${disabled ? ' disabled' : ''}`}>
        <HydraCheckbox checked={checked} disabled={disabled} label={label}
            description={`${description} · ${confidenceLabel(confidence)}`} onChange={event => onChange(event.target.checked)} />
        {warning !== '' && <small className="update-site-selection-warning">{warning}</small>}
    </article>;
}

function matches(first: string, second: string, filter: string): boolean {
    if (filter === '') return true;
    return `${first} ${second}`.toLowerCase().includes(filter);
}

function confidenceLabel(value: UpdateSiteConfidence): string { return value === 'EXACT' ? 'Exact' : value === 'RECOVERED' ? 'Recovered' : value === 'DERIVED' ? 'Derived' : 'Inferred'; }
function human(value: string): string { return value.replaceAll('_', ' ').toLowerCase().replace(/^./, letter => letter.toUpperCase()); }
