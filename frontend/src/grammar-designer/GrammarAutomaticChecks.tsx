import { HydraButton, HydraTooltip } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { GrammarAnalysisResult, GrammarClassDiagramProjection, GrammarCompilationSnapshot, GrammarDefinitionModel, GrammarToolchainProfile } from './types';

interface Props {
    model: GrammarDefinitionModel;
    analysis: GrammarAnalysisResult | null;
    projection: GrammarClassDiagramProjection | null;
    snapshot: GrammarCompilationSnapshot | null;
    profiles: readonly GrammarToolchainProfile[];
    onOpenProblems: () => void;
    onOpenDiagram: () => void;
}

type LayerStatus='OK'|'ERROR'|'PENDING';
interface Layer {id:string;title:string;description:string;status:LayerStatus;detail:string;action?:()=>void;}

export function GrammarAutomaticChecks({model,analysis,projection,snapshot,profiles,onOpenProblems,onOpenDiagram}:Props){
    const {t}=useHydraLanguage();
    const errors=analysis?.diagnostics.filter(item=>item.severity==='ERROR')??[];
    const syntaxErrors=errors.filter(item=>item.code==='GRAMMAR_SYNTAX_ERROR'||item.code==='GRAMMAR_DUPLICATE_SYMBOL');
    const referenceErrors=errors.filter(item=>item.code.startsWith('REFERENCE_')||item.code.includes('IMPORT'));
    const stageByName=new Map((snapshot?.stages??[]).map(stage=>[stage.stage,stage]));
    const stageStatus=(names:string[]):LayerStatus=>{
        const stages=names.map(name=>stageByName.get(name)).filter(Boolean);
        if(stages.some(stage=>stage?.status==='FAILED'||stage?.status==='CANCELLED'))return 'ERROR';
        if(stages.length===names.length&&stages.every(stage=>stage?.status==='SUCCESS'||stage?.status==='SKIPPED'))return 'OK';
        return 'PENDING';
    };
    const profileOk=profiles.some(profile=>profile.id===model.toolchainProfileId&&profile.technology===model.technology);
    const layers:Layer[]=[
        {id:'setup',title:t('Configuration'),description:t('Technology, entry rule and compatible build profile.'),status:profileOk&&model.entryRule.trim()!==''?'OK':'ERROR',detail:profileOk?t('Configuration ready'):t('Review technology or build profile'),action:profileOk&&model.entryRule.trim()!==''?undefined:onOpenProblems},
        {id:'syntax',title:t('Grammar syntax'),description:t('Checks that the grammar can be parsed safely.'),status:analysis===null?'PENDING':syntaxErrors.length===0?'OK':'ERROR',detail:analysis===null?t('Analyzing automatically'):syntaxErrors.length===0?t('No syntax errors'):t('Blocking syntax errors detected'),action:syntaxErrors.length>0?onOpenProblems:undefined},
        {id:'references',title:t('Names and references'),description:t('Checks imports and links between grammar rules.'),status:analysis===null?'PENDING':referenceErrors.length===0?'OK':'ERROR',detail:analysis===null?t('Analyzing automatically'):referenceErrors.length===0?t('References resolved'):`${referenceErrors.length} ${t('problems found')}`,action:referenceErrors.length>0?onOpenProblems:undefined},
        {id:'model',title:t('Inferred model'),description:t('Projects classes, attributes and relationships inferred from the grammar.'),status:projection===null?'PENDING':projection.status==='FAILED'||projection.status==='EMPTY'?'ERROR':projection.status==='PARTIAL'?'ERROR':'OK',detail:projection===null?t('Waiting for semantic analysis'):`${projection.nodes.length} ${t('classifiers')} · ${projection.edges.length} ${t('relations')}`,action:onOpenDiagram},
        {id:'layers',title:t('HYDRA features'),description:t('Checks reusable validations, events, flows and gamification associated with this language.'),status:snapshot===null?(errors.length===0&&analysis!==null?'PENDING':'PENDING'):stageStatus(['CHECK_HYDRA_LAYERS']),detail:snapshot===null?t('Checked during compilation'):stageStatus(['CHECK_HYDRA_LAYERS'])==='OK'?t('Compatible HYDRA features verified'):t('Review associated HYDRA features'),action:snapshot!==null&&stageStatus(['CHECK_HYDRA_LAYERS'])==='ERROR'?onOpenProblems:undefined},
        {id:'generation',title:t('Generated infrastructure'),description:t('Generates and verifies the parser, runtime and editor services.'),status:snapshot===null?'PENDING':stageStatus(['GENERATE_PROJECT','MATERIALIZE_RUNTIME','SMOKE_TEST']),detail:snapshot===null?t('Compile when you want to generate the infrastructure'):snapshot.status==='SUCCESS'?t('Infrastructure generated successfully'):t('Generation stopped at the failing layer'),action:snapshot?.status==='FAILED'?onOpenProblems:undefined}
    ];
    return <section className="grammar-auto-checks" aria-label={t('Automatic grammar checks')}>
        <header><div><strong>{t('Automatic grammar checks')}</strong><span>{t('HYDRA checks every layer automatically. You can compile at any time; no guided steps are required.')}</span></div></header>
        <div className="grammar-auto-check-grid hydra-guided-stepper">{layers.map((layer,index)=>{
            const state=layer.status==='OK'?'complete':layer.status==='ERROR'?'error':'pending';
            const body=<><span className="grammar-auto-check-status hydra-guided-status-dot" aria-label={t(layer.status==='OK'?'Correct':layer.status==='ERROR'?'Needs attention':'Pending')}>{layer.status==='OK'?'✓':layer.status==='ERROR'?'!':'…'}</span><span className="grammar-auto-check-copy"><strong>{index+1}. {layer.title}</strong><small>{layer.description}</small><em>{layer.detail}</em></span><HydraTooltip label={`${t('Information about')} ${layer.title}`} title={layer.title}><p>{layer.description}</p><p><strong>{t('Current status')}</strong> {layer.detail}</p></HydraTooltip></>;
            return layer.action?<HydraButton className={`grammar-auto-check ${state}`} key={layer.id} type="button" onClick={layer.action} title={t(layer.id==='model'?'Open this view':'Open related details')}>{body}</HydraButton>:<div className={`grammar-auto-check ${state}`} key={layer.id}>{body}</div>;
        })}</div>
    </section>;
}
