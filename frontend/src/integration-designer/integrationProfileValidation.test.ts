import { describe,expect,it } from 'vitest';
import { createIntegrationProfile } from './integrationProfileFactory';
import { findIntegrationProfileBlockingError } from './integrationProfileValidation';
import type { IntegrationTechnologyDescriptor } from './types';
const plant:IntegrationTechnologyDescriptor={technology:'PLANTUML',displayName:'PlantUML',targetKind:'DIAGRAM_PROJECTION',recommendedDeliveryMode:'PROJECTION_ONLY',recommendedInjectionStrategy:'GENERATE_PROJECTION',recommendedBuildSystem:'NONE',deliveryModes:['PROJECTION_ONLY'],injectionStrategies:['GENERATE_PROJECTION'],locatorStrategies:['PLANTUML_ALIAS'],capabilities:{},notes:[]};
const besser:IntegrationTechnologyDescriptor={technology:'BESSER_SDK',displayName:'BESSER',targetKind:'MODELING_RUNTIME',recommendedDeliveryMode:'GENERATED_MODULE',recommendedInjectionStrategy:'ADD_PACKAGE',recommendedBuildSystem:'PIP',deliveryModes:['GENERATED_MODULE'],injectionStrategies:['ADD_PACKAGE'],locatorStrategies:['BESSER_ELEMENT_ID'],capabilities:{},notes:[]};
describe('integration profile preflight',()=>{
 it('accepts a safe PlantUML projection',()=>expect(findIntegrationProfileBlockingError(createIntegrationProfile('people',plant))).toBe(''));
 it('rejects runtime execution in PlantUML',()=>{const value=createIntegrationProfile('people',plant);value.features=['EVENT_PUBLICATION'];expect(findIntegrationProfileBlockingError(value)).toContain('cannot execute');});
 it('rejects UI contributions for BESSER SDK',()=>{const value=createIntegrationProfile('people',besser);value.features=['GAMIFICATION_UI'];value.artifacts.push({kind:'GAMIFICATION_DEFINITIONS',documentId:'people-gamification',required:true});expect(findIntegrationProfileBlockingError(value)).toContain('Web Modeling Editor');});
 it('requires a concrete host locator in mappings',()=>{const value=createIntegrationProfile('people',besser);value.mappings=[{id:'mapping:person',kind:'TYPE',canonicalDocumentId:'people',canonicalElementId:'class:Person',required:true,annotations:{},hostReference:{strategy:'BESSER_ELEMENT_ID',documentUri:'',namespace:'',qualifiedName:'',externalId:'',featureName:'',path:'',attributes:{}}}];expect(findIntegrationProfileBlockingError(value)).toContain('host locator');});
});
