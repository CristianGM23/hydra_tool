import fs from 'node:fs';
import vm from 'node:vm';

const runtimeSource = fs.readFileSync('src/main/resources/standalone-template/frontend/runtime.js', 'utf8');
const model = {id:'model.demo', metamodelId:'grammar.demo', annotations:{}, elements:[], relations:[]};
const documents = {
  EVENT_BINDINGS:[{
    bindings:[{
      id:'binding.saved', enabled:true, sourceEventTypeId:'source.saved', targetEventTypeId:'domain.saved',
      payloadMappings:[{targetFieldId:'origin', sourcePath:'payload.origin'}]
    }]
  }],
  EVENT_FLOW:[{
    flows:[{
      id:'flow.saved', enabled:true, triggerEventTypeIds:['domain.saved'],
      nodes:[
        {id:'start', kind:'START', enabled:true},
        {id:'emit', kind:'EMIT', enabled:true, eventTypeId:'flow.completed'}
      ],
      transitions:[{id:'t1', sourceNodeId:'start', targetNodeId:'emit', order:0}]
    }]
  }],
  GAMIFICATION:[
    {pointTypes:[{id:'xp', minimumBalance:0}], achievements:[], challenges:[], levels:[]},
    {rules:[{id:'rule.flow', enabled:true, eventTypeId:'flow.completed', pointsTypeId:'xp', points:5}]}
  ]
};
const sandbox = {console, window:{}, structuredClone};
sandbox.window.console = console;
vm.createContext(sandbox);
vm.runInContext(runtimeSource, sandbox, {filename:'runtime.js'});
const runtime = sandbox.window.HydraGeneratedRuntime.create({metamodel:{id:'grammar.demo',classes:[],relations:[]},documents,getModel:()=>model});
runtime.dispatch('source.saved',{origin:'smoke'},{});
const snapshot = runtime.snapshot();
const chronological = [...snapshot.recentEvents].reverse().map(event=>event.eventTypeId);
if(JSON.stringify(chronological)!==JSON.stringify(['source.saved','domain.saved','flow.completed'])) {
  throw new Error(`Unexpected event order: ${JSON.stringify(chronological)}`);
}
if(snapshot.gamification.points.xp!==5) throw new Error(`Expected xp=5, got ${snapshot.gamification.points.xp}`);
if(!model.annotations['hydra:gamification']) throw new Error('Gamification fallback was not persisted into model annotations');
console.log(JSON.stringify({ok:true,events:chronological,xp:snapshot.gamification.points.xp,fallback:true}));
