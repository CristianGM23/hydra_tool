import { HydraButton } from '../design-system/HydraButton';
import { useState } from 'react';
import { ExpressionBuilder } from '../validation-designer/ExpressionBuilder';
import { createDefaultExpression, createValidationDesignerId } from '../validation-designer/validationDesignerFactory';
import type { MetaModel } from '../metamodel-designer/types';
import type { ExpressionDefinition, ExpressionModel } from '../validation/types';
import { HydraTooltip, HydraSelect } from '../design-system';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import { conditionDuplicatesAnotherRule, duplicateConditionalStyleRuleIds } from './conditionalStyleConditions';
import { labelCss, resolveNodeNotationStyle } from './editorNotationStyle';
import { EditorNodeShapeFrame } from './EditorNodeShapeFrame';
import type { ConditionalStyleRule, NodeMapping } from './types';
import './conditional-style-editor.css';
import './conditional-style-guided.css';

interface Props {
    mapping: NodeMapping;
    metamodel: MetaModel;
    expressionModel: ExpressionModel;
    onMappingChange: (mapping: NodeMapping) => void;
    onExpressionModelChange: (model: ExpressionModel) => void;
}

type StyleGroup = 'REPRESENTATION' | 'SHAPE' | 'BORDER' | 'LABEL';
type Field = { key:string; label:string; help:string; group:StyleGroup; kind:'color'|'number'|'boolean'|'select'; defaultValue:string; min?:number; max?:number; step?:number; options?:Array<{value:string;label:string}> };
const STYLE_FIELDS: Field[] = [
    { key:'shape', label:'Representation shape', help:'Changes the geometric representation while this condition is true. The semantic metamodel element does not change.', group:'REPRESENTATION', kind:'select', defaultValue:'RECTANGLE', options:[
        {value:'RECTANGLE',label:'Rectangle'},{value:'ROUNDED_RECTANGLE',label:'Rounded rectangle'},{value:'ELLIPSE',label:'Ellipse'},{value:'DIAMOND',label:'Diamond'},
        {value:'TRIANGLE',label:'Triangle'},{value:'PENTAGON',label:'Pentagon'},{value:'HEXAGON',label:'Hexagon'},{value:'OCTAGON',label:'Octagon'},
        {value:'PARALLELOGRAM',label:'Parallelogram'},{value:'TRAPEZOID',label:'Trapezoid'},{value:'CYLINDER',label:'Cylinder'},{value:'DOCUMENT',label:'Document'},
        {value:'CLOUD',label:'Cloud'},{value:'STAR',label:'Star'},{value:'CROSS',label:'Cross'}
    ] },
    { key:'fillColor', label:'Fill color', help:'Background color used by the node while this rule is active.', group:'SHAPE', kind:'color', defaultValue:'#DBEAFE' },
    { key:'opacity', label:'Opacity', help:'Controls how transparent the complete node becomes. 1 means fully visible.', group:'SHAPE', kind:'number', defaultValue:'1', min:.1, max:1, step:.1 },
    { key:'borderRadius', label:'Corner radius', help:'Rounds the corners of rectangular node shapes.', group:'SHAPE', kind:'number', defaultValue:'8', min:0, step:1 },
    { key:'strokeColor', label:'Border color', help:'Color of the node border while this rule is active.', group:'BORDER', kind:'color', defaultValue:'#2563EB' },
    { key:'strokeWidth', label:'Border width', help:'Thickness of the node border in visual units.', group:'BORDER', kind:'number', defaultValue:'2', min:.5, step:.5 },
    { key:'borderStyle', label:'Border style', help:'Line pattern used for the node border.', group:'BORDER', kind:'select', defaultValue:'SOLID', options:[{value:'SOLID',label:'Solid'},{value:'DASHED',label:'Dashed'},{value:'DOTTED',label:'Dotted'},{value:'DOUBLE',label:'Double'},{value:'NONE',label:'Hidden'}] },
    { key:'labelColor', label:'Label color', help:'Text color used by the node label.', group:'LABEL', kind:'color', defaultValue:'#0F172A' },
    { key:'labelBackgroundColor', label:'Label background', help:'Background color placed behind the node label.', group:'LABEL', kind:'color', defaultValue:'#FFFFFF' },
    { key:'labelBackgroundTransparent', label:'Transparent label background', help:'Removes the label background while this condition is true.', group:'LABEL', kind:'boolean', defaultValue:'false' },
    { key:'labelPosition', label:'Label position', help:'Moves the label to the same inside or outside positions available in the base representation.', group:'LABEL', kind:'select', defaultValue:'CENTER', options:[
        {value:'TOP',label:'Inside top'},{value:'CENTER',label:'Centered'},{value:'BOTTOM',label:'Inside bottom'},{value:'OUTSIDE_TOP',label:'Outside top'},{value:'OUTSIDE_BOTTOM',label:'Outside bottom'}
    ] },
    { key:'labelFontSize', label:'Label font size', help:'Changes the label text size in pixels while the condition is true.', group:'LABEL', kind:'number', defaultValue:'14', min:7, max:200, step:1 },
    { key:'labelFontWeight', label:'Label weight', help:'Changes the numeric font weight of the node label while the condition is true.', group:'LABEL', kind:'number', defaultValue:'600', min:100, max:900, step:100 },
    { key:'labelPadding', label:'Label padding', help:'Changes the internal spacing around the node label in pixels.', group:'LABEL', kind:'number', defaultValue:'4', min:0, max:100, step:1 },
    { key:'labelVisible', label:'Show label', help:'Shows or hides the node label while the condition is true.', group:'LABEL', kind:'boolean', defaultValue:'true' }
];

