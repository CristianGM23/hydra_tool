(function(root,factory){
  'use strict';
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  if(root)root.HydraVisualAppearanceRuntime=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';
  const DEFAULT_LIMITS=Object.freeze({maximumElements:100000,maximumConditionalStyles:64,maximumStyleProperties:128});
  const values=value=>Array.isArray(value)?value:Object.values(value||{});
  const int=(value,fallback,min,max)=>{const parsed=Number.parseInt(String(value??''),10);return Number.isFinite(parsed)?Math.max(min,Math.min(max,parsed)):fallback;};
  const text=value=>value==null?'':String(value);
  const FORBIDDEN_STYLE_KEYS=new Set(['__proto__','prototype','constructor']);
  const safeStyles=(source,maximum)=>{const result=Object.create(null);for(const [key,value] of Object.entries(source||{}).sort(([a],[b])=>a.localeCompare(b))){const name=text(key).trim();if(!name||name.length>128||FORBIDDEN_STYLE_KEYS.has(name.toLowerCase()))continue;if(Object.keys(result).length>=maximum)break;const normalized=text(value).replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g,'').slice(0,4096);result[name]=normalized;}return result;};
  const rules=(mapping,maximum)=>values(mapping?.conditionalStyles).filter(rule=>rule&&rule.enabled!==false&&text(rule.conditionExpressionId).trim()).sort((left,right)=>Number(left.priority||0)-Number(right.priority||0)||text(left.id).localeCompare(text(right.id))).slice(0,maximum);
  function create(definition,options={}){
    const configured=options.limits||{};
    const limits=Object.freeze({
      maximumElements:int(configured.maximumElements,DEFAULT_LIMITS.maximumElements,1,1000000),
      maximumConditionalStyles:int(configured.maximumConditionalStyles,DEFAULT_LIMITS.maximumConditionalStyles,1,256),
      maximumStyleProperties:int(configured.maximumStyleProperties,DEFAULT_LIMITS.maximumStyleProperties,1,512)
    });
    const evaluate=typeof options.evaluateExpression==='function'?options.evaluateExpression:()=>false;
    const report=typeof options.report==='function'?options.report:()=>{};
    const representations=()=>values(definition?.representationsById||definition?.representations);
    const representation=id=>representations().find(item=>item?.id===id)||representations()[0]||null;
    const nodeMappings=id=>{const current=representation(id);if(!current)return[];const grouped=values(current.nodeMappingsByClassId).flatMap(values);return grouped.length?grouped:values(current.nodeMappings);};
    const edgeMappings=id=>{const current=representation(id);if(!current)return[];const grouped=[...values(current.edgeMappingsByRelationId).flatMap(values),...values(current.edgeMappingsBySemanticClassId).flatMap(values)];const source=grouped.length?grouped:values(current.edgeMappings);const seen=new Set();return source.filter(item=>{const key=text(item?.id)||JSON.stringify(item||{});if(seen.has(key))return false;seen.add(key);return true;});};
    function resolveStyles(mapping,subject,context={}){
      const resolved=safeStyles(mapping?.styles,limits.maximumStyleProperties);
      const appliedRuleIds=[];let fallbackUsed=false;
      for(const rule of rules(mapping,limits.maximumConditionalStyles)){
        let matched=false;
        try{matched=Boolean(evaluate(rule.conditionExpressionId,subject,context));}
        catch(error){fallbackUsed=true;report({code:'VISUAL_CONDITION_EVALUATION_FAILED',mappingId:text(mapping?.id),ruleId:text(rule.id),expressionId:text(rule.conditionExpressionId),message:text(error?.message||error)});continue;}
        if(!matched)continue;
        const overrides=safeStyles(rule.styles,limits.maximumStyleProperties);
        for(const [key,value] of Object.entries(overrides)){if(!Object.prototype.hasOwnProperty.call(resolved,key)&&Object.keys(resolved).length>=limits.maximumStyleProperties)break;resolved[key]=value;}
        appliedRuleIds.push(text(rule.id));
      }
      return Object.freeze({styles:Object.freeze(resolved),appliedRuleIds:Object.freeze(appliedRuleIds),fallbackUsed});
    }
    function resolveNodeAppearance(mapping,subject,context={}){
      const result=resolveStyles(mapping,subject,context),styles=result.styles;
      return Object.freeze({...result,shape:styles.shape||mapping?.shape||'ROUNDED_RECTANGLE',imageAssetPath:styles.imageAssetPath||mapping?.imageAssetPath||'',imageFit:styles.imageFit||mapping?.imageFit||'CONTAIN'});
    }
    function resolveEdgeAppearance(mapping,subject,context={}){
      const result=resolveStyles(mapping,subject,context),styles=result.styles;
      return Object.freeze({...result,lineType:styles.routeStyle||styles.lineType||mapping?.lineType||'SMOOTH_STEP'});
    }
    function applyModel(model,representationId,host={}){
      const elements=values(model?.elements),relations=values(model?.relations);const truncated=elements.length>limits.maximumElements||relations.length>limits.maximumElements;
      const boundedElements=elements.slice(0,limits.maximumElements),boundedRelations=relations.slice(0,limits.maximumElements),availableNodes=nodeMappings(representationId),availableEdges=edgeMappings(representationId);
      const elementById=new Map(boundedElements.map(item=>[item?.id,item])),nodeByClass=new Map(),edgeByRelation=new Map();for(const mapping of availableNodes){if(mapping?.targetClassId&&!nodeByClass.has(mapping.targetClassId))nodeByClass.set(mapping.targetClassId,mapping);}for(const mapping of availableEdges){if(!mapping?.semanticEdge&&mapping?.relationId&&!edgeByRelation.has(mapping.relationId))edgeByRelation.set(mapping.relationId,mapping);}let nodesApplied=0,edgesApplied=0;
      for(const element of boundedElements){
        const mapping=nodeByClass.get(element?.classId)||(typeof options.nodeMappingFor==='function'?options.nodeMappingFor(element,availableNodes,representationId):null);
        if(!mapping)continue;const appearance=resolveNodeAppearance(mapping,element,{model,representationId,kind:'NODE'});host.applyNodeAppearance?.(element,mapping,appearance);nodesApplied++;
      }
      for(const relation of boundedRelations){
        const source=elementById.get(relation?.sourceElementId)||null,target=elementById.get(relation?.targetElementId)||null;
        const mapping=edgeByRelation.get(relation?.relationId)||(typeof options.edgeMappingFor==='function'?options.edgeMappingFor(relation,availableEdges,representationId):null);
        if(!mapping)continue;const appearance=resolveEdgeAppearance(mapping,source,{model,relation,target,representationId,kind:'EDGE'});host.applyEdgeAppearance?.(relation,mapping,appearance);edgesApplied++;
      }
      if(truncated)report({code:'VISUAL_MODEL_LIMIT_REACHED',maximumElements:limits.maximumElements,elements:elements.length,relations:relations.length});
      return Object.freeze({nodesApplied,edgesApplied,truncated,maximumElements:limits.maximumElements});
    }
    return Object.freeze({definition,limits,representations,representation,nodeMappings,edgeMappings,resolveStyles,resolveNodeAppearance,resolveEdgeAppearance,applyModel});
  }
  return Object.freeze({DEFAULT_LIMITS,create});
});
