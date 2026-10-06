import fs from 'node:fs';import vm from 'node:vm';
const source=fs.readFileSync(new URL('../../src/main/resources/standalone-template/frontend/runtime.js',import.meta.url),'utf8');
const window={localStorage:{getItem:()=>null,setItem:()=>{}}};vm.runInNewContext(source,{window,console,Date,Math,JSON,Set,Map,Object,Array,String,Number,Boolean,RegExp});
const model={id:'m',elements:[{id:'e',classId:'C',attributes:{},annotations:{}}],relations:[],rootElementIds:['e'],annotations:{}};const metamodel={id:'mm',classes:[{id:'C'}],relations:[]};
// 1) partially missing layers must degrade without exceptions.
let runtime=window.HydraGeneratedRuntime.create({metamodel,documents:{},getModel:()=>model});
if(runtime.validate().length!==0)throw new Error('empty runtime validation should be empty');if(runtime.dispatch('x')?.eventTypeId!=='x')throw new Error('event dispatch should still work');
// 2) cyclic event bindings are bounded by processingDepth and recentEvents cap.
const bindings=[];for(let i=0;i<64;i++)bindings.push({id:`b${i}`,enabled:true,sourceEventTypeId:`e${i}`,targetEventTypeId:`e${(i+1)%64}`,payloadMappings:[]});
runtime=window.HydraGeneratedRuntime.create({metamodel,documents:{EVENT_BINDINGS:[{bindings}]},getModel:()=>model});runtime.dispatch('e0',{},{});const snap=runtime.snapshot();if(snap.recentEvents.length>30||snap.recentEvents.length<2)throw new Error(`cyclic binding bound failed ${snap.recentEvents.length}`);
// 3) deep EventFlow with a cycle cannot recurse forever.
const nodes=Array.from({length:80},(_,i)=>({id:`n${i}`,kind:i===0?'START':'ACTION_STEP',enabled:true}));const transitions=Array.from({length:79},(_,i)=>({sourceNodeId:`n${i}`,targetNodeId:`n${i+1}`,order:0}));transitions.push({sourceNodeId:'n79',targetNodeId:'n0',order:0});
runtime=window.HydraGeneratedRuntime.create({metamodel,documents:{EVENT_FLOW:[{flows:[{id:'f',enabled:true,triggerEventTypeIds:['go'],nodes,transitions}]}]},getModel:()=>model});runtime.dispatch('go');if(runtime.snapshot().recentEvents.length!==1)throw new Error('flow cycle generated unexpected events');
// 4) missing quick-fix action is explicit and non-mutating.
const before=JSON.stringify(model);const q=runtime.applyQuickFix('missing','e');if(q.changed||JSON.stringify(model)!==before)throw new Error('missing quick fix mutated model');
console.log('TEXTUAL_FULL_STACK_RESILIENCE=PASS cyclicBindings=64 deepFlowNodes=80');