const STYLE_PRESETS: Array<{ id:string; label:string; description:string; styles:Record<string,string> }> = [
    { id:'HIGHLIGHT', label:'Highlight', description:'Emphasize an element without implying an error.', styles:{ fillColor:'#DBEAFE', strokeColor:'#2563EB', strokeWidth:'2' } },
    { id:'SUCCESS', label:'Success', description:'Use for completed, valid or positive states.', styles:{ fillColor:'#DCFCE7', strokeColor:'#16A34A', strokeWidth:'2' } },
    { id:'WARNING', label:'Warning', description:'Use for states that require attention but are still usable.', styles:{ fillColor:'#FEF3C7', strokeColor:'#D97706', strokeWidth:'2' } },
    { id:'ERROR', label:'Error', description:'Use for invalid, blocked or failed states.', styles:{ fillColor:'#FEE2E2', strokeColor:'#DC2626', strokeWidth:'2' } },
    { id:'MUTED', label:'Muted', description:'De-emphasize secondary or inactive elements.', styles:{ fillColor:'#F1F5F9', strokeColor:'#64748B', opacity:'.7' } }
];

export function ConditionalStyleEditor({ mapping, metamodel, expressionModel, onMappingChange, onExpressionModelChange }: Props) {
    const { t } = useHydraLanguage();
    const [conditionError, setConditionError] = useState('');
    const rules = mapping.conditionalStyles ?? [];
    const duplicateRuleIds = duplicateConditionalStyleRuleIds(rules, expressionModel);
    const baseStyle = resolveNodeNotationStyle(mapping.styles);
    const baseShape = mapping.shape;

    function addRule(): void {
        const ruleId = createValidationDesignerId('conditional-style');
        const rule: ConditionalStyleRule = {
            id: ruleId,
            name: `${t('Conditional style')} ${rules.length + 1}`,
            enabled: true,
            priority: rules.length * 10,
            conditionExpressionId: '',
            styles: {}
        };
        setConditionError('');
        onMappingChange({ ...mapping, conditionalStyles:[...rules, rule] });
    }

    function updateRule(rule: ConditionalStyleRule): void {
        onMappingChange({ ...mapping, conditionalStyles:rules.map(item => item.id === rule.id ? rule : item) });
    }

    function updateExpression(expression: ExpressionDefinition): void {
        const owner = rules.find(rule => rule.conditionExpressionId === expression.id);
        if (owner !== undefined && conditionDuplicatesAnotherRule(owner.id, expression, rules, expressionModel)) {
            setConditionError(t('This condition is already used by another conditional style. Each conditional style must define a different condition.'));
            return;
        }
        setConditionError('');
        onExpressionModelChange({ ...expressionModel, expressions:expressionModel.expressions.map(item => item.id === expression.id ? expression : item) });
    }

    function removeRule(rule: ConditionalStyleRule): void {
        const expression = expressionModel.expressions.find(item => item.id === rule.conditionExpressionId);
        const owned = expression?.annotations?.['hydra.editor.conditional-style-owner'] === rule.id;
        onMappingChange({ ...mapping, conditionalStyles:rules.filter(item => item.id !== rule.id) });
        if (owned) onExpressionModelChange({ ...expressionModel, expressions:expressionModel.expressions.filter(item => item.id !== rule.conditionExpressionId) });
        setConditionError('');
    }

    const sortedRules = rules.slice().sort((a,b) => a.priority - b.priority || a.id.localeCompare(b.id));
    return <section className="editor-conditional-styles hydra-designer-wide" aria-label={t('Conditional styles')}>
        <header className="editor-conditional-styles-heading">
            <div><h4>{t('Conditional styles')}</h4><p>{t('The style selected in the representation form is the base style. Conditional rules can change both the representation shape and the visual properties you activate when their condition is true.')}</p></div>
            <div className="editor-conditional-styles-actions">
                <HydraTooltip label={t('About conditional styles')} title={t('Conditional styles')}>
                    <p>{t('The base style has no condition and is always the fallback. Each conditional style adds a different condition and only changes the selected representation and visual properties.')}</p>
                    <p>{t('Two conditional styles cannot use the same condition. HYDRA checks this while editing and again when validating the editor definition.')}</p>
                </HydraTooltip>
                <HydraButton className="editor-conditional-add" type="button" onClick={addRule}>{t('+ Conditional style')}</HydraButton>
            </div>
        </header>

        <section className="editor-conditional-base-style" aria-label={t('Base style')}>
            <div className="editor-conditional-base-marker">0</div>
            <div className="editor-conditional-base-copy"><strong>{t('Base style')}</strong><span>{t('This is the default appearance selected in the representation form. It is used whenever no conditional rule matches.')}</span></div>
            <div className={`editor-conditional-base-preview label-${baseStyle.labelPosition.toLowerCase()}`} data-shape={baseShape} aria-label={t('Base style preview')} title={`${t('Base representation')}: ${t(shapeLabel(baseShape))}`} style={{ opacity:baseStyle.opacity }}><EditorNodeShapeFrame shape={baseShape} notation={baseStyle} className="editor-conditional-preview-shape"/><span className="editor-conditional-preview-label" style={labelCss(baseStyle)}>{baseStyle.labelVisible ? t('Default') : ''}</span><small>{t(shapeLabel(baseShape))}</small></div>
        </section>

        {(conditionError !== '' || duplicateRuleIds.size > 0) && <div className="editor-conditional-condition-error" role="alert">{conditionError !== '' ? conditionError : t('Two or more conditional styles use the same condition. Change or remove the duplicated condition before saving.')}</div>}
        {rules.length === 0 && <div className="editor-conditional-styles-empty"><strong>{t('No conditional styles')}</strong><span>{t('Only the base style will be used. Add a conditional rule when a model state should look different.')}</span><HydraButton type="button" onClick={addRule}>{t('Create first conditional style')}</HydraButton></div>}

        <div className="editor-conditional-style-list">
            {sortedRules.map((rule,index) => {
                const expression = expressionModel.expressions.find(item => item.id === rule.conditionExpressionId);
                const duplicate = duplicateRuleIds.has(rule.id);
                return <article className={`editor-conditional-style-card conditional-style-rule-card${rule.enabled ? '' : ' disabled'}${duplicate ? ' duplicate-condition' : ''}`} key={rule.id}>
                    <header className="editor-conditional-style-rule-header">
                        <div className="editor-conditional-style-identity"><span className="editor-conditional-style-order">{index + 1}</span><div><input aria-label={`${t('Conditional style')} ${index + 1} ${t('Name')}`} value={rule.name} onChange={event => updateRule({ ...rule, name:event.target.value })}/><small>{rule.enabled ? t('This rule participates in style resolution.') : t('This rule is temporarily disabled.')}</small></div></div>
                        <div className="editor-conditional-style-card-actions">
                            <label className="editor-conditional-toggle"><input type="checkbox" checked={rule.enabled} onChange={event => updateRule({ ...rule, enabled:event.target.checked })}/><span>{t('Enabled')}</span></label>
                            <label className="editor-conditional-priority"><span>{t('Priority')}<HydraTooltip label={t('About rule priority')} title={t('Rule priority')}><p>{t('When several different conditions are true, rules with a higher priority are applied later and override lower-priority values for the same visual property.')}</p></HydraTooltip></span><input aria-label={`${t('Priority')} ${index + 1}`} type="number" value={rule.priority} onChange={event => updateRule({ ...rule, priority:Number(event.target.value) || 0 })}/></label>
                            <HydraButton className="hydra-danger-button" type="button" onClick={() => removeRule(rule)}>{t('Delete')}</HydraButton>
                        </div>
                    </header>

                    <div className="editor-conditional-style-summary">
                        <span><strong>1</strong>{t('Define a unique condition')}</span>
                        <span><strong>2</strong>{t('Override the base style')}</span>
                        <em>{Object.keys(rule.styles).length} {t('override(s)')}</em>
                    </div>

                    <details className="editor-conditional-style-step" open>
                        <summary><span className="editor-conditional-step-number">1</span><div><strong>{t('Condition')}</strong><small>{t('Define the model state that activates this style. The same condition cannot be reused by another style rule.')}</small></div></summary>
                        {duplicate && <div className="editor-conditional-inline-warning" role="alert">{t('Duplicated condition. Change this condition before saving.')}</div>}
                        {expression === undefined ? <MissingCondition rule={rule} mapping={mapping} rules={rules} expressionModel={expressionModel} onRuleChange={updateRule} onExpressionModelChange={onExpressionModelChange}/>
                            : <div className="editor-conditional-style-condition"><ExpressionBuilder expression={expression} metamodel={metamodel} onChange={updateExpression}/></div>}
                    </details>

                    <details className="editor-conditional-style-step">
                        <summary><span className="editor-conditional-step-number">2</span><div><strong>{t('Visual overrides')}</strong><small>{t('Only enabled properties replace the base style while this condition is true.')}</small></div></summary>
                        <ConditionalStyleOverrides rule={rule} baseShape={baseShape} baseStyles={mapping.styles} onChange={updateRule}/>
                    </details>
                </article>;
            })}
        </div>
    </section>;
}

