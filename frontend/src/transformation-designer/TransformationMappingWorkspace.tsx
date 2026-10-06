import { useState } from 'react';
import { HydraButton, HydraCheckbox, HydraCreationModeSwitch, HydraFormField, HydraSelect, HydraTooltip } from '../design-system';
import { MetamodelStructureTree } from '../designer-ui/MetamodelStructureTree';
import type { MetamodelStructureSelection } from '../designer-ui/metamodelStructureTypes';
import type { MetaAttribute, MetaModel } from '../metamodel-designer/types';
import { ExpressionBuilder } from '../validation-designer/ExpressionBuilder';
import { GuidedConditionBuilder } from '../validation-designer/GuidedConditionBuilder';
import type { HydraCreationMode } from '../design-system';
import type { TransformationDefinitionDocument, TransformationRule, TransformationRuleDirection } from './types';
import { addDirectBinding, createGuard, createRule, mapCompatibleNames, replaceRule, sourceClassForRule, targetClassForRule } from './transformationDesignerModel';
import { TransformationBindingRow } from './TransformationBindingRow';
import { TransformationRelationBindings } from './TransformationRelationBindings';
import { useHydraLanguage } from '../i18n';

interface Props {
    document: TransformationDefinitionDocument; source: MetaModel | null; target: MetaModel | null; selectedRuleId: string;
    onSelectedRuleIdChange: (id: string) => void; onChange: (document: TransformationDefinitionDocument) => void; compact?: boolean;
}

