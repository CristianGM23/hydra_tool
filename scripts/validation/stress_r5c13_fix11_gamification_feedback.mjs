import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';

const source=fs.readFileSync(new URL('../../src/main/resources/standalone-template/frontend/runtime.js',import.meta.url),'utf8');
function loadRuntime(localStorageImpl){
  const sandbox={console:{warn(){},log(){},error(){}},Date,Math,JSON,Object,Array,Set,Map,Number,String,Boolean,RegExp,setTimeout,clearTimeout,localStorage:localStorageImpl};
  sandbox.window=sandbox;
  vm.runInNewContext(source,sandbox,{filename:'runtime.js'});
  return sandbox.HydraGeneratedRuntime;
}
function memoryStorage(){const map=new Map();return{getItem:key=>map.has(key)?map.get(key):null,setItem:(key,value)=>map.set(key,String(value)),removeItem:key=>map.delete(key)};}
function gameDocuments(overrides={}){
  return {GAMIFICATION:[
    {id:'defs',pointTypes:[{id:'points:quality',name:'Quality',minimumBalance:-100000}],levels:[{id:'level:one',name:'Level one',pointTypeId:'points:quality',minimumPoints:10,rewardId:''}],achievements:[],challenges:[],rewards:[],annotations:{'hydra.feedback.maxVisible':'3','hydra.feedback.dedupeWindowSeconds':'3',...overrides}},
    {id:'bindings',rules:[{id:'rule:quality',name:'Quality',eventTypeId:'model.changed',pointsTypeId:'points:quality',points:1,achievementId:'',challengeId:'',rewardId:'',repeatPolicy:'UNLIMITED',maxAwardsPerActor:0,maxAwardsPerDay:0,cooldownSeconds:0,effectKind:'AWARD',enabled:true}],annotations:{}}
  ]};
}
function model(){return{id:'model',elements:[],relations:[],rootElementIds:[],annotations:{}};}

// High-volume event burst: state must remain correct while UI history remains bounded.
{
  const storage=memoryStorage();const runtime=loadRuntime(storage);const m=model();
  const api=runtime.create({metamodel:{id:'scope',classes:[],relations:[]},documents:gameDocuments(),getModel:()=>m});
  for(let i=0;i<12000;i++)api.dispatch('model.changed',{sequence:i},{});
  const snap=api.snapshot();
  assert.equal(snap.gamification.points['points:quality'],12000);
  assert.ok(snap.feedback.length<=200,'feedback history must remain bounded');
  assert.equal(new Set(snap.feedback.map(item=>item.id)).size,snap.feedback.length,'feedback ids must not duplicate in retained window');
  assert.ok(snap.feedback.every(item=>item.presentation.durationMillis>=1000&&item.presentation.durationMillis<=30000));
}

// Persistence failure: semantic execution and feedback must fall back to model annotations.
{
  const broken={getItem(){throw new Error('storage unavailable')},setItem(){throw new Error('storage unavailable')}};const runtime=loadRuntime(broken);const m=model();
  const api=runtime.create({metamodel:{id:'scope',classes:[],relations:[]},documents:gameDocuments(),getModel:()=>m});
  api.dispatch('model.changed',{},{});const snap=api.snapshot();
  assert.equal(snap.gamification.points['points:quality'],1);assert.ok(m.annotations['hydra:gamification']);assert.ok(snap.feedback.length>0);
}

// Event-binding cycle: bounded queue must stop an accidental cycle without hanging the host.
{
  const runtime=loadRuntime(memoryStorage());const m=model();const docs=gameDocuments();docs.EVENT_BINDINGS=[{bindings:[{id:'a-b',enabled:true,sourceEventTypeId:'cycle.a',targetEventTypeId:'cycle.b',payloadMappings:[]},{id:'b-a',enabled:true,sourceEventTypeId:'cycle.b',targetEventTypeId:'cycle.a',payloadMappings:[]}]}];
  const api=runtime.create({metamodel:{id:'scope',classes:[],relations:[]},documents:docs,getModel:()=>m});
  api.dispatch('cycle.a',{},{});const snap=api.snapshot();assert.ok(snap.recentEvents.length<=30);
}
console.log('[HYDRA] Fix11 gamification feedback stress: PASS (12000 awards, persistence failure, cyclic event queue)');