function MissingCondition({ rule, mapping, rules, expressionModel, onRuleChange, onExpressionModelChange }: { rule:ConditionalStyleRule; mapping:NodeMapping; rules:ConditionalStyleRule[]; expressionModel:ExpressionModel; onRuleChange:(rule:ConditionalStyleRule)=>void; onExpressionModelChange:(model:ExpressionModel)=>void }) {
    const { t } = useHydraLanguage();
    return <div className="editor-conditional-style-missing"><strong>{t('Condition is not defined yet')}</strong><span>{t('Create the condition first. HYDRA will keep it independent from the conditions used by the other style rules.')}</span><HydraButton type="button" onClick={() => {
        const replacement = createUniqueStarterCondition(rule, mapping.targetClassId, rules, expressionModel, t);
        onExpressionModelChange({ ...expressionModel, expressions:[...expressionModel.expressions,replacement] });
        onRuleChange({ ...rule, conditionExpressionId:replacement.id });
    }}>{t('Define condition')}</HydraButton></div>;
}

function createUniqueStarterCondition(rule: ConditionalStyleRule, contextClassId: string, rules: ConditionalStyleRule[], expressionModel: ExpressionModel, t:(value:string)=>string): ExpressionDefinition {
    let replacement: ExpressionDefinition = { ...createDefaultExpression(contextClassId), name:`${rule.name} ${t('condition')}`, annotations:{'hydra.editor.conditional-style-owner':rule.id} };
    let depth = 0;
    while (conditionDuplicatesAnotherRule(rule.id, replacement, rules, expressionModel)) {
        replacement = { ...replacement, root:{ kind:'UNARY', nodeId:createValidationDesignerId('node'), operator:'NOT', operand:replacement.root } };
        depth += 1;
        if (depth > 64) break;
    }
    return replacement;
}

