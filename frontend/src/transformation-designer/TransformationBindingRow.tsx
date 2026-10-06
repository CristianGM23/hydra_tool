import { HydraFormField, HydraSelect, HydraTooltip } from '../design-system';
import type { MetaAttribute, MetaClass, MetaModel } from '../metamodel-designer/types';
import { ExpressionBuilder } from '../validation-designer/ExpressionBuilder';
import { useHydraLanguage } from '../i18n';
import type { TransformationBinding, TransformationBindingKind, TransformationRule, TransformationValuePolicy } from './types';
import { defaultValuePolicy, setAttributeBinding } from './transformationDesignerModel';

export function TransformationBindingRow({ rule, source, target, sourceClass, targetAttribute, onChange }: {
    rule:TransformationRule; source:MetaModel; target:MetaModel; sourceClass:MetaClass | undefined; targetAttribute:MetaAttribute; onChange:(rule:TransformationRule)=>void;
}) {
    const { t }=useHydraLanguage();
    const targetAlias=rule.targets[0]?.alias ?? 'target';
    const binding=rule.bindings.find(item=>item.targetAlias===targetAlias&&item.targetFeatureId===targetAttribute.id);
    const sourceAttributes=sourceClass?.attributes ?? [];
    const kind=binding?.kind ?? 'NONE';
    function setKind(value:string){
        if(value==='NONE'){onChange({...rule,bindings:rule.bindings.filter(item=>item.id!==binding?.id)});return;}
        const nextKind=value as Exclude<TransformationBindingKind,'TRACE_EQUIVALENT'>;
        const sourceAttribute=nextKind==='DIRECT'?(sourceAttributes.find(item=>item.id===binding?.sourceFeatureId)??sourceAttributes[0]):undefined;
        onChange(setAttributeBinding(rule,targetAttribute,nextKind,sourceAttribute));
    }
    function patch(patch:Partial<TransformationBinding>){if(binding!==undefined)onChange({...rule,bindings:rule.bindings.map(item=>item.id===binding.id?{...item,...patch}:item)});}
    function patchPolicy(delta:Partial<TransformationValuePolicy>){if(binding!==undefined)patch({valuePolicy:{...(binding.valuePolicy??defaultValuePolicy()),...delta}});}
    const sourceAttribute=sourceAttributes.find(item=>item.id===binding?.sourceFeatureId);
    const enumSource=sourceAttribute?.type.kind==='ENUM'?source.enums.find(item=>item.id===sourceAttribute.type.targetId):undefined;
    const enumTarget=targetAttribute.type.kind==='ENUM'?target.enums.find(item=>item.id===targetAttribute.type.targetId):undefined;
    return <div className="transformation-binding-row transformation-binding-row-expanded">
        <div className="transformation-binding-target"><div className="transformation-binding-target-title"><strong>{targetAttribute.name}</strong><HydraTooltip label={`${t('Help for target property')} ${targetAttribute.name}`} title={`${t('Target property')}: ${targetAttribute.name}`}><DynamicAttributeHelp targetAttribute={targetAttribute} sourceClass={sourceClass} /></HydraTooltip></div><small>{typeLabel(targetAttribute)}</small></div>
        <HydraSelect ariaLabel={`${t('Mapping kind for')} ${targetAttribute.name}`} value={kind} options={[
            {value:'NONE',label:t('Not mapped'),description:t('Leave the target property without a value mapping.')},
            {value:'DIRECT',label:t('Direct property'),description:t('Copy a source property and optionally adapt its value.')},
            {value:'CONSTANT',label:t('Constant value'),description:t('Use the same literal value for every matched source element.')},
            {value:'EXPRESSION',label:t('Calculated expression'),description:t('Calculate the value with the shared HYDRA expression builder.')},
        ]} onChange={setKind}/>
        {binding?.kind==='DIRECT'&&<HydraSelect ariaLabel={`${t('Source property for')} ${targetAttribute.name}`} value={binding.sourceFeatureId} options={sourceAttributes.map(item=>({value:item.id,label:item.name,description:typeLabel(item)}))} onChange={value=>{const selected=sourceAttributes.find(item=>item.id===value);if(selected)onChange(setAttributeBinding(rule,targetAttribute,'DIRECT',selected));}}/>}
        {binding?.kind==='CONSTANT'&&<HydraFormField label="Constant value" targetId={`constant-${binding.id}`} help="Literal value. Explicit conversion policies are applied afterwards." width="wide"><input id={`constant-${binding.id}`} value={String(binding.literalValue??'')} onChange={event=>patch({literalValue:event.target.value})}/></HydraFormField>}
        {binding?.kind==='EXPRESSION'&&binding.expression&&<div className="transformation-binding-expression"><ExpressionBuilder expression={binding.expression} metamodel={source} onChange={expression=>patch({expression})}/></div>}
        {binding!==undefined&&<div className="transformation-value-policy">
            <HydraSelect ariaLabel={t('Collection adaptation')} value={(binding.valuePolicy??defaultValuePolicy()).collectionMode} options={['PRESERVE','FIRST','LAST','COUNT'].map(value=>({value,label:t(value),description:t(collectionDescription(value))}))} onChange={value=>patchPolicy({collectionMode:value as TransformationValuePolicy['collectionMode']})}/>
            <HydraSelect ariaLabel={t('Type conversion')} value={(binding.valuePolicy??defaultValuePolicy()).conversionMode} options={['NONE','TO_STRING','TO_INTEGER','TO_LONG','TO_DOUBLE','TO_BOOLEAN'].map(value=>({value,label:t(value),description:t(conversionDescription(value))}))} onChange={value=>patchPolicy({conversionMode:value as TransformationValuePolicy['conversionMode']})}/>
            <HydraSelect ariaLabel={t('On mapping error')} value={(binding.valuePolicy??defaultValuePolicy()).errorPolicy} options={['FAIL','SKIP_VALUE','USE_FALLBACK'].map(value=>({value,label:t(value),description:t(errorDescription(value))}))} onChange={value=>patchPolicy({errorPolicy:value as TransformationValuePolicy['errorPolicy']})}/>
            {(binding.valuePolicy??defaultValuePolicy()).errorPolicy==='USE_FALLBACK'&&<HydraFormField label="Fallback value" targetId={`fallback-${binding.id}`} help="Value used only when the selected conversion or mapping cannot be completed." width="normal"><input id={`fallback-${binding.id}`} value={String(binding.valuePolicy?.fallbackValue??'')} onChange={event=>patchPolicy({fallbackValue:event.target.value})}/></HydraFormField>}
        </div>}
        {binding!==undefined&&enumSource!==undefined&&enumTarget!==undefined&&<div className="transformation-enum-map"><strong>{t('Enumeration mapping')}</strong>{enumSource.literals.map(literal=><label key={literal.id}><span>{literal.name}</span><select value={binding.valuePolicy?.enumMapping?.[literal.name]??''} onChange={event=>patchPolicy({enumMapping:{...(binding.valuePolicy?.enumMapping??{}),[literal.name]:event.target.value}})}><option value="">{t('Not mapped')}</option>{enumTarget.literals.map(targetLiteral=><option key={targetLiteral.id} value={targetLiteral.name}>{targetLiteral.name}</option>)}</select></label>)}</div>}
    </div>;
}

