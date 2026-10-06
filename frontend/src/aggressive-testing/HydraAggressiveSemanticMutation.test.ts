import { describe, expect, it } from 'vitest';
import { canonicalMetaModelFixture } from '../metamodel-designer/test-fixtures/canonicalMetaModelFixture';
import { addRelation, removeClass, removeEnum, updateClass } from '../metamodel-designer/metaModelOperations';
import { createMetaAttribute, createMetaClass, createMetaRelation, primitiveType } from '../metamodel-designer/metaModelFactory';
import { createEmptyEditorDefinition, createNodeTool } from '../editor-definition/editorDefinitionFactory';
import { normalizeEditorSessionView } from '../editor-definition/editorRuntimeNormalization';
import type { EditorSessionView } from '../editor-definition/types';
import { findEditorDefinitionBlockingError } from '../editor-definition/editorDefinitionValidation';
import { createDefaultAction, createDefaultExpression, createDefaultOperation, createDefaultValidationRule, createEmptyActionModel, createEmptyExpressionModel, createEmptyValidationModel } from '../validation-designer/validationDesignerFactory';
import { findValidationDesignerBlockingError } from '../validation-designer/validationDesignerValidation';
import { createDefaultEventBinding, createDefaultEventField, createDefaultEventType, createEmptyEventModels } from '../event-designer/eventDesignerFactory';
import { findEventDesignerBlockingError } from '../event-designer/eventDesignerValidation';
import { createDefaultFlow, createEmptyEventFlowDiagram, createEmptyEventFlowModel, createFlowNode, createFlowTransition, synchronizeEventFlowDiagram } from '../event-designer/flow/eventFlowFactory';
import { createEmptyGamificationModels, createLevel, createPointType, createReward, createRule as createGamificationRule } from '../gamification-designer/gamificationFactory';
import { findGamificationBlockingError } from '../gamification-designer/gamificationValidation';
import { addDirectBinding, createEmptyTransformation, createRule as createTransformationRule, mapCompatibleNames, withEndpointMetamodel, withTransformationKind } from '../transformation-designer/transformationDesignerModel';
import type { MetaClass, MetaModel } from '../metamodel-designer/types';
import type { GrammarDefinitionModel, GrammarTechnology } from '../grammar-designer/types';
import { textualModelCompletions } from '../textual-model-editor/textualModelCompletion';
import { createIntegrationProfile } from '../integration-designer/integrationProfileFactory';
import { findIntegrationProfileBlockingError } from '../integration-designer/integrationProfileValidation';
import type { IntegrationTechnologyDescriptor } from '../integration-designer/types';

const attr = (id:string,name:string) => ({ id, name, type:primitiveType('STRING'), multiplicity:{lowerBound:0,upperBound:1}, ordered:true, unique:true, defaultValue:'', annotations:{} });
const cls = (id:string,name:string):MetaClass => ({id,name,kind:'CLASS',attributes:[attr(`${id}:name`,'name')],operations:[],annotations:{}});
const mm = (id:string, classes:MetaClass[]):MetaModel => ({schemaVersion:'2.1',id,name:id,classes,enums:[],dataTypes:[],relations:[],annotations:{}});

function grammar(technology:GrammarTechnology,content:string):GrammarDefinitionModel {
    return {schemaVersion:'1.0',id:'aggressive',name:'Aggressive',technology,authoringMode:'GRAMMAR_FIRST',metamodelId:'',toolchainProfileId:'',entryDocumentId:'main',entryRule:technology==='ANTLR'?'model':'Model',documents:[{id:'main',relativePath:'main',languageId:technology.toLowerCase(),content,sourceHash:'',entryPoint:true,generated:false}],semanticBindings:{schemaVersion:'1.0',bindings:[],annotations:{}},syntaxStyleDefinitionId:'',settings:{},annotations:{}};
}

const plant:IntegrationTechnologyDescriptor={technology:'PLANTUML',displayName:'PlantUML',targetKind:'DIAGRAM_PROJECTION',recommendedDeliveryMode:'PROJECTION_ONLY',recommendedInjectionStrategy:'GENERATE_PROJECTION',recommendedBuildSystem:'NONE',deliveryModes:['PROJECTION_ONLY'],injectionStrategies:['GENERATE_PROJECTION'],locatorStrategies:['PLANTUML_ALIAS'],capabilities:{},notes:[]};

