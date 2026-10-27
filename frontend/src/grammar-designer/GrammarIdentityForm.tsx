import { HydraFormField } from '../design-system';
import { useHydraLanguage } from '../i18n';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import type { CanonicalMetamodelSummary } from '../metamodel-designer/types';
import { GrammarMetamodelField } from './GrammarMetamodelField';
import type { GrammarAnalysisResult, GrammarDefinitionModel, GrammarTechnology, GrammarToolchainProfile } from './types';
import type { GrammarLocalInspection } from './grammarLocalInspection';

interface Props{
    model:GrammarDefinitionModel;
    displayedTechnology:GrammarTechnology;
    analysis:GrammarAnalysisResult|null;
    localInspection:GrammarLocalInspection;
    compatibleProfiles:readonly GrammarToolchainProfile[];
    metamodels:readonly CanonicalMetamodelSummary[];
    onMutate:(model:GrammarDefinitionModel)=>void;
    onTechnologyChange:(technology:GrammarTechnology)=>void;
    onMetamodelsRefresh:(selectedId:string)=>Promise<void>|void;
}

export function GrammarIdentityForm({model,displayedTechnology,analysis,localInspection,compatibleProfiles,metamodels,onMutate,onTechnologyChange,onMetamodelsRefresh}:Props){
    const {t}=useHydraLanguage();
    const technologyOptions=[
        {value:'LANGIUM',label:'Langium',description:'TypeScript-based language engineering with generated LSP, browser worker and CLI profiles.',category:'Recommended'},
        {value:'XTEXT',label:'Xtext',description:'JVM/Eclipse language engineering with generated parser, model inference and language-server profiles.',category:'Available values'},
        {value:'ANTLR',label:'ANTLR',description:'Parser-oriented grammar technology. HYDRA adds an explicit semantic bridge when a metamodel is required.',category:'Available values'}
    ];
    const authoringOptions=[
        {value:'GRAMMAR_FIRST',label:'Grammar first',description:'Infer a candidate semantic structure from the grammar; linking a metamodel remains optional.',category:'Recommended'},
        {value:'LINKED',label:'Grammar linked to metamodel',description:'Keep grammar and selected HYDRA metamodel related and use the metamodel to strengthen semantic reconciliation.',category:'Available values'},
        {value:'METAMODEL_FIRST',label:'Metamodel first',description:'Treat the selected HYDRA metamodel as the structural reference the grammar must implement.',category:'Available values'}
    ];
    const toolchainOptions=compatibleProfiles.map(profile=>({value:profile.id,label:`${profile.id} · ${profile.executionMode}`,description:`Provider ${profile.providerVersion} · ${profile.buildSystem} · target ${profile.targetLanguage}${profile.javaVersion?` · Java ${profile.javaVersion}`:''}${profile.nodeVersion?` · Node ${profile.nodeVersion}`:''}`,category:profile.executionMode==='HYBRID'?'Recommended':'Available values'}));
    const inferredEntryRule=analysis?.metadata['entryRule.inferred']??localInspection.inferredEntryRule;
    const inferenceConfidence=Math.round((analysis===null?localInspection.confidence:Number(analysis.metadata['entryRule.confidence']??localInspection.confidence))*100);
    const parserRules=(analysis?.metadata['entryRule.candidates']??'').split('|').map(value=>value.trim()).filter(Boolean);
    const availableRules=parserRules.length>0?parserRules:(localInspection.parserRules.length>0?localInspection.parserRules:Array.from(new Set((analysis?.symbols??[]).filter(symbol=>symbol.kind==='RULE').map(symbol=>symbol.name))));
    const inferenceReason=analysis?.metadata['entryRule.reason']??localInspection.reason;
    const automaticEntryValue='__HYDRA_AUTOMATIC_ENTRY_RULE__';
    const entryMode=model.annotations['hydra.entryRule.mode']??'AUTO';
    const entryOptions=[
        {value:automaticEntryValue,label:inferredEntryRule===''?'Automatic detection':`Automatic · ${inferredEntryRule}`,description:inferredEntryRule===''?t('HYDRA is analyzing the grammar to identify the most likely semantic root.'):`${t(inferenceReason)}${inferenceConfidence>0?` ${t('Confidence')} ${inferenceConfidence}%.`:''}`,category:'Recommended',disabled:inferredEntryRule===''},
        ...availableRules.map(rule=>({value:rule,label:rule,description:rule===inferredEntryRule?t('Rule inferred by HYDRA as the most likely semantic root. Select it explicitly only when you want to stop following future automatic inference.'):t('Parser rule available as an explicit document root.'),category:rule===inferredEntryRule?'Recommended':'Available values'}))
    ];
    const entrySelectValue=entryMode==='MANUAL'?model.entryRule:automaticEntryValue;
    const changeEntryRule=(value:string)=>{
        if(value===automaticEntryValue){
            if(inferredEntryRule==='')return;
            onMutate({...model,entryRule:inferredEntryRule,annotations:{...model.annotations,'hydra.entryRule.mode':'AUTO'}});
            return;
        }
        onMutate({...model,entryRule:value,annotations:{...model.annotations,'hydra.entryRule.mode':'MANUAL'}});
    };

    return <section className="grammar-identity-grid" aria-label="Grammar identity and semantic setup">
        <HydraFormField label="ID" targetId="grammar-id" required help="Stable technical identifier used by persistence, snapshots, generated artifacts and cross-layer references. Keep it short and do not reuse an ID for a different language." example="orders-language">
            <input id="grammar-id" value={model.id} onChange={event=>onMutate({...model,id:event.target.value})}/>
        </HydraFormField>
        <HydraFormField label="Name" targetId="grammar-name" required help="Human-readable language name shown in HYDRA. It does not replace the stable technical ID and can be changed independently." example="Orders language">
            <input id="grammar-name" value={model.name} onChange={event=>onMutate({...model,name:event.target.value})}/>
        </HydraFormField>
        <HydraFormField label="Technology" targetId="grammar-technology" required help="Select the native grammar ecosystem. HYDRA keeps one neutral semantic contract, while this choice selects the provider, parser conventions and generated project profile." example="Langium" options={technologyOptions}>
            <HydraAdaptiveSelect id="grammar-technology" ariaLabel="Technology" value={displayedTechnology} options={technologyOptions} onChange={value=>onTechnologyChange(value as GrammarTechnology)} showOptionHelp={false}/>
        </HydraFormField>
        <HydraFormField label="Authoring mode" targetId="grammar-authoring-mode" required help="Choose where semantic structure comes from. Grammar first infers it from the language; Linked reconciles both sides; Metamodel first treats the selected metamodel as the reference structure." example="Grammar first" options={authoringOptions}>
            <HydraAdaptiveSelect id="grammar-authoring-mode" ariaLabel="Authoring mode" value={model.authoringMode} options={authoringOptions} onChange={value=>onMutate({...model,authoringMode:value as GrammarDefinitionModel['authoringMode']})} showOptionHelp={false}/>
        </HydraFormField>
        <HydraFormField label="Toolchain" targetId="grammar-toolchain" required help="Versioned provider/build profile used for native validation and project generation. Prefer the modern recommended profile unless an existing language must remain on a legacy runtime." example="langium-4.4-browser" options={toolchainOptions}>
            <HydraAdaptiveSelect id="grammar-toolchain" ariaLabel="Toolchain" value={model.toolchainProfileId} options={toolchainOptions} onChange={value=>onMutate({...model,toolchainProfileId:value})} showOptionHelp={false}/>
        </HydraFormField>
        <HydraFormField label="Entry rule" targetId="grammar-entry-rule" required help="HYDRA analyzes the parser-rule graph and preselects the most likely document root. Explicit entry markers and native platform conventions take precedence; when several roots are possible, graph reachability and the configured entry document are used. You can always select another parser rule manually or return to automatic detection." example="Automatic · Model" options={entryOptions}>
            <div className="grammar-entry-rule-field">
                <HydraAdaptiveSelect id="grammar-entry-rule" ariaLabel="Entry rule" required value={entrySelectValue} options={entryOptions} onChange={changeEntryRule} showOptionHelp={false}/>
            </div>
        </HydraFormField>
        <GrammarMetamodelField value={model.metamodelId} authoringMode={model.authoringMode} metamodels={metamodels} onChange={metamodelId=>onMutate({...model,metamodelId,authoringMode:metamodelId===''&&model.authoringMode!=='GRAMMAR_FIRST'?'GRAMMAR_FIRST':model.authoringMode})} onImported={onMetamodelsRefresh}/>
    </section>;
}
