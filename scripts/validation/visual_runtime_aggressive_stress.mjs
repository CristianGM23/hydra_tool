#!/usr/bin/env node
import {createRequire} from 'node:module';
import {performance} from 'node:perf_hooks';
const require=createRequire(import.meta.url);
const runtime=require('../../src/main/resources/standalone-template/frontend/visual-runtime.js');
let checks=0;
const assert=(condition,message)=>{if(!condition)throw new Error(message);checks++;};
const nodeMapping={id:'person-node',targetClassId:'Person',shape:'RECTANGLE',styles:{fillColor:'#ffffff',strokeColor:'#111111',labelColor:'#222222'},conditionalStyles:[
  {id:'z-later',conditionExpressionId:'isVip',priority:20,enabled:true,styles:{fillColor:'#ffe000',shape:'ELLIPSE'}},
  {id:'a-earlier',conditionExpressionId:'isActive',priority:10,enabled:true,styles:{strokeWidth:'3'}},
  {id:'disabled',conditionExpressionId:'always',priority:1,enabled:false,styles:{fillColor:'#ff0000'}}
]};
const edgeMapping={id:'employer-edge',relationId:'employer',semanticEdge:false,lineType:'ORTHOGONAL',styles:{strokeColor:'#475569'},conditionalStyles:[{id:'important-edge',conditionExpressionId:'important',priority:1,enabled:true,styles:{strokeColor:'#dc2626',routeStyle:'BEZIER',dashed:'1'}}]};
const definition={representations:[{id:'main',kind:'DIAGRAM',nodeMappings:[nodeMapping],edgeMappings:[edgeMapping]}]};
const diagnostics=[];
const engine=runtime.create(definition,{evaluateExpression:(id,subject,context)=>{if(id==='boom')throw new Error('fault injected');if(id==='isVip')return subject?.vip===true;if(id==='isActive')return subject?.active===true;if(id==='important')return context?.relation?.important===true;if(id==='always')return true;return false;},report:d=>diagnostics.push(d)});
assert(engine.limits.maximumElements===100000,'default element limit');
assert(engine.limits.maximumConditionalStyles===64,'default rule limit');
let appearance=engine.resolveNodeAppearance(nodeMapping,{vip:true,active:true});
assert(appearance.styles.fillColor==='#ffe000','conditional fill applied');
assert(appearance.styles.strokeWidth==='3','lower-priority rule applied');
assert(appearance.shape==='ELLIPSE','conditional shape applied');
assert(appearance.appliedRuleIds.join(',')==='a-earlier,z-later','deterministic priority/id order');
assert(!appearance.appliedRuleIds.includes('disabled'),'disabled rule ignored');
let edge=engine.resolveEdgeAppearance(edgeMapping,{}, {relation:{important:true}});
assert(edge.styles.strokeColor==='#dc2626','conditional edge color applied');
assert(edge.lineType==='BEZIER','conditional edge routing applied');

const faulty={...nodeMapping,conditionalStyles:[{id:'fault',conditionExpressionId:'boom',priority:0,enabled:true,styles:{fillColor:'#000000'}}]};
appearance=engine.resolveNodeAppearance(faulty,{vip:true});
assert(appearance.styles.fillColor==='#ffffff','fault preserves base style');
assert(appearance.fallbackUsed===true,'fault marked fallback');
assert(diagnostics.some(d=>d.code==='VISUAL_CONDITION_EVALUATION_FAILED'),'fault diagnostic emitted');

const manyRules=Array.from({length:100},(_,i)=>({id:`r-${String(i).padStart(3,'0')}`,conditionExpressionId:'always',priority:i,enabled:true,styles:{counter:String(i)}}));
const bounded=engine.resolveStyles({id:'bounded',styles:{counter:'base'},conditionalStyles:manyRules},{},{});
assert(bounded.appliedRuleIds.length===64,'default rule cap enforced');
assert(bounded.styles.counter==='63','rules above cap not evaluated/applied');

