/** B9: execute the JavaScript runtime ACTUALLY embedded in HYDRA exported editors.
 * This proves behavior for these canonical artifacts in this runtime only. The
 * tests MUST NOT be presented as EMF or Langium native execution evidence. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const source=fs.readFileSync(path.join(root,'src/main/resources/standalone-template/frontend/runtime.js'),'utf8');
const fixtures=path.join(root,'reference-projects/library-basic/artifacts');
const read=(name)=>JSON.parse(fs.readFileSync(path.join(fixtures,name),'utf8'));
const metamodel=read('library.metamodel.json');
const original=read('library.model.json');
let passed=0;
function harness(model,documents){
  const storage=new Map();
  const window={localStorage:{getItem:key=>storage.get(key)||null,setItem:(key,value)=>storage.set(key,String(value))}};
  const sandbox={window, console,Date,Math,JSON,Object,Set,Map};
  vm.runInNewContext(source,sandbox,{filename:'HYDRA-standalone-production-runtime.js',timeout:3000});
  assert.equal(typeof window.HydraGeneratedRuntime?.create,'function');
  return window.HydraGeneratedRuntime.create({metamodel,documents,getModel:()=>model});
}
function test(label,callback){callback();passed++;console.log('PASS '+label);}
function brokenBook(){const model=structuredClone(original);model.elements.find(e=>e.classId==='class:Book').attributes['attribute:class:Book:pages']=0;return model;}
test('real runtime validates provided canonical library model',()=>{
  const runtime=harness(structuredClone(original),{EXPRESSIONS:[read('library.expressions.json')],VALIDATIONS:[read('library.validations.json')]});
  assert.equal(runtime.validate('FULL_VALIDATION').length,0);
});
test('real runtime catches book pages validation',()=>{
  const runtime=harness(brokenBook(),{EXPRESSIONS:[read('library.expressions.json')],VALIDATIONS:[read('library.validations.json')]});
  assert(runtime.validate('FULL_VALIDATION').some(issue=>issue.ruleId==='rule:book-pages-positive'));
});
test('real runtime runs configured canonical quick fix',()=>{
  const model=brokenBook();const runtime=harness(model,{EXPRESSIONS:[read('library.expressions.json')],VALIDATIONS:[read('library.validations.json')],ACTIONS:[read('library.actions.json')]});
  assert.equal(runtime.validate('FULL_VALIDATION').length,1);
  const result=runtime.applyQuickFix('action:set-pages-one','object:book-1');
  assert(result.changed);
  assert.equal(model.elements[1].attributes['attribute:class:Book:pages'],1);
  assert.equal(runtime.validate('FULL_VALIDATION').length,0);
});
test('real runtime refuses unsupported quick fix',()=>{
  const model=brokenBook();const runtime=harness(model,{ACTIONS:[read('library.actions.json')]});
  assert.equal(runtime.applyQuickFix('not-an-action','object:book-1').changed,false);
});
test('real runtime emits a quick fix event',()=>{
  const runtime=harness(brokenBook(),{ACTIONS:[read('library.actions.json')]});
  runtime.applyQuickFix('action:set-pages-one','object:book-1');
  assert(runtime.snapshot().recentEvents.some(e=>e.eventTypeId==='core.quickfix.applied'));
});
test('real runtime respects exactly-once gamification',()=>{
  const model=structuredClone(original);
  const docs={GAMIFICATION:[{id:'teaching',pointTypes:[{id:'quality',name:'Quality',minimumBalance:0}],levels:[],achievements:[],challenges:[],rewards:[]},
    {id:'teaching-rules',rules:[{id:'first-save',eventTypeId:'core.model.saved',enabled:true,
      repeatPolicy:'ONCE',pointsTypeId:'quality',points:5}]}]};
  const runtime=harness(model,docs);
  runtime.dispatch('core.model.saved',{modelId:model.id});
  assert.equal(runtime.snapshot().gamification.points.quality,5);
  runtime.dispatch('core.model.saved',{modelId:model.id});
  assert.equal(runtime.snapshot().gamification.points.quality,5);
});
test('real runtime excludes disabled gamification rules',()=>{
  const model=structuredClone(original);
  const runtime=harness(model,{GAMIFICATION:[{pointTypes:[{id:'quality'}],levels:[],achievements:[],challenges:[],rewards:[]},
    {rules:[{id:'disabled',eventTypeId:'core.model.saved',enabled:false,pointsTypeId:'quality',points:10}]}]});
  runtime.dispatch('core.model.saved',{});
  assert.equal(runtime.snapshot().gamification.points.quality||0,0);
});
test('real runtime diagnoses broken model references',()=>{
  const model=structuredClone(original);model.relations[0].targetElementId='missing-book';
  const runtime=harness(model,{});
  assert(runtime.validate('FULL_VALIDATION').some(issue=>issue.message.includes('Broken relationship')));
});
console.log(`FIX180_B9_REAL_JS_RUNTIME=PASS scenarios=${passed} native_emf_langium=NOT_CERTIFIED`);