function ConditionalStyleOverrides({ rule, baseShape, baseStyles, onChange }: { rule:ConditionalStyleRule; baseShape:NodeMapping['shape']; baseStyles:Record<string,string>; onChange:(rule:ConditionalStyleRule)=>void }) {
    const { t } = useHydraLanguage();
    function setEnabled(key:string, enabled:boolean, defaultValue:string):void {
        const styles = { ...rule.styles };
        if (enabled) styles[key] = styles[key] ?? defaultValue; else delete styles[key];
        onChange({ ...rule, styles });
    }
    const setValue = (key:string,value:string):void => onChange({ ...rule, styles:{ ...rule.styles, [key]:value } });
    const applyPreset = (styles:Record<string,string>):void => onChange({ ...rule, styles:{ ...rule.styles, ...styles } });
    const groupLabels: Record<StyleGroup,string> = { REPRESENTATION:t('Representation'), SHAPE:t('Surface'), BORDER:t('Border'), LABEL:t('Label') };

    const previewStyle = resolveNodeNotationStyle({ ...baseStyles, ...rule.styles });
    const previewShape = rule.styles.shape ?? baseShape;
    return <section className="editor-conditional-style-overrides">
        <div className="editor-conditional-preset-panel conditional-style-presets"><div className="editor-conditional-preset-copy"><div className="editor-conditional-preset-title"><strong>{t('Quick presets')}</strong><HydraTooltip label={t('Information about quick presets')} title={t('Quick presets')}><p>{t('Quick presets are ready-made visual combinations for common states such as highlighted, correct, warning or error. Use one as a starting point to apply several conditional overrides at once, then adjust any property independently. They never change the metamodel element or the base representation.')}</p></HydraTooltip></div><span>{t('Presets add overrides on top of the base style. They never replace the base configuration itself.')}</span></div><div>{STYLE_PRESETS.map(preset => <HydraButton key={preset.id} type="button" title={t(preset.description)} onClick={() => applyPreset(preset.styles)}>{t(preset.label)}</HydraButton>)}</div></div>
        <div className="editor-conditional-live-preview"><div><span>{t('Live result')}</span><strong>{t('When this condition is true')}</strong><p>{t('The preview combines the base representation with only the overrides enabled in this rule.')}</p></div><div className={`editor-conditional-base-preview label-${previewStyle.labelPosition.toLowerCase()}`} data-shape={previewShape} aria-label={t('Conditional style preview')} style={{ opacity:previewStyle.opacity }}><EditorNodeShapeFrame shape={previewShape as NodeMapping['shape']} notation={previewStyle} className="editor-conditional-preview-shape"/><span className="editor-conditional-preview-label" style={labelCss(previewStyle)}>{previewStyle.labelVisible ? t('Example') : ''}</span><small>{t(shapeLabel(previewShape))}</small></div></div>
        {(['REPRESENTATION','SHAPE','BORDER','LABEL'] as StyleGroup[]).map(group => <section className="editor-conditional-style-group" key={group}>
            <header><div><h6>{groupLabels[group]}</h6><p>{t(groupDescription(group))}</p></div><HydraTooltip label={t('About conditional override group')} title={groupLabels[group]}><p>{t(groupHelp(group))}</p></HydraTooltip></header>
            <div className="editor-conditional-style-grid conditional-style-override-grid">{STYLE_FIELDS.filter(field => field.group === group).map(field => {
                const enabled = Object.hasOwn(rule.styles, field.key), fieldDefault = field.key === 'shape' ? baseShape : field.defaultValue, value = rule.styles[field.key] ?? fieldDefault;
                const colorValue = /^#[0-9a-f]{6}$/i.test(value) ? value : field.defaultValue;
                return <div className={`editor-conditional-style-field${enabled ? ' enabled' : ''}`} key={field.key}>
                    <div className="editor-conditional-style-field-heading"><label><input type="checkbox" checked={enabled} onChange={event => setEnabled(field.key,event.target.checked,field.key === 'shape' ? baseShape : field.defaultValue)}/><span>{t(field.label)}</span></label><HydraTooltip label={`${t('Information about')} ${t(field.label)}`} title={t(field.label)}><p>{t(field.help)}</p></HydraTooltip></div>
                    <div className="editor-conditional-style-control">
                        {field.kind === 'color' && <><input aria-label={`${t(field.label)} ${t('color picker')}`} type="color" disabled={!enabled} value={colorValue} onChange={event => setValue(field.key,event.target.value)}/><input aria-label={`${t(field.label)} ${t('value')}`} className="editor-conditional-color-value" disabled={!enabled} value={value} onChange={event => setValue(field.key,event.target.value)} /></>}
                        {field.kind === 'number' && <input aria-label={t(field.label)} type="number" disabled={!enabled} min={field.min} max={field.max} step={field.step} value={value} onChange={event => setValue(field.key,event.target.value)}/>} 
                        {field.kind === 'boolean' && <HydraSelect ariaLabel={field.label} disabled={!enabled} value={value} options={[{value:'true',label:'Yes',description:'Enable this style override while the condition matches.'},{value:'false',label:'No',description:'Disable this style override while the condition matches.'}]} onChange={next => setValue(field.key,next)} />}
                        {field.kind === 'select' && <HydraSelect ariaLabel={field.label} disabled={!enabled} value={value} options={(field.options ?? []).map(option => ({ value:option.value,label:option.label,description:field.help }))} onChange={next => setValue(field.key,next)} />}
                    </div>
                </div>;
            })}</div>
        </section>)}
    </section>;
}

function groupDescription(group: StyleGroup): string {
    if (group === 'REPRESENTATION') return 'Change the geometric representation without changing the semantic element.';
    if (group === 'SHAPE') return 'Background, transparency and corner treatment.';
    if (group === 'BORDER') return 'Border color, thickness and line style.';
    return 'Label visibility, colors, position, typography and spacing.';
}
function groupHelp(group: StyleGroup): string {
    if (group === 'REPRESENTATION') return 'Use a representation override when the same semantic element should be shown with a different geometric shape in a particular state.';
    if (group === 'SHAPE') return 'Only enabled surface properties replace the base representation style.';
    if (group === 'BORDER') return 'Only enabled border properties replace the base representation style.';
    return 'Only enabled label properties replace the base representation style. Position, size, weight, padding, visibility and colors remain independent.';
}
function shapeLabel(shape: string): string {
    return shape.toLowerCase().replaceAll('_',' ').replace(/^./, value => value.toUpperCase());
}
