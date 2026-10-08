import type { IntegrationSupportLevel,IntegrationTechnology,TechnologyDeliveryActionDescriptor,TechnologyDeliveryActionState,TechnologyDeliveryReadiness,TechnologyWorkflowAction,TechnologyWorkflowStage } from './types';

const TECHNOLOGIES=new Set<IntegrationTechnology>(['EMF','SIRIUS_DESKTOP','XTEXT','LANGIUM','ANTLR','BESSER_SDK','BESSER_WEB_MODELING_EDITOR','ECLIPSE_GLSP','ECLIPSE_MODELING_DISTRIBUTION','VSCODE_EXTENSION','MXGRAPH_DRAWIO','PLANTUML']);
const STAGES=new Set<TechnologyWorkflowStage>(['CONFIGURE','ANALYZE','GENERATE','VERIFY','PREVIEW','APPLY','EXECUTE','ROUND_TRIP','EXPORT']);
const ACTIONS=new Set<TechnologyWorkflowAction>(['EDIT_PROFILE','CHECK_PROFILE','COMPILE_ARCHIVE','VALIDATE_PROJECT','PREVIEW_CHANGES','APPLY_CHANGES','RUN_NATIVE_HOST','ROUND_TRIP_SYNC','EXPORT_PROJECT','EXPORT_PROJECTION','NONE']);
const STATES=new Set<TechnologyDeliveryActionState>(['READY','READY_WITH_WARNINGS','BLOCKED','EXTERNAL','NOT_APPLICABLE']);
const SUPPORT=new Set<IntegrationSupportLevel>(['FULL','PARTIAL','EXTERNAL','UNSUPPORTED']);
const object=(value:unknown):Record<string,unknown>=>typeof value==='object'&&value!==null&&!Array.isArray(value)?value as Record<string,unknown>:{};
const text=(value:unknown):string=>typeof value==='string'?value:'';
const bool=(value:unknown):boolean=>value===true;
const integer=(value:unknown):number=>typeof value==='number'&&Number.isFinite(value)?Math.max(0,Math.trunc(value)):0;
const strings=(value:unknown):string[]=>Array.isArray(value)?value.filter((item):item is string=>typeof item==='string'):[];

function action(value:unknown):TechnologyDeliveryActionDescriptor{
 const source=object(value);
 const stage=STAGES.has(source.stage as TechnologyWorkflowStage)?source.stage as TechnologyWorkflowStage:'CONFIGURE';
 const operation=ACTIONS.has(source.action as TechnologyWorkflowAction)?source.action as TechnologyWorkflowAction:'NONE';
 const state=STATES.has(source.state as TechnologyDeliveryActionState)?source.state as TechnologyDeliveryActionState:'BLOCKED';
 const support=SUPPORT.has(source.support as IntegrationSupportLevel)?source.support as IntegrationSupportLevel:'UNSUPPORTED';
 return {stage,action:operation,state,support,hydraExecutable:bool(source.hydraExecutable),mutatesHost:bool(source.mutatesHost),targetDirectoryRequired:bool(source.targetDirectoryRequired),previewRequired:bool(source.previewRequired),apiSuffix:text(source.apiSuffix),httpMethod:text(source.httpMethod),reason:text(source.reason),prerequisites:strings(source.prerequisites),evidence:strings(source.evidence)};
}

/** Normaliza fail-closed la preparación operativa recibida del backend. */
export function normalizeTechnologyDeliveryReadiness(value:unknown):TechnologyDeliveryReadiness{
 const source=object(value);
 const technology=TECHNOLOGIES.has(source.technology as IntegrationTechnology)?source.technology as IntegrationTechnology:'ANTLR';
 return {profileId:text(source.profileId),technology,profileValid:bool(source.profileValid),compilationReady:bool(source.compilationReady),generatedFileCount:integer(source.generatedFileCount),nativeVerificationStepCount:integer(source.nativeVerificationStepCount),nativeVerificationAvailable:bool(source.nativeVerificationAvailable),actions:Array.isArray(source.actions)?source.actions.map(action):[],blockers:strings(source.blockers),warnings:strings(source.warnings)};
}
