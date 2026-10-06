package com.example.editor.event.flow;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.event.flow.runtime.EventFlowCompiler;
import com.example.editor.event.flow.runtime.ExecutableEventFlowPlan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventFlowCompilerTests {
    private static final String VALIDATION_COMPLETED = "core.validation.execution-completed";
    private final EventFlowCompiler compiler = new EventFlowCompiler(new EventFlowChecker());

    @Test void createsImmutablePlan() { ExecutableEventFlowPlan plan = compiler.compile(model(flow("flow:a", VALIDATION_COMPLETED)), null); assertThat(plan.sourceModelId()).isEqualTo("flow-model"); assertThat(plan.flows()).hasSize(1); }
    @Test void indexesExplicitTrigger() { ExecutableEventFlowPlan plan = compiler.compile(model(flow("flow:a", VALIDATION_COMPLETED)), null); assertThat(plan.flowsByTriggerEventType().get(VALIDATION_COMPLETED)).containsExactly("flow:a"); }
    @Test void indexesSourceNodeEventType() { FlowDefinition flow = sourceFlow("flow:a", "core.model.element-created"); ExecutableEventFlowPlan plan = compiler.compile(model(flow), null); assertThat(plan.flowsByTriggerEventType().get("core.model.element-created")).containsExactly("flow:a"); }
    @Test void deDuplicatesSourceAndExplicitTrigger() { FlowDefinition source = sourceFlow("flow:a", "core.model.element-created"); source = new FlowDefinition(source.id(), source.name(), source.description(), source.enabled(), List.of("core.model.element-created"), source.variables(), source.nodes(), source.transitions(), source.compensations(), source.annotations()); assertThat(compiler.compile(model(source), null).flowsByTriggerEventType().get("core.model.element-created")).containsExactly("flow:a"); }
    @Test void preservesNodeByIdIndex() { ExecutableEventFlowPlan plan = compiler.compile(model(flow("flow:a", VALIDATION_COMPLETED)), null); assertThat(plan.flows().get(0).nodes()).containsKeys("node:start-flow:a", "node:end-flow:a"); }
    @Test void preservesStartNodeId() { ExecutableEventFlowPlan plan = compiler.compile(model(flow("flow:a", VALIDATION_COMPLETED)), null); assertThat(plan.flows().get(0).startNodeId()).isEqualTo("node:start-flow:a"); }
    @Test void sortsOutgoingTransitionsByOrder() { FlowNode start = node("node:start", FlowNodeKind.START, ""); FlowNode first = node("node:first", FlowNodeKind.SUCCESS_END, ""); FlowNode second = node("node:second", FlowNodeKind.ERROR_END, ""); FlowDefinition flow = new FlowDefinition("flow:a", "A", "", true, List.of(), List.of(), List.of(start, first, second), List.of(new FlowTransition("t:2", start.id(), second.id(), FlowTransitionKind.ON_ERROR, "", 2, ""), new FlowTransition("t:1", start.id(), first.id(), FlowTransitionKind.ON_SUCCESS, "", 1, "")), List.of(), Map.of()); assertThat(compiler.compile(model(flow), null).flows().get(0).nodes().get(start.id()).outgoing()).extracting(FlowTransition::id).containsExactly("t:1", "t:2"); }
    @Test void keepsMultipleFlowsForSameTrigger() { ExecutableEventFlowPlan plan = compiler.compile(new EventFlowModel("1.0", "flow-model", "metamodel", "", "", List.of(flow("flow:a", VALIDATION_COMPLETED), flow("flow:b", VALIDATION_COMPLETED)), Map.of()), null); assertThat(plan.flowsByTriggerEventType().get(VALIDATION_COMPLETED)).containsExactly("flow:a", "flow:b"); }
    @Test void rejectsInvalidFlow() { EventFlowModel invalid = model(new FlowDefinition("flow:a", "A", "", true, List.of(), List.of(), List.of(node("node:end", FlowNodeKind.SUCCESS_END, "")), List.of(), List.of(), Map.of())); assertThatThrownBy(() -> compiler.compile(invalid, null)).isInstanceOf(EventFlowApplicationException.class); }
    @Test void planIdIsDerivedFromSource() { assertThat(compiler.compile(model(flow("flow:a", VALIDATION_COMPLETED)), null).id()).isEqualTo("flow-model.plan"); }
    @Test void planUsesSchemaVersionOne() { assertThat(compiler.compile(model(flow("flow:a", VALIDATION_COMPLETED)), null).schemaVersion()).isEqualTo("1.0"); }
    @Test void recordsCompilationTime() { assertThat(compiler.compile(model(flow("flow:a", VALIDATION_COMPLETED)), null).compiledAt()).isNotNull(); }

    private EventFlowModel model(FlowDefinition flow) { return new EventFlowModel("1.0", "flow-model", "metamodel", "", "", List.of(flow), Map.of()); }
    private FlowDefinition flow(String id, String trigger) { FlowNode start = node("node:start-" + id, FlowNodeKind.START, ""); FlowNode end = node("node:end-" + id, FlowNodeKind.SUCCESS_END, ""); return new FlowDefinition(id, id, "", true, List.of(trigger), List.of(), List.of(start, end), List.of(new FlowTransition("transition:" + id, start.id(), end.id(), FlowTransitionKind.NEXT, "", 0, "")), List.of(), Map.of()); }
    private FlowDefinition sourceFlow(String id, String eventType) { FlowNode source = node("node:source-" + id, FlowNodeKind.SOURCE, eventType); FlowNode end = node("node:end-" + id, FlowNodeKind.SUCCESS_END, ""); return new FlowDefinition(id, id, "", true, List.of(), List.of(), List.of(source, end), List.of(new FlowTransition("transition:" + id, source.id(), end.id(), FlowTransitionKind.NEXT, "", 0, "")), List.of(), Map.of()); }
    private FlowNode node(String id, FlowNodeKind kind, String eventType) { return new FlowNode(id, kind.name(), kind, "", eventType, "", Map.of(), RetryPolicy.none(), TimeoutPolicy.none(), false, true); }
}