describe('aggressive cross-capability semantic mutation campaign', () => {
    it('survives a high-fan-out metamodel mutation without leaving dangling relations or mutating the baseline', () => {
        let model = canonicalMetaModelFixture();
        const baseline = JSON.stringify(model);
        const generated = Array.from({length:80},(_,i)=>({...createMetaClass(`Generated${i}`),id:`generated:${i}`,attributes:[{...createMetaAttribute(),id:`generated:${i}:name`,name:'name'}]}));
        model = {...model, classes:[...model.classes,...generated]};
        for (let i=1;i<generated.length;i++) model = addRelation(model,{...createMetaRelation(generated[0]!.id,generated[i]!.id,'REFERENCE',`link${i}`),id:`generated:relation:${i}`});
        const reduced = removeClass(model,generated[0]!.id);
        expect(reduced.classes).toHaveLength(model.classes.length-1);
        expect(reduced.relations.some(r=>r.sourceTypeId===generated[0]!.id||r.targetTypeId===generated[0]!.id)).toBe(false);
        expect(JSON.stringify(canonicalMetaModelFixture())).toBe(baseline);
    });

    it('cascades classifier removal into dependent semantic elements instead of leaving a half-valid product', () => {
        const original = canonicalMetaModelFixture();
        const renamed = updateClass(original,'customer',value=>({...value,name:'CustomerRenamed'}));
        expect(original.classes.find(value=>value.id==='customer')?.name).toBe('Customer');
        expect(renamed.classes.find(value=>value.id==='customer')?.name).toBe('CustomerRenamed');
        const withoutStatus = removeEnum(renamed,'status');
        expect(withoutStatus.classes.flatMap(value=>value.attributes).some(value=>value.type.targetId==='status')).toBe(false);
    });

    it('round-trips an editor definition as JSON and rejects dangling palette tools after corruption', () => {
        const source = createEmptyEditorDefinition('people');
        source.palette.tools.push(createNodeTool('class:Person',source.representations[0]!.id,'Person'));
        const roundTrip = JSON.parse(JSON.stringify(source)) as typeof source;
        expect(findEditorDefinitionBlockingError(roundTrip)).toBe('');
        roundTrip.palette.tools.push(createNodeTool('class:Ghost','representation:missing','Ghost'));
        expect(findEditorDefinitionBlockingError(roundTrip)).toContain('unknown representation');
    });

    it('normalizes a large model-editor projection without inventing or dropping semantic identities', () => {
        const elements=Array.from({length:1_200},(_,index)=>({id:`element:${index}`,classId:'class:Person',attributes:{name:`Person ${index}`},references:{}}));
        const nodes=elements.map((element,index)=>({elementId:element.id,classId:'class:Person',className:'Person',mappingId:'mapping:person',representationName:'Main',shape:'RECTANGLE',label:String(element.attributes.name),width:160,height:70,attributes:{name:element.attributes.name},styles:index%2===0?undefined:{opacity:'1'}}));
        const edges=Array.from({length:1_199},(_,index)=>({relationInstanceId:`relation:${index}`,relationId:'relation:next',relationName:'next',mappingId:'mapping:next',sourceElementId:`element:${index}`,targetElementId:`element:${index+1}`,lineType:'SOLID',label:'',styles:undefined}));
        const raw={session:{schemaVersion:'1.0',id:'session:aggressive',metamodelId:'people',editorId:'editor:people',sourceEditorEtag:'etag',activeRepresentationId:'main',openRepresentationIds:undefined,model:{schemaVersion:'1.0',id:'model:aggressive',metamodelId:'people',rootElementIds:['element:0'],elements,relations:[],annotations:undefined},latestValidationReport:null,revision:99,createdAt:'',updatedAt:'',annotations:undefined},etag:'etag',representationId:'main',representationName:'Main',representationKind:'DIAGRAM',editable:true,nodes,edges,paletteGroups:undefined,toolStates:undefined,selectedForm:null,representations:undefined,validationReport:null,undoAvailable:true,redoAvailable:false} as unknown as EditorSessionView;
        const normalized=normalizeEditorSessionView(raw);
        expect(normalized.session.model.elements).toHaveLength(1_200);
        expect(normalized.nodes).toHaveLength(1_200);
        expect(normalized.edges).toHaveLength(1_199);
        expect(normalized.nodes.map(node=>node.elementId)).toEqual(elements.map(element=>element.id));
        expect(new Set(normalized.edges.flatMap(edge=>[edge.sourceElementId,edge.targetElementId]))).toEqual(new Set(elements.map(element=>element.id)));
        expect(normalized.nodes.every(node=>node.styles!==undefined&&node.containmentRelationIds!==undefined&&node.availableAppearances!==undefined)).toBe(true);
        expect(normalized.session.model.elements).not.toBe(elements);
    });

    it('keeps validation and quick-fix documents mutually consistent across serialization and corruption', () => {
        const metamodel = canonicalMetaModelFixture();
        const expressions = createEmptyExpressionModel('shop');
        const expression = createDefaultExpression('customer');
        expressions.expressions.push(expression);
        const actions = createEmptyActionModel('shop',expressions.id);
        const action = {...createDefaultAction(),operations:[createDefaultOperation('TRANSFORM_TEXT',metamodel)]};
        actions.actions.push(action);
        const validations = createEmptyValidationModel('shop',expressions.id,actions.id);
        validations.rules.push({...createDefaultValidationRule(metamodel,expression),quickFixIds:[action.id]});
        const serialized = JSON.parse(JSON.stringify({expressions,actions,validations}));
        expect(findValidationDesignerBlockingError(serialized.expressions,serialized.actions,serialized.validations)).toBe('');
        serialized.actions.actions[0].operations=[];
        expect(findValidationDesignerBlockingError(serialized.expressions,serialized.actions,serialized.validations)).toContain('at least one operation');
    });

    it('propagates event-contract changes into binding validity instead of silently accepting missing required payload', () => {
        const value=createEmptyEventModels('people');
        const type={...createDefaultEventType(value.definitions.namespace),fields:[{...createDefaultEventField(),id:'field:required',name:'customerId',required:true}]};
        value.definitions.eventTypes=[type];
        value.bindings.bindings=[createDefaultEventBinding('core.model.saved',type.id)];
        expect(findEventDesignerBlockingError(value.definitions,value.bindings)).toContain('required field');
        value.bindings.bindings[0]!.payloadMappings=[{sourcePath:'context.modelId',targetFieldId:'field:required',expressionId:''}];
        expect(findEventDesignerBlockingError(value.definitions,value.bindings)).toBe('');
    });

    it('makes EventFlow diagram synchronization idempotent under repeated edits and removes stale visual nodes', () => {
        const model=createEmptyEventFlowModel('people','people-events');
        const flow=createDefaultFlow();
        const extra=createFlowNode('MAP');
        flow.nodes=[...flow.nodes,extra];
        flow.transitions=[createFlowTransition(flow.nodes[0]!.id,extra.id),createFlowTransition(extra.id,flow.nodes[1]!.id)];
        model.flows=[flow];
        let diagram=createEmptyEventFlowDiagram(model);
        for(let i=0;i<50;i++) diagram=synchronizeEventFlowDiagram(model,diagram);
        expect(new Set(diagram.nodes.map(node=>node.nodeId)).size).toBe(flow.nodes.length);
        model.flows=[{...flow,nodes:flow.nodes.filter(node=>node.id!==extra.id),transitions:[]}];
        diagram=synchronizeEventFlowDiagram(model,diagram);
        expect(diagram.nodes.some(node=>node.nodeId===extra.id)).toBe(false);
    });

    it('rejects broken gamification dependencies after an upstream reward/point mutation', () => {
        const value=createEmptyGamificationModels('people');
        const point=createPointType(); const reward=createReward();
        value.definitions.pointTypes=[point]; value.definitions.rewards=[reward];
        value.definitions.levels=[{...createLevel(point.id),rewardId:reward.id}];
        value.bindings.rules=[createGamificationRule('core.validation.violation-resolved',point.id)];
        expect(findGamificationBlockingError(value.definitions,value.bindings)).toBe('');
        value.definitions.rewards=[];
        expect(findGamificationBlockingError(value.definitions,value.bindings)).toContain('reward');
        value.definitions.levels=[]; value.definitions.pointTypes=[];
        expect(findGamificationBlockingError(value.definitions,value.bindings)).toContain('point type');
    });

    it('keeps transformation identity and deterministic compatible mappings through M2M/M2T/T2M changes', () => {
        const source=cls('source:Person','Person'); const target=cls('target:PersonDto','PersonDto');
        target.attributes.push(attr('target:other','other'));
        let document=createEmptyTransformation('source','target');
        const base=createTransformationRule(document,source,target);
        const mapped=mapCompatibleNames(base,source,target);
        expect(mapped.bindings).toHaveLength(1);
        expect(addDirectBinding(base,source.attributes[0]!,target.attributes[0]!).bindings[0]).toMatchObject({sourceFeatureId:'source:Person:name',targetFeatureId:'target:PersonDto:name'});
        const id=document.id;
        document=withTransformationKind(document,'M2T');
        document=withTransformationKind(document,'T2M');
        document=withEndpointMetamodel(document,'SOURCE','source-v2');
        expect(document.id).toBe(id);
        expect(document.sources[0].metamodelId).toBe('source-v2');
        expect(mm('source',[source]).schemaVersion).toBe('2.1');
    });

    it('keeps textual completion bounded after thousands of repeated elements and at cursor boundaries', () => {
        const langium=grammar('LANGIUM',`grammar Main\nentry Model: elements+=Element*;\nElement: 'element' name=ID;\nterminal ID: /[_a-zA-Z][\\w_]*/;`);
        const text=Array.from({length:10_000},(_,index)=>`element n${index}`).join(' ')+' ';
        const completions=textualModelCompletions(langium,text,text.length);
        expect(completions[0]?.insertText).toBe('element');
        expect(completions.length).toBeLessThanOrEqual(20);
        expect(textualModelCompletions(langium,'ele',0)).toBeDefined();
    });

    it('prevents an integration profile mutation from silently enabling runtime behavior on a projection-only target', () => {
        const profile=createIntegrationProfile('people',plant);
        expect(findIntegrationProfileBlockingError(profile)).toBe('');
        profile.features=['EVENT_PUBLICATION'];
        expect(findIntegrationProfileBlockingError(profile)).toContain('cannot execute');
        profile.features=[];
        profile.injectionPolicy={...profile.injectionPolicy,previewRequired:false,rollbackRequired:false};
        const serialized=JSON.parse(JSON.stringify(profile));
        expect(serialized.target.technology).toBe('PLANTUML');
    });
});