function typeLabel(attribute:MetaAttribute):string { const upper=attribute.multiplicity.upperBound===null?'*':String(attribute.multiplicity.upperBound);return `${attribute.type.kind}:${attribute.type.targetId||attribute.type.externalName||'value'} [${attribute.multiplicity.lowerBound}..${upper}]`; }
function collectionDescription(value:string):string{return value==='PRESERVE'?'Keep the original cardinality.':value==='FIRST'?'Use the first value.':value==='LAST'?'Use the last value.':'Count values and return the size.';}
function conversionDescription(value:string):string{return value==='NONE'?'Do not coerce the value.':value==='TO_STRING'?'Convert explicitly using string semantics.':value==='TO_INTEGER'?'Convert explicitly using integer semantics.':value==='TO_LONG'?'Convert explicitly using long semantics.':value==='TO_DOUBLE'?'Convert explicitly using double semantics.':'Convert explicitly using boolean semantics.';}
function errorDescription(value:string):string{return value==='FAIL'?'Stop the transformation on an invalid value.':value==='SKIP_VALUE'?'Leave this target feature unset when adaptation fails.':'Use the configured fallback value when adaptation fails.';}


function DynamicAttributeHelp({targetAttribute,sourceClass}:{targetAttribute:MetaAttribute;sourceClass:MetaClass|undefined}) {
    const {t}=useHydraLanguage();
    const candidates=sourceClass?.attributes ?? [];
    const exact=candidates.filter(item=>sameType(item,targetAttribute)&&sameMultiplicity(item,targetAttribute));
    const adaptable=candidates.filter(item=>!exact.includes(item)&&sameType(item,targetAttribute));
    return <div className="transformation-dynamic-help"><p><strong>{targetAttribute.name}</strong> · {t('Expected type and cardinality')}: <code>{typeLabel(targetAttribute)}</code>.</p>{sourceClass===undefined?<p>{t('No source class is selected for this rule yet. Select one to see compatible properties.')}</p>:<><p>{t('HYDRA is comparing this target property with the properties of')} <strong>{sourceClass.name}</strong>.</p>{exact.length>0?<p>{t('Direct candidates with the same type and multiplicity')}: <strong>{exact.map(item=>item.name).join(', ')}</strong>.</p>:<p>{t('No source property has exactly the same type and multiplicity. A conversion or collection adaptation may be required.')}</p>}{adaptable.length>0&&<p>{t('Same-type candidates with different multiplicity')}: <strong>{adaptable.map(item=>`${item.name} (${typeLabel(item)})`).join(', ')}</strong>.</p>}<p>{t('Choose a direct mapping when the values already mean the same thing. Use a conversion only when the semantic meaning is preserved, and use a calculated expression when the target value must combine or derive information.')}</p></>}</div>;
}
function sameType(left:MetaAttribute,right:MetaAttribute):boolean{return left.type.kind===right.type.kind&&left.type.targetId===right.type.targetId&&left.type.externalName===right.type.externalName;}
function sameMultiplicity(left:MetaAttribute,right:MetaAttribute):boolean{return left.multiplicity.lowerBound===right.multiplicity.lowerBound&&left.multiplicity.upperBound===right.multiplicity.upperBound;}