export function TransformationMappingWorkspace({ document, source, target, selectedRuleId, onSelectedRuleIdChange, onChange, compact = false }: Props) {
    const { t } = useHydraLanguage();
    const [ruleDirection, setRuleDirection] = useState<TransformationRuleDirection>('FORWARD');
    const [sourceFilter, setSourceFilter] = useState(''); const [targetFilter, setTargetFilter] = useState('');
    const [sourceSelection, setSourceSelection] = useState<MetamodelStructureSelection | null>(null); const [targetSelection, setTargetSelection] = useState<MetamodelStructureSelection | null>(null);
    const activeDirection:TransformationRuleDirection = document.directionality === 'BIDIRECTIONAL' ? ruleDirection : 'FORWARD';
    const inputModel = activeDirection === 'REVERSE' ? target : source;
    const outputModel = activeDirection === 'REVERSE' ? source : target;
    const directionalRules = document.rules.filter(item => item.direction === activeDirection);
    const selectedRule = directionalRules.find(item => item.id === selectedRuleId) ?? directionalRules[0];
    const selectedSourceClass = sourceSelection?.kind === 'CLASS' ? inputModel?.classes.find(item => item.id === sourceSelection.id) : undefined;
    const selectedTargetClass = targetSelection?.kind === 'CLASS' ? outputModel?.classes.find(item => item.id === targetSelection.id) : undefined;
    const selectedSourceAttribute = sourceSelection?.kind === 'ATTRIBUTE' ? attributeBySelection(inputModel, sourceSelection) : undefined;
    const selectedTargetAttribute = targetSelection?.kind === 'ATTRIBUTE' ? attributeBySelection(outputModel, targetSelection) : undefined;
    const currentSourceClass = selectedRule === undefined ? undefined : sourceClassForRule(selectedRule, inputModel);
    const currentTargetClass = selectedRule === undefined ? undefined : targetClassForRule(selectedRule, outputModel);
    const canCreateRule = selectedSourceClass !== undefined && selectedTargetClass !== undefined;
    const canMapSelected = selectedRule !== undefined && selectedSourceAttribute !== undefined && selectedTargetAttribute !== undefined
        && sourceSelection?.ownerId === currentSourceClass?.id && targetSelection?.ownerId === currentTargetClass?.id;

    function changeDirection(next:TransformationRuleDirection):void {
        setRuleDirection(next); setSourceSelection(null); setTargetSelection(null);
        const first = document.rules.find(item => item.direction === next); onSelectedRuleIdChange(first?.id ?? '');
    }
    function addRule(): void { if (!canCreateRule || selectedSourceClass === undefined || selectedTargetClass === undefined) return; const rule = createRule(document, selectedSourceClass, selectedTargetClass, activeDirection); onChange({ ...document, rules: [...document.rules, rule] }); onSelectedRuleIdChange(rule.id); }
    function mapSelected(): void { if (!canMapSelected || selectedRule === undefined || selectedSourceAttribute === undefined || selectedTargetAttribute === undefined) return; onChange(replaceRule(document, addDirectBinding(selectedRule, selectedSourceAttribute, selectedTargetAttribute))); }
    const directionOptions = [
        {value:'FORWARD',label:t('Forward'),description:t('Source → target')},
        {value:'REVERSE',label:t('Backward'),description:t('Target → source')},
    ];

    return <section className={`transformation-mapping-workspace${compact ? ' compact' : ''}`}>
        <MetamodelPane title={activeDirection === 'REVERSE' ? 'Target metamodel · input' : 'Source metamodel · input'} model={inputModel} filter={sourceFilter} onFilterChange={setSourceFilter} selection={sourceSelection} onSelection={setSourceSelection} />
        <section className="transformation-mapping-center" aria-label={t('Transformation mappings')}>
            <header className="transformation-mapping-actions"><div><strong>{t('Mappings and rules')}</strong><span>{t(activeDirection === 'REVERSE' ? 'Define how target-side instances reconstruct the source side.' : 'Connect source types and then map their properties.')}</span></div><div>{document.directionality === 'BIDIRECTIONAL' && <HydraSelect ariaLabel={t('Rule direction')} value={activeDirection} options={directionOptions} onChange={value => changeDirection(value as TransformationRuleDirection)}/>}<HydraButton disabled={!canCreateRule} type="button" onClick={addRule}>{t(activeDirection === 'REVERSE' ? 'Create backward rule' : 'Create rule from selected classes')}</HydraButton><HydraButton disabled={!canMapSelected} type="button" onClick={mapSelected}>{t('Map selected properties')}</HydraButton></div></header>
            {directionalRules.length === 0 ? <div className="transformation-empty-state"><strong>{t(activeDirection === 'REVERSE' ? 'No backward rules yet' : 'No transformation rules yet')}</strong><span>{t(activeDirection === 'REVERSE' ? 'Select a class on each side and create the reverse half of a correspondence.' : 'Select one class in each metamodel and create the first correspondence.')}</span></div> : <div className="transformation-rule-list" role="list">{directionalRules.map(rule => {
                const sourceClass = sourceClassForRule(rule, inputModel); const targetClass = targetClassForRule(rule, outputModel);
                return <HydraButton className={rule.id === selectedRule?.id ? 'active transformation-rule-card' : 'transformation-rule-card'} key={rule.id} type="button" onClick={() => onSelectedRuleIdChange(rule.id)}><strong>{sourceClass?.name ?? rule.sourceTypeId}<span aria-hidden="true"> → </span>{targetClass?.name ?? rule.targets[0]?.typeId}</strong><span>{rule.bindings.length} {t('mapping(s)')}{rule.guard === undefined ? '' : ` · ${t('conditional')}`}{rule.correspondenceId === '' ? '' : ` · ↔ ${t('paired')}`}</span></HydraButton>;
            })}</div>}
            {selectedRule !== undefined && inputModel !== null && outputModel !== null && <TransformationRuleEditor document={document} rule={selectedRule} source={inputModel} target={outputModel} onChange={rule => onChange(replaceRule(document, rule))} onDelete={() => { const nextRules = document.rules.filter(item => item.id !== selectedRule.id); onChange({ ...document, rules: nextRules }); const nextDirectional = nextRules.find(item => item.direction === activeDirection); onSelectedRuleIdChange(nextDirectional?.id ?? ''); }} />}
        </section>
        <MetamodelPane title={activeDirection === 'REVERSE' ? 'Source metamodel · output' : 'Target metamodel · output'} model={outputModel} filter={targetFilter} onFilterChange={setTargetFilter} selection={targetSelection} onSelection={setTargetSelection} />
    </section>;
}

