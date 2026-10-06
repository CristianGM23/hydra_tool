import type { IntegrationBuildSystem,IntegrationDeliveryMode,IntegrationProfileModel,IntegrationTechnologyDescriptor,InjectionStrategy } from './types';
function documentId(value:string):string{return value.replace(/[^A-Za-z0-9._-]+/g,'-').replace(/^-+|-+$/g,'').slice(0,120)||'integration';}
export function defaultBuildSystem(descriptor:IntegrationTechnologyDescriptor):IntegrationBuildSystem{return descriptor.recommendedBuildSystem;}
export function preferredDelivery(descriptor:IntegrationTechnologyDescriptor):IntegrationDeliveryMode{return descriptor.recommendedDeliveryMode;}
export function preferredStrategy(descriptor:IntegrationTechnologyDescriptor):InjectionStrategy{return descriptor.recommendedInjectionStrategy;}
export function createIntegrationProfile(semanticScopeId:string,descriptor:IntegrationTechnologyDescriptor):IntegrationProfileModel{
    const id=documentId(`${semanticScopeId}-${descriptor.technology.toLowerCase()}-integration`);
    const grammarFirst=semanticScopeId.startsWith('grammar.');
    const metamodelId=grammarFirst?'':semanticScopeId;
    const grammarId=grammarFirst?semanticScopeId.slice('grammar.'.length):'';
    const semanticRoot=grammarFirst?{kind:'GRAMMAR' as const,documentId:grammarId,required:true}:{kind:'METAMODEL' as const,documentId:metamodelId,required:true};
    return {schemaVersion:'1.0',id,metamodelId,semanticScopeId,name:`${descriptor.displayName} integration`,target:{technology:descriptor.technology,kind:descriptor.targetKind,technologyVersion:'',projectId:documentId(`${semanticScopeId}-host-project`),moduleName:'',buildSystem:defaultBuildSystem(descriptor),sourceRoots:[],configurationFiles:[],endpoint:'',settings:{}},deliveryMode:preferredDelivery(descriptor),features:descriptor.technology==='PLANTUML'?['EVENT_DEFINITION_PROJECTION','PROJECT_EXPORT']:['EVENT_PUBLICATION','EVENT_CONSUMPTION'],artifacts:[semanticRoot,{kind:'EVENT_DEFINITIONS',documentId:`${semanticScopeId}-events`,required:true}],mappings:[],injectionPolicy:{strategy:preferredStrategy(descriptor),generatedRoot:descriptor.technology==='PLANTUML'?'docs/diagrams':'generated-integration',moduleName:'',previewRequired:true,rollbackRequired:true,preserveCustomCode:true,allowModifyExistingFiles:false,allowModifyGeneratedFiles:false,failOnPartialMappings:true},requiredCapabilities:[],annotations:{...(descriptor.technology==='PLANTUML'?{}:{'hydra.runtime.activation':'TOGGLEABLE','hydra.runtime.enabledByDefault':'false'}),'hydra.semanticRootKind':grammarFirst?'GRAMMAR':'METAMODEL'}};
}
