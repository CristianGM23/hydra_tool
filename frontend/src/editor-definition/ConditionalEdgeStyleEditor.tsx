import { HydraButton } from '../design-system/HydraButton';
import { useState } from 'react';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { HYDRA_CONNECTION_TERMINATOR_DEFINITIONS } from '../designer-ui/HydraConnectionVisual';
import { HydraConnectionStylePreview } from '../designer-ui/HydraConnectionStylePreview';
import { HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n/useHydraLanguage';
import type { MetaModel } from '../metamodel-designer/types';
import { ExpressionBuilder } from '../validation-designer/ExpressionBuilder';
import { createDefaultExpression, createValidationDesignerId } from '../validation-designer/validationDesignerFactory';
import type { ExpressionDefinition, ExpressionModel } from '../validation/types';
import { conditionDuplicatesAnotherRule, duplicateConditionalStyleRuleIds } from './conditionalStyleConditions';
import { editorEdgeStyleToHydraVisual } from './editorNotationStyle';
import type { ConditionalStyleRule, EdgeMapping } from './types';

interface Props { mapping:EdgeMapping; metamodel:MetaModel; expressionModel:ExpressionModel; onMappingChange:(mapping:EdgeMapping)=>void; onExpressionModelChange:(model:ExpressionModel)=>void; }
type Group='ROUTE'|'LINE'|'SOURCE'|'TARGET'|'LABEL';
type Field={ key:string; label:string; help:string; group:Group; kind:'color'|'number'|'boolean'|'select'; defaultValue:string; min?:number; max?:number; step?:number; options?:Array<{value:string;label:string;description?:string;category?:string}> };
const markerOptions=HYDRA_CONNECTION_TERMINATOR_DEFINITIONS.map(item=>({value:item.value,label:item.label,description:item.description,category:item.category}));
const FIELDS:Field[]=[
 {key:'routeStyle',label:'Conditional routing',help:'Overrides only the visual route while this condition is true.',group:'ROUTE',kind:'select',defaultValue:'SMOOTH',options:[{value:'STRAIGHT',label:'Straight'},{value:'SMOOTH',label:'Smooth'},{value:'ORTHOGONAL',label:'Orthogonal'}]},
 {key:'strokeColor',label:'Line color',help:'Changes the visible connection color for this state.',group:'LINE',kind:'color',defaultValue:'#334155'},
 {key:'strokeWidth',label:'Line width',help:'Changes connection thickness without changing relation semantics.',group:'LINE',kind:'number',defaultValue:'2',min:.5,max:64,step:.5},
 {key:'strokeOpacity',label:'Line opacity',help:'De-emphasizes or emphasizes the connection while keeping it visible.',group:'LINE',kind:'number',defaultValue:'1',min:.1,max:1,step:.05},
 {key:'strokeBorderColor',label:'Line border color',help:'Optional contrast outline behind the connection.',group:'LINE',kind:'color',defaultValue:'#FFFFFF'},
 {key:'strokeBorderWidth',label:'Line border width',help:'Width of the optional contrast outline. Use 0 to disable it.',group:'LINE',kind:'number',defaultValue:'0',min:0,max:8,step:.5},
 {key:'borderStyle',label:'Line pattern',help:'Changes the line rhythm while this rule is active.',group:'LINE',kind:'select',defaultValue:'SOLID',options:[{value:'SOLID',label:'Solid'},{value:'DASHED',label:'Dashed'},{value:'DOTTED',label:'Dotted'},{value:'LONG_DASH',label:'Long dash'},{value:'DASH_DOT',label:'Dash dot'},{value:'NONE',label:'Hidden'}]},
 ...endpointFields('source','SOURCE'), ...endpointFields('target','TARGET'),
 {key:'labelColor',label:'Label color',help:'Changes the connection label text color.',group:'LABEL',kind:'color',defaultValue:'#334155'},
 {key:'labelBackgroundColor',label:'Label background',help:'Changes the background behind the connection label.',group:'LABEL',kind:'color',defaultValue:'#FFFFFF'},
 {key:'labelBackgroundTransparent',label:'Transparent label background',help:'Removes the background behind the connection label so only the text remains.',group:'LABEL',kind:'boolean',defaultValue:'false'},
 {key:'labelVisible',label:'Show label',help:'Shows or hides only the visible label.',group:'LABEL',kind:'boolean',defaultValue:'true'},
 {key:'labelFontSize',label:'Label font size',help:'Changes label size while this condition is active.',group:'LABEL',kind:'number',defaultValue:'12',min:1,max:200,step:1},
 {key:'labelFontWeight',label:'Label weight',help:'Changes numeric font weight for the connection label.',group:'LABEL',kind:'number',defaultValue:'600',min:100,max:900,step:100},
 {key:'labelPadding',label:'Label padding',help:'Changes internal space around the label.',group:'LABEL',kind:'number',defaultValue:'3',min:0,max:100,step:1}
];
const PRESETS:Array<{label:string;help:string;styles:Record<string,string>}>= [
 {label:'Emphasized',help:'Make an important relation easier to follow.',styles:{strokeColor:'#2563EB',strokeWidth:'3',strokeOpacity:'1'}},
 {label:'Muted',help:'De-emphasize a secondary relation without removing it.',styles:{strokeColor:'#64748B',strokeWidth:'1.5',strokeOpacity:'.55',borderStyle:'DASHED'}},
 {label:'Aggregation',help:'Use an open diamond at the source end.',styles:{sourceDecoration:'DIAMOND_OPEN',targetDecoration:'NONE'}},
 {label:'Composition',help:'Use a filled diamond at the source end.',styles:{sourceDecoration:'DIAMOND_CLOSED',targetDecoration:'NONE'}},
 {label:'Bidirectional',help:'Show compatible closed arrows at both ends.',styles:{sourceDecoration:'ARROW_CLOSED',targetDecoration:'ARROW_CLOSED'}},
 {label:'No markers',help:'Keep a plain connection without endpoint markers.',styles:{sourceDecoration:'NONE',targetDecoration:'NONE'}}
];

export function ConditionalEdgeStyleEditor({mapping,metamodel,expressionModel,onMappingChange,onExpressionModelChange}:Props){
 const {t}=useHydraLanguage(); const [error,setError]=useState(''); const rules=mapping.conditionalStyles??[]; const duplicateIds=duplicateConditionalStyleRuleIds(rules,expressionModel);
 const hasContext=mapping.semanticEdge!==undefined||metamodel.relations.some(item=>item.id===mapping.relationId);
 const updateRule=(rule:ConditionalStyleRule)=>onMappingChange({...mapping,conditionalStyles:rules.map(item=>item.id===rule.id?rule:item)});
 const remove=(rule:ConditionalStyleRule)=>{onMappingChange({...mapping,conditionalStyles:rules.filter(item=>item.id!==rule.id)}); if(rule.conditionExpressionId) onExpressionModelChange({...expressionModel,expressions:expressionModel.expressions.filter(item=>item.id!==rule.conditionExpressionId)});};
 const add=()=>onMappingChange({...mapping,conditionalStyles:[...rules,{id:createValidationDesignerId('conditional-edge-style'),name:`${t('Conditional connection style')} ${rules.length+1}`,enabled:true,priority:rules.length*10,conditionExpressionId:'',styles:{}}]});
 const updateExpression=(owner:ConditionalStyleRule,expression:ExpressionDefinition)=>{if(conditionDuplicatesAnotherRule(owner.id,expression,rules,expressionModel)){setError(t('This condition is already used by another conditional style. Each conditional style must define a different condition.'));return;} setError('');onExpressionModelChange({...expressionModel,expressions:expressionModel.expressions.map(item=>item.id===expression.id?expression:item)});};
 return <section className="editor-conditional-style-editor editor-conditional-edge-style-editor hydra-designer-wide">
  <header className="editor-conditional-style-header"><div><span className="editor-conditional-style-eyebrow">{t('State-based connection styling')}</span><h4>{t('Conditional connection styles')}</h4><p>{t('Change routing, line appearance, endpoints or labels when the source element enters a model state. The relation itself never changes.')}</p></div><HydraButton type="button" onClick={add}>+ {t('Add connection style')}</HydraButton></header>
  <div className="editor-conditional-style-guidance"><strong>{t('How it works')}</strong><span>{t('Define a condition, choose only the visual properties that should change, and preview the result. Rules are applied by priority.')}</span><HydraTooltip label={t('About connection style conditions')} title={t('Condition context')}><p>{t('Connection conditions are evaluated against the source element. This keeps evaluation deterministic while the mapped metamodel relation fixes the target type.')}</p></HydraTooltip></div>
  {hasContext&&<div className="editor-conditional-style-context"><strong>{t('Condition context')}</strong><span>{metamodel.classes.find(item=>item.id===mapping.sourceClassId)?.name??mapping.sourceClassId} → {metamodel.classes.find(item=>item.id===mapping.targetClassId)?.name??mapping.targetClassId}</span></div>}
  {error&&<div className="editor-conditional-inline-warning" role="alert">{error}</div>}
  {rules.length===0&&<div className="editor-conditional-empty"><strong>{t('No conditional connection styles yet')}</strong><span>{t('The base connection appearance is used in every state. Add a rule only when the same relation needs a different visual treatment in a particular state.')}</span></div>}
  <div className="editor-conditional-style-list">{rules.map((rule,index)=>{
   const expression=expressionModel.expressions.find(item=>item.id===rule.conditionExpressionId); const duplicate=duplicateIds.has(rule.id);
   const setEnabled=(key:string,enabled:boolean,defaultValue:string)=>{const styles={...rule.styles};if(enabled)styles[key]=styles[key]??defaultValue;else delete styles[key];updateRule({...rule,styles});};
   const setValue=(key:string,value:string)=>updateRule({...rule,styles:{...rule.styles,[key]:value}});
   const groupLabels:Record<Group,string>={ROUTE:t('Routing'),LINE:t('Line'),SOURCE:t('Source end'),TARGET:t('Target end'),LABEL:t('Label')};
   return <article className={`editor-conditional-style-card${rule.enabled?' enabled':''}`} key={rule.id}>
    <header><div className="editor-conditional-style-card-title"><span>{index+1}</span><div><input aria-label={`${t('Rule name')} ${index+1}`} value={rule.name} onChange={e=>updateRule({...rule,name:e.target.value})}/><small>{t('Only selected overrides replace the base connection style.')}</small></div></div><div className="editor-conditional-style-card-actions"><label className="editor-conditional-toggle"><input type="checkbox" checked={rule.enabled} onChange={e=>updateRule({...rule,enabled:e.target.checked})}/><span>{t('Enabled')}</span></label><label className="editor-conditional-priority"><span>{t('Priority')}</span><input aria-label={`${t('Priority')} ${index+1}`} type="number" value={rule.priority} onChange={e=>updateRule({...rule,priority:Number(e.target.value)||0})}/></label><HydraButton className="hydra-danger-button" type="button" onClick={()=>remove(rule)}>{t('Delete')}</HydraButton></div></header>
    <details className="editor-conditional-style-step" open><summary><span className="editor-conditional-step-number">1</span><div><strong>{t('Condition')}</strong><small>{t('Choose the source-element state that activates this visual rule.')}</small></div></summary>{duplicate&&<div className="editor-conditional-inline-warning" role="alert">{t('Duplicated condition. Change this condition before saving.')}</div>}{expression?<ExpressionBuilder expression={expression} metamodel={metamodel} onChange={value=>updateExpression(rule,value)}/>:<div className="editor-conditional-style-missing"><strong>{t('Condition is not defined yet')}</strong><span>{t('Create a dedicated condition for this connection style.')}</span><HydraButton type="button" onClick={()=>{const value=createUniqueCondition(rule,mapping.sourceClassId,rules,expressionModel,t);onExpressionModelChange({...expressionModel,expressions:[...expressionModel.expressions,value]});updateRule({...rule,conditionExpressionId:value.id});}}>{t('Define condition')}</HydraButton></div>}</details>
    <details className="editor-conditional-style-step"><summary><span className="editor-conditional-step-number">2</span><div><strong>{t('Connection overrides')}</strong><small>{t('Configure only what should look different while the condition is true.')}</small></div></summary>
     <div className="editor-conditional-style-overrides">
      <div className="editor-conditional-preset-panel"><div><strong>{t('Quick presets')}</strong><span>{t('Presets are editable starting points and never change the semantic relation.')}</span></div><div>{PRESETS.map(p=><HydraButton type="button" key={p.label} title={t(p.help)} onClick={()=>updateRule({...rule,styles:{...rule.styles,...p.styles}})}>{t(p.label)}</HydraButton>)}</div></div>
      <div className="editor-conditional-live-preview editor-conditional-edge-preview"><div><span>{t('Live result')}</span><strong>{t('When this condition is true')}</strong><p>{t('The preview combines the base connection with only the overrides enabled in this rule.')}</p></div><HydraConnectionStylePreview value={editorEdgeStyleToHydraVisual(mapping,{...mapping.styles,...rule.styles})} label="Conditional connection preview" /></div>
     {(['ROUTE','LINE','SOURCE','TARGET','LABEL'] as Group[]).map(group=><section className="editor-conditional-style-group" key={group}><header><div><h6>{groupLabels[group]}</h6><p>{t(groupDescription(group))}</p></div></header><div className="editor-conditional-style-grid">{FIELDS.filter(field=>field.group===group).map(field=>{const enabled=Object.hasOwn(rule.styles,field.key);const value=rule.styles[field.key]??field.defaultValue;const color=/^#[0-9a-f]{6}$/i.test(value)?value:field.defaultValue;return <div className={`editor-conditional-style-field${enabled?' enabled':''}`} key={field.key}><div className="editor-conditional-style-field-heading"><label><input type="checkbox" checked={enabled} onChange={e=>setEnabled(field.key,e.target.checked,field.defaultValue)}/><span>{t(field.label)}</span></label><HydraTooltip label={`${t('Information about')} ${t(field.label)}`} title={t(field.label)}><p>{t(field.help)}</p></HydraTooltip></div><div className="editor-conditional-style-control">{field.kind==='color'&&<input aria-label={t(field.label)} type="color" disabled={!enabled} value={color} onChange={e=>setValue(field.key,e.target.value)}/>} {field.kind==='number'&&<input aria-label={t(field.label)} type="number" disabled={!enabled} min={field.min} max={field.max} step={field.step} value={value} onChange={e=>setValue(field.key,e.target.value)}/>} {field.kind==='boolean'&&<HydraAdaptiveSelect ariaLabel={t(field.label)} disabled={!enabled} value={value} options={[{value:'true',label:t('Yes'),description:t('Use this option when the conditional style should show the element.')},{value:'false',label:t('No'),description:t('Use this option when the conditional style should hide the element.') }]} onChange={v=>setValue(field.key,v)}/>} {field.kind==='select'&&<HydraAdaptiveSelect ariaLabel={t(field.label)} value={value} disabled={!enabled} options={(field.options??[]).map(o=>({value:o.value,label:t(o.label),description:o.description===undefined?undefined:t(o.description),category:o.category===undefined?undefined:t(o.category)}))} onChange={v=>setValue(field.key,v)}/>}</div></div>})}</div></section>)}
     </div>
    </details>
   </article>})}</div>
 </section>;
}
function endpointFields(prefix:'source'|'target',group:'SOURCE'|'TARGET'):Field[]{const cap=prefix==='source'?'Source':'Target';return [
 {key:`${prefix}Decoration`,label:`${cap} end shape`,help:`Geometric marker shown at the ${prefix} end. NONE creates a plain end.`,group,kind:'select',defaultValue:'NONE',options:markerOptions},
 {key:`${prefix}DecorationFillColor`,label:`${cap} end fill`,help:'Interior color used by closed endpoint shapes.',group,kind:'color',defaultValue:'#FFFFFF'},
 {key:`${prefix}DecorationBorderColor`,label:`${cap} end border`,help:'Outline color of the endpoint marker.',group,kind:'color',defaultValue:'#334155'},
 {key:`${prefix}DecorationBorderWidth`,label:`${cap} end border width`,help:'Outline thickness of the endpoint marker.',group,kind:'number',defaultValue:'1.5',min:0,max:8,step:.5},
 {key:`${prefix}DecorationSize`,label:`${cap} end size`,help:'Relative size of the endpoint marker.',group,kind:'number',defaultValue:'1',min:.5,max:2.5,step:.1}
];}
function createUniqueCondition(rule:ConditionalStyleRule,contextClassId:string,rules:ConditionalStyleRule[],model:ExpressionModel,t:(value:string)=>string):ExpressionDefinition{let value:ExpressionDefinition={...createDefaultExpression(contextClassId),name:`${rule.name} ${t('condition')}`,annotations:{'hydra.editor.conditional-edge-style-owner':rule.id}};let depth=0;while(conditionDuplicatesAnotherRule(rule.id,value,rules,model)&&depth++<64)value={...value,root:{kind:'UNARY',nodeId:createValidationDesignerId('node'),operator:'NOT',operand:value.root}};return value;}
function groupDescription(group:Group):string{if(group==='ROUTE')return 'Change how the connection travels between both elements.';if(group==='LINE')return 'Color, width, opacity, outline and line pattern.';if(group==='SOURCE')return 'Shape and styling of the connection start.';if(group==='TARGET')return 'Shape and styling of the connection destination.';return 'Visibility and presentation of the connection label.';}