const limited=runtime.create(definition,{limits:{maximumElements:5000,maximumConditionalStyles:9999,maximumStyleProperties:2},evaluateExpression:()=>true,report:d=>diagnostics.push(d)});
assert(limited.limits.maximumConditionalStyles===256,'configured rule limit clamped');
assert(limited.limits.maximumStyleProperties===2,'configured style limit accepted');
const capped=limited.resolveStyles({styles:{a:'1',b:'2',c:'3'},conditionalStyles:[{id:'x',conditionExpressionId:'x',styles:{d:'4',a:'9'}}]},{});
assert(Object.keys(capped.styles).length===2,'style property cap enforced');
assert(capped.styles.a==='9','existing style can be overridden when at cap');

const hostile=JSON.parse('{"styles":{"__proto__":"polluted","constructor":"bad","Constructor":"bad2","prototype":"bad","safe":"ok","control":"a\\u0001b"}}');
const sanitized=engine.resolveStyles(hostile,{}).styles;
assert(!Object.prototype.hasOwnProperty.call(sanitized,'__proto__'),'prototype pollution key blocked');
assert(!Object.prototype.hasOwnProperty.call(sanitized,'constructor'),'constructor key blocked');
assert(!Object.prototype.hasOwnProperty.call(sanitized,'Constructor'),'mixed-case constructor key blocked');
assert(sanitized.safe==='ok','safe style retained');
assert(sanitized.control==='ab','control characters removed');
assert(({}).polluted===undefined,'Object prototype unchanged');

// Permute equal-priority rules repeatedly: the result must be stable because id is the tie-breaker.
const equal=[{id:'c',conditionExpressionId:'always',priority:5,styles:{winner:'c'}},{id:'a',conditionExpressionId:'always',priority:5,styles:{winner:'a'}},{id:'b',conditionExpressionId:'always',priority:5,styles:{winner:'b'}}];
for(let i=0;i<500;i++){
  const shuffled=[...equal].sort(()=>Math.random()-.5);
  const result=engine.resolveStyles({styles:{},conditionalStyles:shuffled},{});
  assert(result.appliedRuleIds.join(',')==='a,b,c','equal-priority rules sorted deterministically');
  assert(result.styles.winner==='c','deterministic last override wins');
}

// Large model: >100k elements and relations are supplied, but a configured 5k cap prevents unbounded work.
const elements=Array.from({length:100001},(_,i)=>({id:`n${i}`,classId:'Person',vip:(i%2)===0,active:true}));
const relations=Array.from({length:100001},(_,i)=>({id:`e${i}`,relationId:'employer',sourceElementId:`n${i%5000}`,targetElementId:`n${(i+1)%5000}`,important:(i%3)===0}));
let nodesApplied=0,edgesApplied=0;
const start=performance.now();
const applied=limited.applyModel({elements,relations},'main',{applyNodeAppearance:()=>nodesApplied++,applyEdgeAppearance:()=>edgesApplied++});
const elapsed=performance.now()-start;
assert(applied.truncated===true,'large model truncation reported');
assert(nodesApplied===5000&&edgesApplied===5000,'large model bounded exactly');
assert(diagnostics.some(d=>d.code==='VISUAL_MODEL_LIMIT_REACHED'),'large model diagnostic emitted');
assert(elapsed<5000,`bounded 100k stress budget exceeded: ${elapsed.toFixed(1)}ms`);

// Mapping cardinality stress: pre-indexing must avoid O(elements*mappings) lookup behavior.
const mappings=Array.from({length:2000},(_,i)=>({id:`m${i}`,targetClassId:`C${i}`,styles:{fillColor:'#fff'}}));
const rep2={id:'many-mappings',nodeMappings:mappings,edgeMappings:[]};
const indexed=runtime.create({representations:[rep2]},{limits:{maximumElements:20000}});
const model2={elements:Array.from({length:20000},(_,i)=>({id:`x${i}`,classId:`C${i%2000}`})),relations:[]};
let mapped=0;const start2=performance.now();const res2=indexed.applyModel(model2,'many-mappings',{applyNodeAppearance:()=>mapped++});const elapsed2=performance.now()-start2;
assert(res2.nodesApplied===20000&&mapped===20000,'2000-mapping stress resolved all nodes');
assert(elapsed2<5000,`mapping index stress budget exceeded: ${elapsed2.toFixed(1)}ms`);

console.log(`HYDRA_VISUAL_RUNTIME_AGGRESSIVE=${checks}/${checks} PASS large=${elapsed.toFixed(1)}ms mappings=${elapsed2.toFixed(1)}ms`);