function MetamodelPane({ title, model, filter, onFilterChange, selection, onSelection }: { title:string; model:MetaModel|null; filter:string; onFilterChange:(value:string)=>void; selection:MetamodelStructureSelection|null; onSelection:(selection:MetamodelStructureSelection)=>void; }) {
    const { t } = useHydraLanguage();
    return <aside className="transformation-metamodel-pane"><header><div><strong>{t(title)}</strong><span>{model?.name ?? t('Not selected')}</span></div><span>{model === null ? '—' : `${model.classes.length} ${t('classes')}`}</span></header><label className="transformation-tree-filter"><span>{t('Filter structure')}</span><input value={filter} onChange={event => onFilterChange(event.target.value)} placeholder={t('Class, property or relation')} /></label><div className="transformation-tree-scroll"><MetamodelStructureTree metamodel={model} filter={filter} selection={selection} onSelect={onSelection} /></div>{selection !== null && <footer><strong>{selection.label}</strong><span>{t(selection.kind)}</span>{selection.detail && <small>{selection.detail}</small>}</footer>}</aside>;
}

function TransformationRuleEditor({ document, rule, source, target, onChange, onDelete }: { document:TransformationDefinitionDocument; rule:TransformationRule; source:MetaModel; target:MetaModel; onChange:(rule:TransformationRule)=>void; onDelete:()=>void; }) {
    const { t } = useHydraLanguage(); const [conditionMode, setConditionMode] = useState<HydraCreationMode>('GUIDED');
    const sourceClass = sourceClassForRule(rule, source); const targetClass = targetClassForRule(rule, target); const guardedRule = rule.guard === undefined ? { ...rule, guard:createGuard(rule) } : rule;
    const oppositeRules = document.rules.filter(candidate => candidate.direction !== rule.direction && candidate.correspondenceId !== '');
    const correspondenceOptions = oppositeRules.map(candidate => ({ value:candidate.correspondenceId, label:candidate.name, description:`${candidate.direction === 'FORWARD' ? t('Forward') : t('Backward')} · ${candidate.correspondenceId}` }));
    const hasCurrentCorrespondenceOption = correspondenceOptions.some(option => option.value === rule.correspondenceId);
    if (rule.correspondenceId !== '' && !hasCurrentCorrespondenceOption) correspondenceOptions.unshift({ value:rule.correspondenceId, label:t('Current correspondence'), description:rule.correspondenceId });
    return <section className="transformation-rule-editor">
        <header><div><h3>{rule.name}</h3><p>{sourceClass?.name ?? rule.sourceTypeId} → {targetClass?.name ?? rule.targets[0]?.typeId}</p></div><div><HydraCheckbox label="Enabled" checked={rule.enabled} onChange={event => onChange({ ...rule, enabled:event.target.checked })} /><HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>{t('Delete rule')}</HydraButton></div></header>
        <div className="transformation-rule-fields"><HydraFormField label="Rule name" targetId={`transformation-rule-${rule.id}-name`} required help="A readable name used in diagnostics, traces and generated transformation code." width="wide"><input id={`transformation-rule-${rule.id}-name`} value={rule.name} onChange={event => onChange({ ...rule, name:event.target.value })} /></HydraFormField>{document.directionality === 'BIDIRECTIONAL' && <HydraFormField label="Paired correspondence" targetId={`transformation-rule-${rule.id}-correspondence`} required help="Choose the rule on the opposite direction that represents the same semantic correspondence." width="wide">{correspondenceOptions.length > 0 ? <HydraSelect ariaLabel={t('Paired correspondence')} value={rule.correspondenceId} options={correspondenceOptions} onChange={value => onChange({ ...rule, correspondenceId:value })}/> : <div className="transformation-correspondence-empty">{t('Create a rule in the opposite direction to complete this correspondence.')}</div>}</HydraFormField>}</div>
        <section className="transformation-binding-editor"><header><div><div className="transformation-heading-with-help"><strong>{t('Property mappings')}</strong>{sourceClass!==undefined&&targetClass!==undefined&&<HydraTooltip label={t('Help based on the selected classes')} title={t('How to map these classes')}><p>{t('HYDRA compares the properties of the selected source and target classes and highlights exact or adaptable candidates in each target property.')}</p><p><strong>{sourceClass.name}</strong>: {sourceClass.attributes.length} {t('properties')} · <strong>{targetClass.name}</strong>: {targetClass.attributes.length} {t('properties')}.</p><p>{t('Use direct mappings for semantically equivalent values, explicit conversions when only the representation changes, and calculated expressions when the target value is derived.')}</p></HydraTooltip>}</div><span>{t('Choose direct, constant or calculated values and adapt them explicitly when needed.')}</span></div>{sourceClass !== undefined && targetClass !== undefined && <HydraButton type="button" onClick={() => onChange(mapCompatibleNames(rule, sourceClass, targetClass))}>{t('Map compatible names')}</HydraButton>}</header>{targetClass?.attributes.length === 0 ? <p>{t('The target class has no properties to map.')}</p> : <div className="transformation-binding-grid">{targetClass?.attributes.map(targetAttribute => <TransformationBindingRow key={targetAttribute.id} rule={rule} source={source} target={target} sourceClass={sourceClass} targetAttribute={targetAttribute} onChange={onChange}/>)}</div>}</section>
        <TransformationRelationBindings rule={rule} source={source} target={target} sourceClass={sourceClass} targetClass={targetClass} onChange={onChange}/>
        <section className="transformation-condition-editor"><header><div><div className="transformation-heading-with-help"><strong>{t('Application condition')}</strong>{sourceClass!==undefined&&<HydraTooltip label={t('Help for the application condition')} title={`${t('Condition for')} ${sourceClass.name}`}><p>{t('The condition is evaluated for each source element of the selected class before the rule creates a target element.')}</p><p>{sourceClass.attributes.length>0?`${t('Available properties')}: ${sourceClass.attributes.map(item=>item.name).join(', ')}.`:t('This class has no attributes; conditions can still use supported structural or contextual expressions.')}</p></HydraTooltip>}</div><span>{t('Rules are unconditional unless you define when they should apply.')}</span></div><div>{rule.guard === undefined ? <HydraButton type="button" onClick={() => onChange(guardedRule)}>{t('Add condition')}</HydraButton> : <><HydraCreationModeSwitch mode={conditionMode} onChange={setConditionMode} guidedLabel="Guided condition" advancedLabel="Advanced expression" /><HydraButton type="button" onClick={() => onChange({ ...rule, guard:undefined })}>{t('Remove condition')}</HydraButton></>}</div></header>{rule.guard !== undefined && (conditionMode === 'GUIDED' ? <GuidedConditionBuilder root={rule.guard.root} metamodel={source} contextClassId={rule.sourceTypeId} onChange={root => onChange({ ...rule, guard:{ ...rule.guard!, root } })} /> : <ExpressionBuilder expression={rule.guard} metamodel={source} onChange={guard => onChange({ ...rule, guard })} />)}</section>
        <details className="transformation-rule-technical"><summary>{t('Technical details')}</summary><dl><div><dt>{t('Rule ID')}</dt><dd><code>{rule.id}</code></dd></div><div><dt>{t('Direction')}</dt><dd><code>{rule.direction}</code></dd></div><div><dt>{t('Correspondence')}</dt><dd><code>{rule.correspondenceId || '—'}</code></dd></div><div><dt>{t('Source endpoint')}</dt><dd><code>{rule.sourceEndpointId}</code></dd></div><div><dt>{t('Target endpoint')}</dt><dd><code>{rule.targets[0]?.endpointId ?? (rule.direction === 'REVERSE' ? document.sources[0]?.id : document.targets[0]?.id)}</code></dd></div></dl></details>
    </section>;
}

function attributeBySelection(model: MetaModel | null, selection: MetamodelStructureSelection): MetaAttribute | undefined { if (model === null || selection.kind !== 'ATTRIBUTE') return undefined; return model.classes.find(item => item.id === selection.ownerId)?.attributes.find(item => item.id === selection.id); }
