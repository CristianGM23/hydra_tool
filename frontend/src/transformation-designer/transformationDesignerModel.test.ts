import { describe, expect, it } from 'vitest';
import type { MetaClass, MetaModel } from '../metamodel-designer/types';
import { addDirectBinding, createEmptyTransformation, createGenerationRule, createGuard, createRule, mapCompatibleNames, setAttributeBinding, withEndpointMetamodel, withTransformationKind } from './transformationDesignerModel';

const attribute = (id:string,name:string) => ({ id, name, type:{kind:'PRIMITIVE' as const,targetId:'STRING',externalName:''}, multiplicity:{lowerBound:0,upperBound:1}, ordered:true, unique:true, defaultValue:'', annotations:{} });
const sourceClass:MetaClass={id:'source:Person',name:'Person',kind:'CLASS',attributes:[attribute('source:name','name'),attribute('source:age','age')],operations:[],annotations:{}};
const targetClass:MetaClass={id:'target:PersonDto',name:'PersonDto',kind:'CLASS',attributes:[attribute('target:name','name'),attribute('target:age','age')],operations:[],annotations:{}};
const model=(id:string,classes:MetaClass[]):MetaModel=>({schemaVersion:'2.1',id,name:id,classes,enums:[],dataTypes:[],relations:[],annotations:{}});

describe('transformation designer canonical model',()=>{
    it('keeps endpoint identity while changing the selected metamodel',()=>{
        const initial=createEmptyTransformation('source-a','target-a');
        const changed=withEndpointMetamodel(initial,'SOURCE','source-b');
        expect(changed.sources[0].id).toBe(initial.sources[0].id);
        expect(changed.sources[0].metamodelId).toBe('source-b');
        expect(changed.sources[0].metamodelFingerprint).toBe('');
    });

    it('creates a neutral class rule and direct bindings without technology-specific syntax',()=>{
        const document=createEmptyTransformation('source','target');
        const rule=createRule(document,sourceClass,targetClass);
        const mapped=addDirectBinding(rule,sourceClass.attributes[0],targetClass.attributes[0]);
        expect(mapped.sourceTypeId).toBe(sourceClass.id);
        expect(mapped.targets[0].typeId).toBe(targetClass.id);
        expect(mapped.bindings[0]).toMatchObject({kind:'DIRECT',sourceFeatureId:'source:name',targetFeatureId:'target:name'});
        expect(JSON.stringify(mapped)).not.toMatch(/ATL|ETL|Xtend|Acceleo/);
    });

    it('maps compatible property names and preserves the same rule identity',()=>{
        const document=createEmptyTransformation('source','target');
        const rule=createRule(document,sourceClass,targetClass);
        const mapped=mapCompatibleNames(rule,sourceClass,targetClass);
        expect(mapped.id).toBe(rule.id);
        expect(mapped.bindings).toHaveLength(2);
        expect(new Set(mapped.bindings.map(item=>item.targetFeatureId))).toEqual(new Set(['target:name','target:age']));
    });

    it('creates a boolean guard scoped to the source type',()=>{
        const rule=createRule(createEmptyTransformation(),sourceClass,targetClass);
        const guard=createGuard(rule);
        expect(guard.contextClassId).toBe(sourceClass.id);
        expect(guard.declaredReturnType?.kind).toBe('BOOLEAN');
        expect(guard.root).toMatchObject({kind:'LITERAL',value:true});
    });


    it('switches M2M, M2T and T2M on the same canonical document identity',()=>{
        const original=createEmptyTransformation('source','target');
        const m2t=withTransformationKind(original,'M2T');
        const t2m=withTransformationKind(m2t,'T2M');
        expect(m2t.id).toBe(original.id);
        expect(t2m.id).toBe(original.id);
        expect(m2t.sources[0].metamodelId).toBe('source');
        expect(t2m.textToModel).toMatchObject({grammarId:'',relativePath:'model.hydra',postTransformationId:''});
    });

    it('creates M2T generation rules without coupling templates to a target metamodel',()=>{
        const document=withTransformationKind(createEmptyTransformation('source','target'),'M2T');
        const rule=createGenerationRule(document,sourceClass);
        expect(rule.sourceEndpointId).toBe(document.sources[0].id);
        expect(rule.contextTypeId).toBe(sourceClass.id);
        expect(rule.outputPathTemplate).toContain('{{attr:source:name}}');
        expect(rule.overwritePolicy).toBe('FAIL');
    });

    it('keeps value origin and adaptation policy orthogonal in attribute bindings',()=>{
        const rule=createRule(createEmptyTransformation('source','target'),sourceClass,targetClass);
        const constant=setAttributeBinding(rule,targetClass.attributes[0],'CONSTANT');
        const binding=constant.bindings[0];
        expect(binding.kind).toBe('CONSTANT');
        expect(binding.valuePolicy).toMatchObject({collectionMode:'PRESERVE',conversionMode:'NONE',errorPolicy:'FAIL'});
        expect(binding.sourceFeatureId).toBe('');
    });

    it('fixtures remain valid canonical metamodels',()=>{
        expect(model('source',[sourceClass]).schemaVersion).toBe('2.1');
    });
});
