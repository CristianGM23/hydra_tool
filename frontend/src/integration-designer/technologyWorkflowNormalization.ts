import type {
    IntegrationSupportLevel,
    IntegrationTargetKind,
    IntegrationTechnology,
    TechnologyWorkflowAction,
    TechnologyWorkflowDescriptor,
    TechnologyWorkflowMaturity,
    TechnologyWorkflowStage
} from './types';

const TECHNOLOGIES: readonly IntegrationTechnology[] = ['EMF','SIRIUS_DESKTOP','XTEXT','LANGIUM','ANTLR','BESSER_SDK','BESSER_WEB_MODELING_EDITOR','ECLIPSE_GLSP','ECLIPSE_MODELING_DISTRIBUTION','VSCODE_EXTENSION','MXGRAPH_DRAWIO','PLANTUML'];
const TARGET_KINDS: readonly IntegrationTargetKind[] = ['MODELING_RUNTIME','GRAPHICAL_MODELING_EDITOR','TEXTUAL_LANGUAGE_TOOLING','LOW_CODE_PLATFORM','DIAGRAM_PROJECTION','IDE_DISTRIBUTION','IDE_EXTENSION'];
const STAGES: readonly TechnologyWorkflowStage[] = ['CONFIGURE','ANALYZE','GENERATE','VERIFY','PREVIEW','APPLY','EXECUTE','ROUND_TRIP','EXPORT'];
const ACTIONS: readonly TechnologyWorkflowAction[] = ['EDIT_PROFILE','CHECK_PROFILE','COMPILE_ARCHIVE','VALIDATE_PROJECT','PREVIEW_CHANGES','APPLY_CHANGES','RUN_NATIVE_HOST','ROUND_TRIP_SYNC','EXPORT_PROJECT','EXPORT_PROJECTION','NONE'];
const MATURITY: readonly TechnologyWorkflowMaturity[] = ['FULL_WORKFLOW','OPERATIONAL','PARTIAL','DECLARED_ONLY','PROJECTION_ONLY'];
const SUPPORT: readonly IntegrationSupportLevel[] = ['FULL','PARTIAL','EXTERNAL','UNSUPPORTED'];

function object(value:unknown):Record<string,unknown>{
    if(value===null||typeof value!=='object'||Array.isArray(value))throw new Error('Technology workflow response contains an invalid entry.');
    return value as Record<string,unknown>;
}
function known<T extends string>(value:unknown,values:readonly T[]):value is T{return typeof value==='string'&&values.includes(value as T);}
function text(value:unknown):string{return typeof value==='string'?value.trim():'';}
function texts(value:unknown):string[]{return Array.isArray(value)?value.filter((item):item is string=>typeof item==='string').map(item=>item.trim()).filter(Boolean):[];}

export function normalizeTechnologyWorkflowDescriptor(value:unknown):TechnologyWorkflowDescriptor{
    const source=object(value);
    if(!known(source.technology,TECHNOLOGIES))throw new Error('Technology workflow contains an unsupported technology identifier.');
    if(!known(source.targetKind,TARGET_KINDS))throw new Error(`Technology workflow ${source.technology} has an invalid target kind.`);
    if(!known(source.maturity,MATURITY))throw new Error(`Technology workflow ${source.technology} has an invalid maturity level.`);
    if(!Array.isArray(source.stages))throw new Error(`Technology workflow ${source.technology} does not provide its process stages.`);
    const stages=source.stages.map(item=>{
        const stage=object(item);
        if(!known(stage.stage,STAGES)||!known(stage.support,SUPPORT)||!known(stage.action,ACTIONS))throw new Error(`Technology workflow ${source.technology} contains an invalid stage.`);
        return {stage:stage.stage,support:stage.support,applicable:stage.applicable!==false,action:stage.action,title:text(stage.title)||stage.stage,description:text(stage.description),outcome:text(stage.outcome),prerequisites:texts(stage.prerequisites)};
    });
    const seen=new Set<TechnologyWorkflowStage>();
    const unique=stages.filter(stage=>seen.has(stage.stage)?false:(seen.add(stage.stage),true));
    if(unique.length!==STAGES.length)throw new Error(`Technology workflow ${source.technology} must describe every process stage exactly once.`);
    const score=typeof source.parityScore==='number'&&Number.isFinite(source.parityScore)?Math.max(0,Math.min(100,Math.round(source.parityScore))):0;
    return {technology:source.technology,displayName:text(source.displayName)||source.technology,targetKind:source.targetKind,maturity:source.maturity,parityScore:score,adapterImplemented:source.adapterImplemented===true,validatorImplemented:source.validatorImplemented===true,stages:unique,blockers:texts(source.blockers),recommendations:texts(source.recommendations)};
}

export function normalizeTechnologyWorkflowDescriptors(value:unknown):TechnologyWorkflowDescriptor[]{
    if(!Array.isArray(value))throw new Error('Technology workflow response must be a list.');
    const seen=new Set<IntegrationTechnology>();
    return value.map(normalizeTechnologyWorkflowDescriptor).filter(item=>seen.has(item.technology)?false:(seen.add(item.technology),true));
}
