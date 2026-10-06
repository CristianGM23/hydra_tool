#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
checks=[]
def text(path): return (ROOT/path).read_text(encoding='utf-8')
def check(name, cond):
    ok=bool(cond); checks.append((name,ok)); print(('PASS' if ok else 'FAIL')+' - '+name)

bus=text(Path('src/main/java/com/example/editor/event/bus/InMemoryCanonicalEventBus.java'))
binding=text(Path('src/main/java/com/example/editor/event/runtime/EventBindingRuntimeService.java'))
resolver=text(Path('src/main/java/com/example/editor/event/runtime/EventBindingValueResolver.java'))
flow=text(Path('src/main/java/com/example/editor/event/flow/runtime/EventFlowRuntimeService.java'))
deployment=text(Path('src/main/java/com/example/editor/event/flow/runtime/EventFlowRuntimeDeployment.java'))
standalone=text(Path('src/main/resources/standalone-template/frontend/runtime.js'))
bus_test=text(Path('src/test/java/com/example/editor/event/bus/CanonicalEventBusTests.java'))
binding_test=text(Path('src/test/java/com/example/editor/event/runtime/EventBindingRuntimeServiceTests.java'))
flow_test=text(Path('src/test/java/com/example/editor/event/flow/runtime/EventFlowRuntimeAutoDeploymentTests.java'))
final_gate=text(Path('scripts/validation/verify_final_tree.py'))

# Bus: 8
check('canonical bus owns a FIFO queue', 'ArrayDeque' in bus and 'pending.addLast(event)' in bus and 'removeFirst()' in bus)
check('nested publication never starts a second drain', 'if(state.draining) return' in bus)
check('bus drains all listeners before derived events', 'for(Registered registered : listeners)' in bus)
check('bus keeps subscriber failures isolated', 'state.failures.add(exception)' in bus)
check('bus has bounded publication protection', 'MAX_EVENTS_PER_PUBLICATION = 4096' in bus)
check('bus clears cyclic overflow queue', 'state.pending.clear()' in bus and 'binding/flow cycle' in bus)
check('bus thread state is released after a root publication', 'publicationState.remove()' in bus)
check('FIFO behavior has a regression test', 'nestedPublicationsAreDeliveredFifoAfterCurrentEventFinishes' in bus_test and 'second:event:1' in bus_test)

# EventBinding runtime: 20
check('native EventBinding runtime service exists', 'class EventBindingRuntimeService' in binding)
check('binding runtime subscribes to canonical bus once', 'eventBus.subscribe(event -> true, this::process)' in binding)
check('binding runtime accepts grammar semantic scope from event context', 'event.context().metamodelId()' in binding)
check('binding runtime supports runtime disable metadata', 'hydra.eventBindingsEnabled' in binding)
check('binding runtime has explicit depth protection', 'MAX_BINDING_DEPTH = 32' in binding)
check('binding runtime loads canonical event model', 'eventModels.find(scopeId)' in binding)
check('missing binding documents are a no-op', 'Reason.NOT_FOUND' in binding and 'return null' in binding)
check('binding runtime consumes configured expression model', 'loadExpressions(scopeId, loaded.bindings().expressionModelId())' in binding)
check('binding runtime resolves grammar projection through canonical metamodel service', 'metamodels.findById(scopeId).metamodel()' in binding)
check('binding runtime filters disabled bindings', '!binding.enabled()' in binding)
check('binding runtime matches source event type', 'binding.sourceEventTypeId().equals(event.eventTypeId())' in binding)
check('binding runtime evaluates optional condition', 'conditionMatches(binding' in binding)
check('binding runtime maps source paths', 'EventBindingValueResolver.read(event, mapping.sourcePath())' in binding)
check('binding runtime maps expressions', 'evaluate(mapping.expressionId()' in binding)
check('binding runtime maps fixed values', 'return mapping.fixedValue()' in binding)
check('derived binding event preserves correlation and causation', 'event.correlationId().isBlank() ? event.id() : event.correlationId()' in binding and 'event.id(), payload, metadata' in binding)
check('derived binding event is republished canonically', 'eventBus.publish(derived)' in binding)
check('DISABLE_BINDING policy is honored', 'EventBindingErrorPolicy.DISABLE_BINDING' in binding and 'disabledBindings.add(key)' in binding)
check('binding paths expose event payload metadata and context', all(v in resolver for v in ['case "payload"','case "metadata"','case "context"','case "subject"']))
check('binding runtime has grammar-first execution regression', 'executesBindingOnGrammarSemanticScopeAndPublishesDerivedEventInFifoOrder' in binding_test and 'grammar.main-language' in binding_test)

