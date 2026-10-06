package com.example.editor.event.flow;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EventFlowCheckerTests {
    private final EventFlowChecker checker = new EventFlowChecker();

    @Test void acceptsMinimalStartToEndFlow() { assertThat(checker.check(model(flow(start(), end(), transition("t:1", "node:start", "node:end", FlowTransitionKind.NEXT))), null).valid()).isTrue(); }
    @Test void rejectsMissingStart() { assertCode(flow(end()), "FLOW_START_COUNT"); }
    @Test void rejectsTwoStarts() { assertCode(flow(start(), node("node:start-2", FlowNodeKind.START), end()), "FLOW_START_COUNT"); }
    @Test void rejectsMissingEnd() { FlowNode middle = node("node:middle", FlowNodeKind.MAP); assertCode(flow(start(), middle, transition("t:1", "node:start", middle.id(), FlowTransitionKind.NEXT)), "FLOW_END_REQUIRED"); }
    @Test void rejectsOrphanNode() { assertCode(flow(start(), node("node:orphan", FlowNodeKind.MAP), end(), transition("t:1", "node:start", "node:end", FlowTransitionKind.NEXT)), "ORPHAN_NODE"); }
    @Test void rejectsUnknownTransitionTarget() { assertCode(flow(start(), end(), transition("t:1", "node:start", "node:missing", FlowTransitionKind.NEXT)), "UNKNOWN_TRANSITION_TARGET"); }
    @Test void requiresTrueAndFalseDecisionBranches() { FlowNode decision = node("node:decision", FlowNodeKind.DECISION); assertCode(flow(start(), decision, end(), transition("t:1", "node:start", decision.id(), FlowTransitionKind.NEXT), transition("t:2", decision.id(), "node:end", FlowTransitionKind.TRUE)), "DECISION_BRANCHES"); }
    @Test void requiresTwoJoinInputs() { FlowNode join = node("node:join", FlowNodeKind.JOIN); assertCode(flow(start(), join, end(), transition("t:1", "node:start", join.id(), FlowTransitionKind.NEXT), transition("t:2", join.id(), "node:end", FlowTransitionKind.NEXT)), "JOIN_INPUT_COUNT"); }
    @Test void requiresEventTypeForEmit() { FlowNode emit = node("node:emit", FlowNodeKind.EMIT); assertCode(flow(start(), emit, end(), transition("t:1", "node:start", emit.id(), FlowTransitionKind.NEXT), transition("t:2", emit.id(), "node:end", FlowTransitionKind.NEXT)), "EVENT_TYPE_REQUIRED"); }
    @Test void rejectsUnknownTriggerEvent() { FlowDefinition value = baseFlow(); value = new FlowDefinition(value.id(), value.name(), value.description(), value.enabled(), List.of("custom.unknown"), value.variables(), value.nodes(), value.transitions(), value.compensations(), value.annotations()); assertCode(value, "UNKNOWN_TRIGGER_EVENT"); }
    @Test void rejectsCompensationFromNonCompensableNode() { FlowNode work = node("node:work", FlowNodeKind.ACTION_STEP); FlowNode compensation = node("node:compensation", FlowNodeKind.COMPENSATION_STEP); FlowDefinition value = flow(start(), work, compensation, end(), transition("t:1", "node:start", work.id(), FlowTransitionKind.NEXT), transition("t:2", work.id(), "node:end", FlowTransitionKind.NEXT), transition("t:3", compensation.id(), "node:end", FlowTransitionKind.NEXT)); value = new FlowDefinition(value.id(), value.name(), value.description(), value.enabled(), value.triggerEventTypeIds(), value.variables(), value.nodes(), value.transitions(), List.of(new CompensationLink("comp:1", work.id(), compensation.id())), value.annotations()); assertCode(value, "SOURCE_NOT_COMPENSABLE"); }
    @Test void rejectsInvalidCompensationTargetKind() { FlowNode work = new FlowNode("node:work", "Work", FlowNodeKind.ACTION_STEP, "", "", "", Map.of(), RetryPolicy.none(), TimeoutPolicy.none(), true, true); FlowDefinition value = flow(start(), work, end(), transition("t:1", "node:start", work.id(), FlowTransitionKind.NEXT), transition("t:2", work.id(), "node:end", FlowTransitionKind.NEXT)); value = new FlowDefinition(value.id(), value.name(), value.description(), value.enabled(), value.triggerEventTypeIds(), value.variables(), value.nodes(), value.transitions(), List.of(new CompensationLink("comp:1", work.id(), "node:end")), value.annotations()); assertCode(value, "INVALID_COMPENSATION_TARGET"); }
    @Test void reportsDuplicateFlowIds() { EventFlowModel value = new EventFlowModel("1.0", "flow-model", "metamodel", "", "", List.of(baseFlow(), baseFlow()), Map.of()); assertThat(checker.check(value, null).diagnostics()).anyMatch(item -> item.code().equals("DUPLICATE_FLOW_ID")); }
    @Test void reportsUnreachableNode() { FlowNode orphan = node("node:orphan", FlowNodeKind.MAP); FlowDefinition value = flow(start(), orphan, end(), transition("t:1", "node:start", "node:end", FlowTransitionKind.NEXT), transition("t:2", orphan.id(), "node:end", FlowTransitionKind.NEXT)); assertCode(value, "UNREACHABLE_NODE"); }

    private void assertCode(FlowDefinition flow, String code) { assertThat(checker.check(model(flow), null).diagnostics()).anyMatch(item -> item.code().equals(code)); }
    private EventFlowModel model(FlowDefinition flow) { return new EventFlowModel("1.0", "flow-model", "metamodel", "", "", List.of(flow), Map.of()); }
    private FlowDefinition baseFlow() { return flow(start(), end(), transition("t:1", "node:start", "node:end", FlowTransitionKind.NEXT)); }
    private FlowDefinition flow(Object... values) { List<FlowNode> nodes = java.util.Arrays.stream(values).filter(FlowNode.class::isInstance).map(FlowNode.class::cast).toList(); List<FlowTransition> transitions = java.util.Arrays.stream(values).filter(FlowTransition.class::isInstance).map(FlowTransition.class::cast).toList(); return new FlowDefinition("flow:main", "Main", "", true, List.of(), List.of(), nodes, transitions, List.of(), Map.of()); }
    private FlowNode start() { return node("node:start", FlowNodeKind.START); }
    private FlowNode end() { return node("node:end", FlowNodeKind.SUCCESS_END); }
    private FlowNode node(String id, FlowNodeKind kind) { return new FlowNode(id, kind.name(), kind, "", "", "", Map.of(), RetryPolicy.none(), TimeoutPolicy.none(), false, true); }
    private FlowTransition transition(String id, String source, String target, FlowTransitionKind kind) { return new FlowTransition(id, source, target, kind, "", 0, ""); }
}
