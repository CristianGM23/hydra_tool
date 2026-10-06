#!/usr/bin/env python3
"""V4.33.13: visual standalone + IDE full-stack export contracts and executable stress smoke."""
from pathlib import Path
import subprocess, tempfile, textwrap, sys
ROOT=Path(__file__).resolve().parents[2]
checks=[]
def check(name, cond):
    if not cond: raise AssertionError(name)
    checks.append(name)
def text(path): return (ROOT/path).read_text(encoding='utf-8')

support=text('src/main/java/com/example/editor/integration/compiler/TechnologyAdapterVisualFullStackSupport.java')
check('neutral visual definition', 'visual-editor-definition.json' in support)
check('visual full stack manifest', 'visual-full-stack.json' in support)
check('ts host consumes definition', 'applyVisualDefinition?.(HYDRA_VISUAL_DEFINITION)' in support)
check('python host consumes definition', 'apply_visual_definition' in support)
check('java host consumes definition', 'applyVisualDefinition(VISUAL_DEFINITION_JSON)' in support)
check('typescript limits are configurable numbers', 'export interface HydraVisualLimits' in support and 'limits?:Partial<HydraVisualLimits>' in support)
check('python bridge escapes control whitelist', "ch in '\\\\t\\\\n\\\\r'" in support)
check('portable bridges block mixed-case dangerous style keys', 'name.lower()' in support and 'equalsIgnoreCase(\"constructor\")' in support and 'name.toLowerCase()' in support)
for token in ['VALIDATIONS','ACTIONS','OPERATION_BEHAVIORS','EVENT_DEFINITIONS','EVENT_BINDINGS','EVENT_FLOWS','GAMIFICATION_DEFINITIONS','GAMIFICATION_BINDINGS']:
    check('layer '+token, token in support)
check('direct node mappings preserved', 'r.nodeMappingsByClassId?' in support and ':values(r.nodeMappings)' in support)
check('edge mappings preserved', 'grouped.length?grouped:values(r.edgeMappings)' in support)

adapters={
 'BESSER_WME':'src/main/java/com/example/editor/integration/adapter/besser/BesserWebModelingEditorTechnologyAdapter.java',
 'BESSER_SDK':'src/main/java/com/example/editor/integration/adapter/besser/BesserSdkTechnologyAdapter.java',
 'GLSP':'src/main/java/com/example/editor/integration/adapter/glsp/EclipseGlspTechnologyAdapter.java',
 'SIRIUS':'src/main/java/com/example/editor/integration/adapter/sirius/SiriusDesktopTechnologyAdapter.java',
 'ECLIPSE_DIST':'src/main/java/com/example/editor/integration/adapter/eclipse/EclipseModelingDistributionTechnologyAdapter.java'}
for name,path in adapters.items():
    source=text(path)
    check(name+' visual support imported','TechnologyAdapterVisualFullStackSupport' in source)
    check(name+' visual bridge emitted','HydraVisualFullStack' in source or 'hydra-visual-full-stack' in source or 'visual_editor.py' in source)
check('BESSER SDK accepts editor definition','IntegrationArtifactKind.EDITOR_DEFINITION' in text(adapters['BESSER_SDK']))
check('GLSP client visual bridge','client/src/hydra-visual-full-stack.ts' in text(adapters['GLSP']))
check('GLSP server visual bridge','HydraVisualFullStack.java' in text(adapters['GLSP']))
mxgraph=text('src/main/java/com/example/editor/integration/adapter/mxgraph/MxGraphDrawioTechnologyAdapter.java')
check('mxGraph executes conditional styles', 'resolveNodeStyle' in mxgraph and 'resolveEdgeStyle' in mxgraph and 'evaluateExpression' in mxgraph)
check('mxGraph bounds and sanitizes base styles', 'safeStyleEntry' in mxgraph and 'maximumStyleProperties' in mxgraph and 'boundedStyleValue' in mxgraph)

app=text('src/main/resources/standalone-template/frontend/app.js')
runtime=text('src/main/resources/standalone-template/frontend/runtime.js')
for token in ['renderDiagramElement','renderTree','renderTable','renderDashboard','renderMatrix','applyNodeStyles','applyLabelStyles','drawDiagramEdges']:
    check('standalone visual '+token,token in app)