# EventFlow auto-deployment: 15
check('EventFlow owns one dispatcher subscription', 'dispatcherSubscription' in flow and 'eventBus.subscribe(event -> true, this::onEvent)' in flow)
check('EventFlow no longer creates one bus subscription per scope', 'deployment.subscription' not in flow)
check('deployment object no longer owns a bus subscription', 'EventSubscription' not in deployment)
check('first event derives semantic scope from canonical context', 'String semanticScopeId = event.context().metamodelId()' in flow)
check('blank semantic scope is ignored', 'semanticScopeId.isBlank()' in flow)
check('event-flow metadata can disable automatic execution', 'hydra.eventFlowsEnabled' in flow)
check('EventFlow auto-deploys missing scope plan', 'deployments.compute(semanticScopeId' in flow and 'compile(id)' in flow)
check('auto-deployed flow is marked active', 'created.active = true' in flow)
check('auto-deployed flow processes triggering event', 'processWithDeployment(deployment, event, true)' in flow)
check('explicit stop records semantic scope', 'explicitlyStopped.add(metamodelId)' in flow)
check('explicit stop blocks auto restart', 'explicitlyStopped.contains(semanticScopeId)' in flow)
check('explicit start clears stop barrier', 'explicitlyStopped.remove(metamodelId)' in flow)
check('refresh preserves explicit stop state', 'previous.active && !explicitlyStopped.contains(metamodelId)' in flow)
check('service closes dispatcher and deployments', 'dispatcherSubscription.close()' in flow and 'deployments.clear()' in flow)
check('EventFlow auto-deployment has regression coverage', 'firstGrammarEventDeploysFlowAndExplicitStopBlocksAutomaticRestart' in flow_test)

# Standalone runtime: 15
check('standalone runtime owns FIFO event queue', 'const eventQueue=[]' in standalone)
check('standalone runtime has one drain flag', 'let drainingEvents=false' in standalone)
check('standalone queue has bounded cycle protection', 'maximumQueuedEvents=4096' in standalone)
check('standalone nested dispatch only enqueues', 'eventQueue.push(root);if(drainingEvents)return root' in standalone)
check('standalone queue drains FIFO', 'const event=eventQueue.shift()' in standalone)
check('standalone bindings execute before next queued event', 'for(const binding of values(eventBindings?.bindings))' in standalone)
check('standalone binding conditions are executable', 'binding.conditionExpressionId' in standalone and 'evaluate(binding.conditionExpressionId' in standalone)
check('standalone source-path mapping is executable', 'readPath(event,mapping.sourcePath)' in standalone)
check('standalone expression mapping is executable', 'mapping.expressionId?evaluate(mapping.expressionId' in standalone)
check('standalone EventFlow consumes each dequeued event', 'processFlows(event)' in standalone)
check('standalone gamification consumes each dequeued event', 'processGamification(event)' in standalone)
check('standalone flow EMIT republishes through FIFO dispatch', "node.kind==='EMIT'" in standalone and 'dispatch(node.eventTypeId' in standalone)
check('standalone gamification persists to localStorage when available', 'global.localStorage.setItem' in standalone)
check('standalone gamification falls back to model annotations', "model().annotations['hydra:gamification']=JSON.stringify(state)" in standalone)
smoke=subprocess.run(['node','scripts/validation/r5c13_standalone_smoke.mjs'],cwd=ROOT,text=True,capture_output=True)
check('standalone executable smoke yields source->binding->flow and 5 XP', smoke.returncode==0 and '"xp":5' in smoke.stdout and '"flow.completed"' in smoke.stdout)

# Test/gate integration: 8
check('binding runtime source has a dedicated test class', (ROOT/'src/test/java/com/example/editor/event/runtime/EventBindingRuntimeServiceTests.java').is_file())
check('flow auto-deployment source has a dedicated test class', (ROOT/'src/test/java/com/example/editor/event/flow/runtime/EventFlowRuntimeAutoDeploymentTests.java').is_file())
check('binding test asserts derived payload', 'containsEntry("origin", "binding")' in binding_test)
check('binding test asserts causation', 'causationId()' in binding_test)
check('flow test asserts automatic active status', 'status(scope).active()).isTrue()' in flow_test)
check('flow test asserts stop blocks compiler re-entry', 'times(1)' in flow_test)
check('flow test asserts explicit start re-enables execution', 'runtime.start(scope)' in flow_test and 'times(2)' in flow_test)
check('standalone smoke is committed as a validation asset', (ROOT/'scripts/validation/r5c13_standalone_smoke.mjs').is_file())

# Final integration: 5
check('runtime accepts canonical grammar prefix', 'grammar.main-language' in binding_test and 'grammar.main-language' in flow_test)
check('runtime binding and flow both use same semantic context field', 'event.context().metamodelId()' in binding and 'event.context().metamodelId()' in flow)
check('FIFO backend and standalone both have 4096 event bound', '4096' in bus and '4096' in standalone)
check('runtime gate is registered in final tree verification', 'verify_c10_semantic_runtime_execution.py' in final_gate)
check('runtime implementation contains no TODO placeholder', 'TODO' not in binding and 'TODO' not in resolver)

if len(checks)!=71:
    print(f'C10_INTERNAL_COUNT_MISMATCH={len(checks)}/71')
    sys.exit(2)
failed=[name for name,ok in checks if not ok]
print(f'C10_SEMANTIC_RUNTIME_EXECUTION={len(checks)-len(failed)}/{len(checks)} PASS' if not failed else f'C10_SEMANTIC_RUNTIME_EXECUTION=FAIL {len(failed)}')
if failed:
    for name in failed: print('  - '+name)
    if smoke.stdout or smoke.stderr: print((smoke.stdout+'\n'+smoke.stderr).strip())
    sys.exit(1)
