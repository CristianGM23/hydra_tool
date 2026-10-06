package com.example.editor.event.flow;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventContext;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventSource;
import com.example.editor.event.flow.runtime.EventFlowRuntimeSnapshot;
import com.example.editor.event.flow.runtime.EventFlowRuntimeStateDocument;
import com.example.editor.event.flow.runtime.FlowAbsenceContinuation;
import com.example.editor.event.flow.runtime.FlowExecutionStatus;
import com.example.editor.event.flow.runtime.FlowExecutionTrace;
import com.example.editor.event.flow.runtime.FlowNodeExecution;
import com.example.editor.event.flow.runtime.FlowWaitingContinuation;

class EventFlowRuntimeStateTests {
    @Test void emptyStateContainsNoCepEntries(){assertThat(EventFlowRuntimeStateDocument.empty().windows()).isEmpty();}
    @Test void stateCopiesDeduplicationMap(){var source=new java.util.LinkedHashMap<String,Map<String,Instant>>();source.put("node",new java.util.LinkedHashMap<>(Map.of("event",Instant.EPOCH)));var state=new EventFlowRuntimeStateDocument(source,Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),List.of());source.clear();assertThat(state.deduplication()).containsKey("node");}
    @Test void stateCopiesWindows(){var state=new EventFlowRuntimeStateDocument(Map.of(),Map.of("window",List.of(event("event:one","custom.start"))),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),List.of());assertThat(state.windows().get("window")).hasSize(1);}
    @Test void stateCopiesJoinEvents(){var state=new EventFlowRuntimeStateDocument(Map.of(),Map.of(),Map.of(),Map.of(),Map.of("join",Map.of("custom.start",event("event:one","custom.start"))),Map.of(),Map.of(),List.of());assertThat(state.joins().get("join")).containsKey("custom.start");}
    @Test void statePreservesSequenceProgress(){var state=new EventFlowRuntimeStateDocument(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of("sequence",2),Map.of(),List.of());assertThat(state.sequences()).containsEntry("sequence",2);}
    @Test void statePreservesPatternProgress(){var state=new EventFlowRuntimeStateDocument(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of("pattern",Set.of("custom.start")),List.of());assertThat(state.patterns().get("pattern")).contains("custom.start");}
    @Test void snapshotDefaultsRuntimeStateForOldDocuments(){var snapshot=new EventFlowRuntimeSnapshot("1.0","people","people",Instant.EPOCH,List.of(),List.of());assertThat(snapshot.runtimeState().absenceWatches()).isEmpty();}
    @Test void snapshotPreservesWaitingContinuation(){var wait=new FlowWaitingContinuation("wait:one","flow:one","node:wait","custom.done","correlation:one",Instant.EPOCH,Instant.EPOCH.plusSeconds(5),event("event:one","custom.start"),Map.of(),List.of());var snapshot=new EventFlowRuntimeSnapshot("1.0","people","people",Instant.EPOCH,List.of(wait),List.of(),EventFlowRuntimeStateDocument.empty());assertThat(snapshot.waitingContinuations()).containsExactly(wait);}
    @Test void snapshotPreservesAbsenceContinuation(){var absence=new FlowAbsenceContinuation("absence:one","flow:one","node:absence","key","correlationId","custom.cancel","custom.timeout",Instant.EPOCH,Instant.EPOCH.plusSeconds(5),event("event:one","custom.start"));var state=new EventFlowRuntimeStateDocument(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),List.of(absence));assertThat(state.absenceWatches()).containsExactly(absence);}
    @Test void snapshotPreservesCompensatedTrace(){var trace=new FlowExecutionTrace("trace:one","flow:one","event:one",Instant.EPOCH,Instant.EPOCH,FlowExecutionStatus.COMPENSATED,List.of(new FlowNodeExecution("node:undo",FlowNodeKind.COMPENSATION_STEP,FlowExecutionStatus.COMPENSATED,"done",Instant.EPOCH)),List.of(),"compensated");var snapshot=new EventFlowRuntimeSnapshot("1.0","people","people",Instant.EPOCH,List.of(),List.of(trace),EventFlowRuntimeStateDocument.empty());assertThat(snapshot.traces()).singleElement().extracting(FlowExecutionTrace::status).isEqualTo(FlowExecutionStatus.COMPENSATED);}

    @Test void snapshotPreservesScheduledRetry(){var retry=new com.example.editor.event.flow.runtime.FlowRetryContinuation("retry:one","flow:one","node:action",1,3,Instant.EPOCH.plusSeconds(2),Instant.EPOCH,event("event:one","custom.start"),event("event:one","custom.start"),Map.of(),List.of(),List.of());var snapshot=new EventFlowRuntimeSnapshot("1.0","people","people",Instant.EPOCH,List.of(),List.of(retry),List.of(),EventFlowRuntimeStateDocument.empty());assertThat(snapshot.retryContinuations()).containsExactly(retry);}
    @Test void oldSnapshotConstructorDefaultsScheduledRetries(){var snapshot=new EventFlowRuntimeSnapshot("1.0","people","people",Instant.EPOCH,List.of(),List.of(),EventFlowRuntimeStateDocument.empty());assertThat(snapshot.retryContinuations()).isEmpty();}
    @Test void retryContinuationDefensivelyCopiesVariables(){var variables=new java.util.LinkedHashMap<String,Object>();variables.put("attempt",1);var retry=new com.example.editor.event.flow.runtime.FlowRetryContinuation("retry:one","flow:one","node:action",1,3,Instant.EPOCH,Instant.EPOCH,event("event:one","custom.start"),event("event:one","custom.start"),variables,List.of(),List.of());variables.clear();assertThat(retry.variables()).containsEntry("attempt",1);}

    private CanonicalEvent event(String id,String type){return new CanonicalEvent("1.0",id,type,"1.0",Instant.EPOCH,new EventSource("tests","flow","","1.0"),new EventContext(EventContextKind.MODEL_EDITING,"","","people","model:one","","session:one"),null,null,"","correlation:one","",Map.of(),Map.of());}
}
