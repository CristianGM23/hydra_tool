package com.example.editor.integration.compiler;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.editor.integration.IntegrationArtifactKind;
import tools.jackson.databind.json.JsonMapper;

/** Shared projection of HYDRA visual-editor definition and neutral runtime layers into exported hosts. */
public final class TechnologyAdapterVisualFullStackSupport {
    /** Descripción: Inicializa una instancia de TechnologyAdapterVisualFullStackSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TechnologyAdapterVisualFullStackSupport() { }

    /** Descripción: Ejecuta la operación «available» correspondiente a la responsabilidad de TechnologyAdapterVisualFullStackSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean available(TechnologyCompilationRequest request) {
        return request.find(IntegrationArtifactKind.EDITOR_DEFINITION).isPresent();
    }

    /** Descripción: Ejecuta la operación «visualDefinitionJson» correspondiente a la responsabilidad de TechnologyAdapterVisualFullStackSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String visualDefinitionJson(TechnologyCompilationRequest request) {
        byte[] content = request.require(IntegrationArtifactKind.EDITOR_DEFINITION).content();
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            throw new TechnologyCompilationException("Visual editor definition must be valid UTF-8", exception);
        }
    }

    /** Descripción: Ejecuta la operación «manifest» correspondiente a la responsabilidad de TechnologyAdapterVisualFullStackSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static String manifest(TechnologyCompilationRequest request, JsonMapper mapper) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.ofEntries(
                Map.entry("schemaVersion", "1.1"),
                Map.entry("editorDefinition", true),
                Map.entry("validation", has(request, IntegrationArtifactKind.VALIDATIONS)),
                Map.entry("quickFixes", has(request, IntegrationArtifactKind.ACTIONS)),
                Map.entry("operations", has(request, IntegrationArtifactKind.OPERATION_BEHAVIORS)),
                Map.entry("events", has(request, IntegrationArtifactKind.EVENT_DEFINITIONS)),
                Map.entry("eventBindings", has(request, IntegrationArtifactKind.EVENT_BINDINGS)),
                Map.entry("eventFlows", has(request, IntegrationArtifactKind.EVENT_FLOWS)),
                Map.entry("gamification", has(request, IntegrationArtifactKind.GAMIFICATION_DEFINITIONS)),
                Map.entry("gamificationBindings", has(request, IntegrationArtifactKind.GAMIFICATION_BINDINGS)),
                Map.entry("visualPersonalization", Map.of(
                        "baseStyles", true,
                        "conditionalStyles", true,
                        "assets", true,
                        "failSafeConditionalFallback", true,
                        "deterministicRuleOrder", "priority,id")),
                Map.entry("limits", Map.of(
                        "maximumElements", 100000,
                        "maximumConditionalStyles", 64,
                        "maximumStyleProperties", 128))));
        } catch (Exception exception) {
            throw new TechnologyCompilationException("Could not serialize visual full-stack manifest", exception);
        }
    }

    /** Descripción: Ejecuta la operación «typescriptBridgeSource» correspondiente a la responsabilidad de TechnologyAdapterVisualFullStackSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String typescriptBridgeSource(TechnologyCompilationRequest request) {
        String json = tsTemplate(visualDefinitionJson(request));
        return """
            export const HYDRA_VISUAL_DEFINITION=JSON.parse(`%s`);
            export const HYDRA_VISUAL_LAYERS={validation:%s,quickFixes:%s,operations:%s,events:%s,eventBindings:%s,eventFlows:%s,gamification:%s,gamificationBindings:%s} as const;
            export interface HydraVisualLimits{maximumElements:number;maximumConditionalStyles:number;maximumStyleProperties:number;}
            export const HYDRA_VISUAL_DEFAULT_LIMITS:Readonly<HydraVisualLimits>={maximumElements:100000,maximumConditionalStyles:64,maximumStyleProperties:128};
            const values=(v:any):any[]=>Array.isArray(v)?v:Object.values(v||{});
            const numberIn=(v:unknown,fallback:number,min:number,max:number)=>{const n=Number.parseInt(String(v??''),10);return Number.isFinite(n)?Math.max(min,Math.min(max,n)):fallback;};
            const cleanStyles=(source:any,maximum:number)=>Object.fromEntries(Object.entries(source||{}).sort(([a],[b])=>a.localeCompare(b)).filter(([key])=>{const name=key.trim();return name.length>0&&name.length<=128&&!['__proto__','prototype','constructor'].includes(name.toLowerCase());}).slice(0,maximum).map(([key,value])=>[key,String(value??'').replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g,'').slice(0,4096)]));
            export interface HydraVisualHost{applyVisualDefinition?(definition:any):void;evaluateExpression?(expressionId:string,subject:any,context:Record<string,unknown>):unknown;applyNodeAppearance?(element:any,mapping:any,appearance:HydraVisualAppearance):void;applyEdgeAppearance?(relation:any,mapping:any,appearance:HydraVisualAppearance):void;reportVisualDiagnostic?(diagnostic:Record<string,unknown>):void;validate?(model:any):any;applyQuickFix?(model:any,diagnosticId:string,actionId:string,parameters?:Record<string,unknown>):any;invokeOperation?(operationId:string,elementId:string,args?:unknown[]):any;publishEvent?(type:string,elementId:string,payload:Record<string,unknown>):any;runEventFlow?(type:string,payload:Record<string,unknown>):any;gamificationState?(actorId:string,event:any):any;}
            export interface HydraVisualAppearance{styles:Readonly<Record<string,string>>;appliedRuleIds:readonly string[];fallbackUsed:boolean;shape?:string;imageAssetPath?:string;imageFit?:string;lineType?:string;}
            export interface HydraVisualOptions{limits?:Partial<HydraVisualLimits>;nodeMappingFor?(element:any,mappings:any[],representationId?:string):any;edgeMappingFor?(relation:any,mappings:any[],representationId?:string):any;}
            export function createHydraVisualFullStack(host:HydraVisualHost,options:HydraVisualOptions={}){
              host.applyVisualDefinition?.(HYDRA_VISUAL_DEFINITION);
              const configured=options.limits||{};const limits={maximumElements:numberIn(configured.maximumElements,100000,1,1000000),maximumConditionalStyles:numberIn(configured.maximumConditionalStyles,64,1,256),maximumStyleProperties:numberIn(configured.maximumStyleProperties,128,1,512)} as const;
              const reps=()=>values(HYDRA_VISUAL_DEFINITION.representationsById||HYDRA_VISUAL_DEFINITION.representations);
              const representation=(id?:string)=>reps().find((r:any)=>r?.id===id)||reps()[0]||null;
              const nodeMappings=(id?:string)=>{const r:any=representation(id);if(!r)return[];const grouped=r.nodeMappingsByClassId?values(r.nodeMappingsByClassId).flatMap(values):[];return grouped.length?grouped:values(r.nodeMappings);};
              const edgeMappings=(id?:string)=>{const r:any=representation(id);if(!r)return[];const grouped=[...values(r.edgeMappingsByRelationId).flatMap(values),...values(r.edgeMappingsBySemanticClassId).flatMap(values)];const source=grouped.length?grouped:values(r.edgeMappings);const seen=new Set<string>();return source.filter((item:any)=>{const key=String(item?.id||JSON.stringify(item||{}));if(seen.has(key))return false;seen.add(key);return true;});};
              const resolveStyles=(mapping:any,subject:any,context:Record<string,unknown>={}):HydraVisualAppearance=>{const resolved:Record<string,string>=cleanStyles(mapping?.styles,limits.maximumStyleProperties);const appliedRuleIds:string[]=[];let fallbackUsed=false;const rules=values(mapping?.conditionalStyles).filter((rule:any)=>rule&&rule.enabled!==false&&String(rule.conditionExpressionId||'').trim()).sort((a:any,b:any)=>Number(a.priority||0)-Number(b.priority||0)||String(a.id||'').localeCompare(String(b.id||''))).slice(0,limits.maximumConditionalStyles);for(const rule of rules){let matched=false;try{matched=Boolean(host.evaluateExpression?.(String(rule.conditionExpressionId),subject,context));}catch(error){fallbackUsed=true;host.reportVisualDiagnostic?.({code:'VISUAL_CONDITION_EVALUATION_FAILED',mappingId:String(mapping?.id||''),ruleId:String(rule.id||''),expressionId:String(rule.conditionExpressionId||''),message:error instanceof Error?error.message:String(error)});continue;}if(!matched)continue;for(const [key,value] of Object.entries(cleanStyles(rule.styles,limits.maximumStyleProperties))){if(!(key in resolved)&&Object.keys(resolved).length>=limits.maximumStyleProperties)break;resolved[key]=value;}appliedRuleIds.push(String(rule.id||''));}return {styles:Object.freeze(resolved),appliedRuleIds:Object.freeze(appliedRuleIds),fallbackUsed};};
              const resolveNodeAppearance=(mapping:any,subject:any,context:Record<string,unknown>={}):HydraVisualAppearance=>{const result=resolveStyles(mapping,subject,context);return {...result,shape:result.styles.shape||mapping?.shape||'ROUNDED_RECTANGLE',imageAssetPath:result.styles.imageAssetPath||mapping?.imageAssetPath||'',imageFit:result.styles.imageFit||mapping?.imageFit||'CONTAIN'};};
              const resolveEdgeAppearance=(mapping:any,subject:any,context:Record<string,unknown>={}):HydraVisualAppearance=>{const result=resolveStyles(mapping,subject,context);return {...result,lineType:result.styles.routeStyle||result.styles.lineType||mapping?.lineType||'SMOOTH_STEP'};};
              const applyModel=(model:any,representationId?:string)=>{const elements=values(model?.elements),relations=values(model?.relations),boundedElements=elements.slice(0,limits.maximumElements),boundedRelations=relations.slice(0,limits.maximumElements),availableNodes=nodeMappings(representationId),availableEdges=edgeMappings(representationId),byId=new Map(boundedElements.map((item:any)=>[item?.id,item])),nodeByClass=new Map<string,any>(),edgeByRelation=new Map<string,any>();for(const mapping of availableNodes){if(mapping?.targetClassId&&!nodeByClass.has(String(mapping.targetClassId)))nodeByClass.set(String(mapping.targetClassId),mapping);}for(const mapping of availableEdges){if(!mapping?.semanticEdge&&mapping?.relationId&&!edgeByRelation.has(String(mapping.relationId)))edgeByRelation.set(String(mapping.relationId),mapping);}let nodesApplied=0,edgesApplied=0;for(const element of boundedElements){const mapping=nodeByClass.get(String(element?.classId))||options.nodeMappingFor?.(element,availableNodes,representationId);if(!mapping)continue;const appearance=resolveNodeAppearance(mapping,element,{model,representationId,kind:'NODE'});host.applyNodeAppearance?.(element,mapping,appearance);nodesApplied++;}for(const relation of boundedRelations){const mapping=edgeByRelation.get(String(relation?.relationId))||options.edgeMappingFor?.(relation,availableEdges,representationId);if(!mapping)continue;const source=byId.get(relation?.sourceElementId)||null,target=byId.get(relation?.targetElementId)||null;const appearance=resolveEdgeAppearance(mapping,source,{model,relation,target,representationId,kind:'EDGE'});host.applyEdgeAppearance?.(relation,mapping,appearance);edgesApplied++;}const truncated=elements.length>limits.maximumElements||relations.length>limits.maximumElements;if(truncated)host.reportVisualDiagnostic?.({code:'VISUAL_MODEL_LIMIT_REACHED',maximumElements:limits.maximumElements,elements:elements.length,relations:relations.length});return {nodesApplied,edgesApplied,truncated,maximumElements:limits.maximumElements};};
              return {definition:HYDRA_VISUAL_DEFINITION,layers:HYDRA_VISUAL_LAYERS,limits,representations:reps,representation,nodeMappings,edgeMappings,resolveStyles,resolveNodeAppearance,resolveEdgeAppearance,applyModel,
                validate:(model:any)=>HYDRA_VISUAL_LAYERS.validation&&host.validate?host.validate(model):[],
                applyQuickFix:(model:any,diagnosticId:string,actionId:string,parameters:Record<string,unknown>={})=>{if(!HYDRA_VISUAL_LAYERS.quickFixes||!host.applyQuickFix)throw new Error('HYDRA quick fixes are not available in this package');return host.applyQuickFix(model,diagnosticId,actionId,parameters);},
                invokeOperation:(operationId:string,elementId:string,args:unknown[]=[])=>HYDRA_VISUAL_LAYERS.operations&&host.invokeOperation?host.invokeOperation(operationId,elementId,args):undefined,
                afterCommit:async(type:string,elementId:string,payload:Record<string,unknown>,actorId='local')=>{if(HYDRA_VISUAL_LAYERS.events&&host.publishEvent)await host.publishEvent(type,elementId,payload);const flow=HYDRA_VISUAL_LAYERS.eventFlows&&host.runEventFlow?await host.runEventFlow(type,payload):undefined;const gamification=HYDRA_VISUAL_LAYERS.gamification&&host.gamificationState?await host.gamificationState(actorId,{type,elementId,payload,flow}):undefined;return {flow,gamification};}
              } as const;
            }
            """.formatted(json,
                has(request, IntegrationArtifactKind.VALIDATIONS), has(request, IntegrationArtifactKind.ACTIONS),
                has(request, IntegrationArtifactKind.OPERATION_BEHAVIORS), has(request, IntegrationArtifactKind.EVENT_DEFINITIONS),
                has(request, IntegrationArtifactKind.EVENT_BINDINGS), has(request, IntegrationArtifactKind.EVENT_FLOWS),
                has(request, IntegrationArtifactKind.GAMIFICATION_DEFINITIONS), has(request, IntegrationArtifactKind.GAMIFICATION_BINDINGS));
    }

    /** Descripción: Ejecuta la operación «pythonBridgeSource» correspondiente a la responsabilidad de TechnologyAdapterVisualFullStackSupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String pythonBridgeSource(TechnologyCompilationRequest request) {
        String json = visualDefinitionJson(request).replace("'''", "\\u0027\\u0027\\u0027");
        return """
            import json
            VISUAL_DEFINITION=json.loads(r'''%s''')
            LAYERS={'validation':%s,'quick_fixes':%s,'operations':%s,'events':%s,'event_bindings':%s,'event_flows':%s,'gamification':%s,'gamification_bindings':%s}
            DEFAULT_LIMITS={'maximum_elements':100000,'maximum_conditional_styles':64,'maximum_style_properties':128}
            def _values(v): return v if isinstance(v,list) else list((v or {}).values())
            def _bounded_int(value,fallback,minimum,maximum):
                try: return max(minimum,min(maximum,int(value)))
                except (TypeError,ValueError): return fallback
            def _styles(source,maximum):
                result={}
                for key,value in sorted((source or {}).items(),key=lambda item:str(item[0]))[:maximum]:
                    name=str(key).strip()
                    if name and len(name)<=128 and name.lower() not in {'__proto__','prototype','constructor'}: result[name]=''.join(ch for ch in str(value if value is not None else '') if ord(ch)>=32 or ch in '\\t\\n\\r')[:4096]
                return result
            class HydraVisualFullStack:
                def __init__(self,host,limits=None):
                    self.host=host; limits=limits or {}
                    self.limits={'maximum_elements':_bounded_int(limits.get('maximum_elements'),100000,1,1000000),'maximum_conditional_styles':_bounded_int(limits.get('maximum_conditional_styles'),64,1,256),'maximum_style_properties':_bounded_int(limits.get('maximum_style_properties'),128,1,512)}
                    fn=getattr(host,'apply_visual_definition',None)
                    if fn: fn(VISUAL_DEFINITION)
                def representations(self): return _values(VISUAL_DEFINITION.get('representationsById') or VISUAL_DEFINITION.get('representations'))
                def representation(self,rep_id=None):
                    reps=self.representations(); return next((r for r in reps if r.get('id')==rep_id),reps[0] if reps else None)
                def node_mappings(self,rep_id=None):
                    rep=self.representation(rep_id) or {}; grouped=[]
                    for group in _values(rep.get('nodeMappingsByClassId')): grouped.extend(_values(group))
                    return grouped or _values(rep.get('nodeMappings'))
                def edge_mappings(self,rep_id=None):
                    rep=self.representation(rep_id) or {}; grouped=[]
                    for collection in (rep.get('edgeMappingsByRelationId'),rep.get('edgeMappingsBySemanticClassId')):
                        for group in _values(collection): grouped.extend(_values(group))
                    source=grouped or _values(rep.get('edgeMappings')); result=[]; seen=set()
                    for item in source:
                        key=str(item.get('id') or json.dumps(item,sort_keys=True))
                        if key not in seen: seen.add(key); result.append(item)
                    return result
                def resolve_styles(self,mapping,subject=None,context=None):
                    maximum=self.limits['maximum_style_properties']; resolved=_styles((mapping or {}).get('styles'),maximum); applied=[]; fallback=False
                    rules=sorted([r for r in _values((mapping or {}).get('conditionalStyles')) if r and r.get('enabled',True) is not False and str(r.get('conditionExpressionId') or '').strip()],key=lambda r:(int(r.get('priority') or 0),str(r.get('id') or '')))[:self.limits['maximum_conditional_styles']]
                    evaluator=getattr(self.host,'evaluate_expression',None)
                    for rule in rules:
                        try: matched=bool(evaluator(rule.get('conditionExpressionId'),subject,context or {})) if evaluator else False
                        except Exception as error:
                            fallback=True; reporter=getattr(self.host,'report_visual_diagnostic',None)
                            if reporter: reporter({'code':'VISUAL_CONDITION_EVALUATION_FAILED','mappingId':str((mapping or {}).get('id') or ''),'ruleId':str(rule.get('id') or ''),'expressionId':str(rule.get('conditionExpressionId') or ''),'message':str(error)})
                            continue
                        if not matched: continue
                        for key,value in _styles(rule.get('styles'),maximum).items():
                            if key not in resolved and len(resolved)>=maximum: break
                            resolved[key]=value
                        applied.append(str(rule.get('id') or ''))
                    return {'styles':resolved,'appliedRuleIds':applied,'fallbackUsed':fallback}
                def resolve_node_appearance(self,mapping,subject=None,context=None):
                    result=self.resolve_styles(mapping,subject,context); styles=result['styles']; return {**result,'shape':styles.get('shape') or (mapping or {}).get('shape') or 'ROUNDED_RECTANGLE','imageAssetPath':styles.get('imageAssetPath') or (mapping or {}).get('imageAssetPath') or '','imageFit':styles.get('imageFit') or (mapping or {}).get('imageFit') or 'CONTAIN'}
                def resolve_edge_appearance(self,mapping,subject=None,context=None):
                    result=self.resolve_styles(mapping,subject,context); styles=result['styles']; return {**result,'lineType':styles.get('routeStyle') or styles.get('lineType') or (mapping or {}).get('lineType') or 'SMOOTH_STEP'}
                def apply_model(self,model,rep_id=None,context=None):
                    context=context or {}; maximum=self.limits['maximum_elements']; model=model or {}
                    by_class={}
                    for mapping in self.node_mappings(rep_id):
                        key=str(mapping.get('targetClassId') or mapping.get('eClass') or '')
                        if key and key not in by_class: by_class[key]=mapping
                    by_relation={}
                    for mapping in self.edge_mappings(rep_id):
                        key=str(mapping.get('relationId') or mapping.get('reference') or '')
                        if key and key not in by_relation: by_relation[key]=mapping
                    node_fn=getattr(self.host,'apply_node_appearance',None); edge_fn=getattr(self.host,'apply_edge_appearance',None)
                    nodes=0; edges=0
                    for element in _values(model.get('elements'))[:maximum]:
                        mapping=by_class.get(str(element.get('classId') or element.get('eClass') or ''))
                        if mapping:
                            appearance=self.resolve_node_appearance(mapping,element,context)
                            if node_fn: node_fn(str(element.get('id') or ''),appearance)
                            nodes+=1
                    for relation in _values(model.get('relations'))[:maximum]:
                        mapping=by_relation.get(str(relation.get('relationId') or relation.get('reference') or ''))
                        if mapping:
                            appearance=self.resolve_edge_appearance(mapping,relation,context)
                            if edge_fn: edge_fn(str(relation.get('id') or ''),appearance)
                            edges+=1
                    return {'nodes':nodes,'edges':edges,'truncated':len(_values(model.get('elements')))>maximum or len(_values(model.get('relations')))>maximum}
                def validate(self,model):
                    fn=getattr(self.host,'validate',None); return fn(model) if LAYERS['validation'] and fn else []
                def apply_quick_fix(self,model,diagnostic_id,action_id,parameters=None):
                    fn=getattr(self.host,'apply_quick_fix',None)
                    if not LAYERS['quick_fixes'] or not fn: raise RuntimeError('HYDRA quick fixes are not available in this package')
                    return fn(model,diagnostic_id,action_id,parameters or {})
                def after_commit(self,event_type,element_id,payload,actor_id='local'):
                    publish=getattr(self.host,'publish_event',None)
                    if LAYERS['events'] and publish: publish(event_type,element_id,payload)
                    flow_fn=getattr(self.host,'run_event_flow',None); flow=flow_fn(event_type,payload) if LAYERS['event_flows'] and flow_fn else None
                    game_fn=getattr(self.host,'gamification_state',None); game=game_fn(actor_id,{'type':event_type,'elementId':element_id,'payload':payload,'flow':flow}) if LAYERS['gamification'] and game_fn else None
                    return {'flow':flow,'gamification':game}
            """.formatted(json,
                py(has(request, IntegrationArtifactKind.VALIDATIONS)), py(has(request, IntegrationArtifactKind.ACTIONS)),
                py(has(request, IntegrationArtifactKind.OPERATION_BEHAVIORS)), py(has(request, IntegrationArtifactKind.EVENT_DEFINITIONS)),
                py(has(request, IntegrationArtifactKind.EVENT_BINDINGS)), py(has(request, IntegrationArtifactKind.EVENT_FLOWS)),
                py(has(request, IntegrationArtifactKind.GAMIFICATION_DEFINITIONS)), py(has(request, IntegrationArtifactKind.GAMIFICATION_BINDINGS)));
    }

    /** Descripción: Ejecuta la operación «javaBridgeSource» correspondiente a la responsabilidad de TechnologyAdapterVisualFullStackSupport. Entradas: packageName: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String javaBridgeSource(String packageName, TechnologyCompilationRequest request) {
        String json = javaEscape(visualDefinitionJson(request));
        return """
            package %s;
            import java.util.ArrayList;
            import java.util.Comparator;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Map;
            public final class HydraVisualFullStack {
              public static final String VISUAL_DEFINITION_JSON="%s";
              public static final boolean VALIDATION=%s,QUICK_FIXES=%s,OPERATIONS=%s,EVENTS=%s,EVENT_BINDINGS=%s,EVENT_FLOWS=%s,GAMIFICATION=%s,GAMIFICATION_BINDINGS=%s;
              public static final int MAXIMUM_ELEMENTS=100000,MAXIMUM_CONDITIONAL_STYLES=64,MAXIMUM_STYLE_PROPERTIES=128;
              public interface Host {default void applyVisualDefinition(String json){} default boolean evaluateExpression(String expressionId,Object subject,Map<String,Object> context){return false;} default void reportVisualDiagnostic(String code,String mappingId,String ruleId,String message){} default void applyNodeAppearance(String elementId,NodeAppearance appearance){} default void applyEdgeAppearance(String relationId,EdgeAppearance appearance){} default Object validate(Object model){return java.util.List.of();} default Object applyQuickFix(Object model,String diagnosticId,String actionId,Map<String,Object> parameters){throw new UnsupportedOperationException();} default Object invokeOperation(String operationId,String elementId,Object[] args){return null;} default void publishEvent(String type,String elementId,Map<String,Object> payload){} default Object runEventFlow(String type,Map<String,Object> payload){return null;} default Object gamificationState(String actorId,Object event){return null;}}
              public record ConditionalStyle(String id,String conditionExpressionId,int priority,boolean enabled,Map<String,String> styles){}
              public record Appearance(Map<String,String> styles,List<String> appliedRuleIds,boolean fallbackUsed){}
              public record NodeAppearance(Map<String,String> styles,List<String> appliedRuleIds,boolean fallbackUsed,String shape,String imageAssetPath,String imageFit){}
              public record EdgeAppearance(Map<String,String> styles,List<String> appliedRuleIds,boolean fallbackUsed,String lineType){}
              private final Host host;
              public HydraVisualFullStack(Host host){this.host=host;host.applyVisualDefinition(VISUAL_DEFINITION_JSON);}
              private static boolean safeStyleKey(String key){return key!=null&&!key.isBlank()&&key.length()<=128&&!key.equalsIgnoreCase("__proto__")&&!key.equalsIgnoreCase("prototype")&&!key.equalsIgnoreCase("constructor");} private static String safeStyleValue(String value){if(value==null)return "";StringBuilder out=new StringBuilder();value.codePoints().filter(cp->cp>=32||cp==9||cp==10||cp==13).limit(4096).forEach(out::appendCodePoint);return out.toString();} public Appearance resolveStyles(String mappingId,Map<String,String> base,List<ConditionalStyle> rules,Object subject,Map<String,Object> context){Map<String,String> resolved=new LinkedHashMap<>();if(base!=null)base.entrySet().stream().filter(e->safeStyleKey(e.getKey())).sorted(Map.Entry.comparingByKey()).limit(MAXIMUM_STYLE_PROPERTIES).forEach(e->resolved.put(e.getKey(),safeStyleValue(e.getValue())));List<ConditionalStyle> ordered=rules==null?List.of():rules.stream().filter(ConditionalStyle::enabled).sorted(Comparator.comparingInt(ConditionalStyle::priority).thenComparing(ConditionalStyle::id)).limit(MAXIMUM_CONDITIONAL_STYLES).toList();List<String> applied=new ArrayList<>();boolean fallback=false;for(ConditionalStyle rule:ordered){boolean matched=false;try{matched=host.evaluateExpression(rule.conditionExpressionId(),subject,context==null?Map.of():context);}catch(RuntimeException exception){fallback=true;host.reportVisualDiagnostic("VISUAL_CONDITION_EVALUATION_FAILED",mappingId,rule.id(),exception.getMessage());}if(!matched)continue;if(rule.styles()!=null)for(var entry:rule.styles().entrySet()){if(!safeStyleKey(entry.getKey()))continue;if(!resolved.containsKey(entry.getKey())&&resolved.size()>=MAXIMUM_STYLE_PROPERTIES)break;resolved.put(entry.getKey(),safeStyleValue(entry.getValue()));}applied.add(rule.id());}return new Appearance(Map.copyOf(resolved),List.copyOf(applied),fallback);}
              public NodeAppearance resolveNodeAppearance(String mappingId,String fallbackShape,String fallbackImageAssetPath,String fallbackImageFit,Map<String,String> base,List<ConditionalStyle> rules,Object subject,Map<String,Object> context){Appearance a=resolveStyles(mappingId,base,rules,subject,context);String shape=a.styles().getOrDefault("shape",fallbackShape==null?"ROUNDED_RECTANGLE":fallbackShape);String asset=a.styles().getOrDefault("imageAssetPath",fallbackImageAssetPath==null?"":fallbackImageAssetPath);String fit=a.styles().getOrDefault("imageFit",fallbackImageFit==null?"CONTAIN":fallbackImageFit);return new NodeAppearance(a.styles(),a.appliedRuleIds(),a.fallbackUsed(),shape,asset,fit);}
              public EdgeAppearance resolveEdgeAppearance(String mappingId,String fallbackLineType,Map<String,String> base,List<ConditionalStyle> rules,Object subject,Map<String,Object> context){Appearance a=resolveStyles(mappingId,base,rules,subject,context);String line=a.styles().getOrDefault("routeStyle",a.styles().getOrDefault("lineType",fallbackLineType==null?"SMOOTH_STEP":fallbackLineType));return new EdgeAppearance(a.styles(),a.appliedRuleIds(),a.fallbackUsed(),line);}
              public NodeAppearance applyNode(String elementId,String mappingId,String fallbackShape,String fallbackImageAssetPath,String fallbackImageFit,Map<String,String> base,List<ConditionalStyle> rules,Object subject,Map<String,Object> context){NodeAppearance a=resolveNodeAppearance(mappingId,fallbackShape,fallbackImageAssetPath,fallbackImageFit,base,rules,subject,context);host.applyNodeAppearance(elementId,a);return a;}
              public EdgeAppearance applyEdge(String relationId,String mappingId,String fallbackLineType,Map<String,String> base,List<ConditionalStyle> rules,Object subject,Map<String,Object> context){EdgeAppearance a=resolveEdgeAppearance(mappingId,fallbackLineType,base,rules,subject,context);host.applyEdgeAppearance(relationId,a);return a;}
              public Object validate(Object model){return VALIDATION?host.validate(model):java.util.List.of();}
              public Object applyQuickFix(Object model,String diagnosticId,String actionId,Map<String,Object> parameters){if(!QUICK_FIXES)throw new IllegalStateException("HYDRA quick fixes are not available in this package");return host.applyQuickFix(model,diagnosticId,actionId,parameters);}
              public Result afterCommit(String type,String elementId,Map<String,Object> payload,String actorId){if(EVENTS)host.publishEvent(type,elementId,payload);Object flow=EVENT_FLOWS?host.runEventFlow(type,payload):null;Object game=GAMIFICATION?host.gamificationState(actorId,Map.of("type",type,"elementId",elementId,"payload",payload,"flow",flow==null?"":flow)):null;return new Result(flow,game);} public record Result(Object eventFlow,Object gamification){}
            }
            """.formatted(packageName,json,
                has(request, IntegrationArtifactKind.VALIDATIONS), has(request, IntegrationArtifactKind.ACTIONS),
                has(request, IntegrationArtifactKind.OPERATION_BEHAVIORS), has(request, IntegrationArtifactKind.EVENT_DEFINITIONS),
                has(request, IntegrationArtifactKind.EVENT_BINDINGS), has(request, IntegrationArtifactKind.EVENT_FLOWS),
                has(request, IntegrationArtifactKind.GAMIFICATION_DEFINITIONS), has(request, IntegrationArtifactKind.GAMIFICATION_BINDINGS));
    }

    /** Descripción: Ejecuta la operación «neutralFiles» correspondiente a la responsabilidad de TechnologyAdapterVisualFullStackSupport. Entradas: root: ruta o ubicación utilizada por la operación; request: solicitud con los datos necesarios para ejecutar la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static List<TechnologyGeneratedFile> neutralFiles(String root, TechnologyCompilationRequest request, JsonMapper mapper) {
        if(!available(request)) return List.of();
        List<String> sourceIds=TechnologyAdapterGenerationSupport.sourceIds(request);
        List<TechnologyGeneratedFile> files=new ArrayList<>();
        files.add(TechnologyAdapterSupport.text(root+"/hydra/visual-editor-definition.json","application/json",visualDefinitionJson(request),sourceIds));
        files.add(TechnologyAdapterSupport.text(root+"/hydra/visual-full-stack.json","application/json",manifest(request,mapper),sourceIds));
        return files;
    }

    /** Descripción: Evalúa la condición «has» sin modificar el estado externo. Entradas: r: dato de entrada necesario para la operación; k: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean has(TechnologyCompilationRequest r, IntegrationArtifactKind k){return r.find(k).isPresent();}
    /** Descripción: Ejecuta la operación «py» correspondiente a la responsabilidad de TechnologyAdapterVisualFullStackSupport. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String py(boolean v){return v?"True":"False";}
    /** Descripción: Ejecuta la operación «javaEscape» correspondiente a la responsabilidad de TechnologyAdapterVisualFullStackSupport. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String javaEscape(String v){return v==null?"":v.replace("\\","\\\\").replace("\"","\\\"").replace("\r"," ").replace("\n","\\n");}
    /** Descripción: Ejecuta la operación «tsTemplate» correspondiente a la responsabilidad de TechnologyAdapterVisualFullStackSupport. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String tsTemplate(String v){return v.replace("\\","\\\\").replace("`","\\`").replace("${","\\${");}
}