for token in ['applyQuickFix','processFlows','processGamification','invokeOperation','dispatch']:
    check('standalone runtime '+token,token in runtime)
check('runtime event recursion bounded by iterative queue','maximumQueuedEvents=4096' in runtime and 'if(drainingEvents)return root' in runtime and 'if(++processed>maximumQueuedEvents)' in runtime)
check('event flow recursion bounded','depth>64' in runtime)
check('recent events bounded','recentEvents.length>30' in runtime)

# Executable stress: 5k elements, 60k diagnostics, quick fix, 20k events, cyclic binding, gamification.
js=textwrap.dedent(f'''\
const vm=require('vm'); const source={runtime!r}; const store=new Map();
const sandbox={{window:{{localStorage:{{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v)}}}},console}}; vm.createContext(sandbox); vm.runInContext(source,sandbox);
const N=5000,model={{id:'m',rootElementIds:[],elements:[],relations:[],annotations:{{}}}}; for(let i=0;i<N;i++){{model.elements.push({{id:'e'+i,classId:'C',attributes:{{name:' bad '}},annotations:{{}}}});model.rootElementIds.push('e'+i)}}
const expressions={{expressions:[]}},rules=[];for(let i=0;i<12;i++){{expressions.expressions.push({{id:'f'+i,root:{{kind:'LITERAL',value:false}}}});rules.push({{id:'r'+i,enabled:true,conditionExpressionId:'f'+i,context:{{classId:'C'}},severity:'ERROR',quickFixIds:['fix']}})}}
const nodes=[],transitions=[];for(let i=0;i<80;i++){{nodes.push({{id:'n'+i,kind:i===0?'START':'ACTION_STEP',enabled:true,referenceId:i?'missing':''}});if(i<79)transitions.push({{sourceNodeId:'n'+i,targetNodeId:'n'+(i+1)}})}}transitions.push({{sourceNodeId:'n79',targetNodeId:'n0'}});
const documents={{EXPRESSIONS:[expressions],VALIDATIONS:[{{rules}}],ACTIONS:[{{actions:[{{id:'fix',enabled:true,operations:[{{kind:'TRANSFORM_TEXT',target:{{kind:'SELF'}},attributeId:'name',transformation:'TRIM'}}]}}]}}],EVENT_BINDINGS:[{{bindings:[{{enabled:true,sourceEventTypeId:'cycle',targetEventTypeId:'cycle',payloadMappings:[]}}]}}],EVENT_FLOW:[{{flows:[{{id:'flow',enabled:true,triggerEventTypeIds:['core.quickfix.applied'],nodes,transitions}}]}}],GAMIFICATION:[{{pointTypes:[{{id:'xp',minimumBalance:0}}],levels:[],achievements:[],challenges:[]}},{{rules:[{{id:'g',enabled:true,eventTypeId:'core.quickfix.applied',pointsTypeId:'xp',points:2,repeatPolicy:'ALWAYS',maxAwardsPerActor:0,maxAwardsPerDay:0}}]}}]}};
const rt=sandbox.window.HydraGeneratedRuntime.create({{metamodel:{{id:'mm',classes:[{{id:'C'}}],relations:[]}},documents,getModel:()=>model}}); const p=rt.validate(); if(p.length!==60000)throw Error('diagnostics '+p.length); if(!rt.applyQuickFix('fix','e0').changed)throw Error('quickfix'); if(model.elements[0].attributes.name!=='bad')throw Error('quickfix value'); if(rt.snapshot().gamification.points.xp!==2)throw Error('gamification'); for(let i=0;i<20000;i++)rt.dispatch('perf',{{i}},{{elementId:'e'+(i%N)}});rt.dispatch('cycle',{{}},{{}});if(rt.snapshot().recentEvents.length>30)throw Error('unbounded recent events'); console.log('PASS');
''')
with tempfile.TemporaryDirectory() as td:
    p=Path(td)/'stress.js';p.write_text(js,encoding='utf-8')
    run=subprocess.run(['node',str(p)],capture_output=True,text=True,timeout=20)
    check('executable visual runtime stress',run.returncode==0 and 'PASS' in run.stdout)

print(f'V43313_VISUAL_FULL_STACK_EXPORT={len(checks)}/{len(checks)} PASS')
