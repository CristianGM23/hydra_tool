import { describe,expect,it } from 'vitest';
import { createIntegrationProfile } from './integrationProfileFactory';
import type { IntegrationTechnologyDescriptor } from './types';
function descriptor(technology:IntegrationTechnologyDescriptor['technology']):IntegrationTechnologyDescriptor{return {technology,displayName:technology,targetKind:technology==='PLANTUML'?'DIAGRAM_PROJECTION':'MODELING_RUNTIME',recommendedDeliveryMode:technology==='PLANTUML'?'PROJECTION_ONLY':'GENERATED_MODULE',recommendedInjectionStrategy:technology==='PLANTUML'?'GENERATE_PROJECTION':'ADD_PACKAGE',recommendedBuildSystem:technology==='PLANTUML'?'NONE':technology==='BESSER_SDK'?'PIP':'NPM',deliveryModes:technology==='PLANTUML'?['PROJECTION_ONLY']:['GENERATED_MODULE'],injectionStrategies:technology==='PLANTUML'?['GENERATE_PROJECTION']:['ADD_PACKAGE'],locatorStrategies:technology==='PLANTUML'?['PLANTUML_ALIAS']:['BESSER_ELEMENT_ID'],capabilities:{},notes:[]};}
describe('integration profile factory',()=>{
 it('creates projection-only PlantUML profiles',()=>{const value=createIntegrationProfile('people',descriptor('PLANTUML'));expect(value.deliveryMode).toBe('PROJECTION_ONLY');expect(value.injectionPolicy.strategy).toBe('GENERATE_PROJECTION');expect(value.artifacts).toContainEqual({kind:'EVENT_DEFINITIONS',documentId:'people-events',required:true});});
 it('creates BESSER profiles without changing the metamodel designer palette',()=>{const value=createIntegrationProfile('people',descriptor('BESSER_SDK'));expect(value.target.buildSystem).toBe('PIP');expect(value.features).toEqual(['EVENT_PUBLICATION','EVENT_CONSUMPTION']);});
 it('creates Eclipse GLSP profiles as additive npm packages',()=>{const value=createIntegrationProfile('people',descriptor('ECLIPSE_GLSP'));expect(value.target.buildSystem).toBe('NPM');expect(value.injectionPolicy.strategy).toBe('ADD_PACKAGE');expect(value.deliveryMode).toBe('GENERATED_MODULE');});
 it('creates mxGraph and draw.io integrations as additive npm packages',()=>{const value=createIntegrationProfile('people',{...descriptor('MXGRAPH_DRAWIO'),targetKind:'GRAPHICAL_MODELING_EDITOR',recommendedDeliveryMode:'GENERATED_MODULE',recommendedInjectionStrategy:'ADD_PACKAGE',recommendedBuildSystem:'NPM',deliveryModes:['GENERATED_MODULE'],injectionStrategies:['ADD_PACKAGE'],locatorStrategies:['CUSTOM','QUALIFIED_NAME','DOCUMENT_PATH']});expect(value.target.buildSystem).toBe('NPM');expect(value.injectionPolicy.strategy).toBe('ADD_PACKAGE');expect(value.deliveryMode).toBe('GENERATED_MODULE');});
});

it('uses a grammar artifact as the semantic root for grammar-first contexts',()=>{
 const profile=createIntegrationProfile('grammar.main-language',descriptor('ECLIPSE_GLSP'));
 expect(profile.semanticScopeId).toBe('grammar.main-language');
 expect(profile.metamodelId).toBe('');
 expect(profile.artifacts.some(item=>item.kind==='GRAMMAR'&&item.documentId==='main-language'&&item.required)).toBe(true);
 expect(profile.artifacts.some(item=>item.kind==='METAMODEL')).toBe(false);
});
