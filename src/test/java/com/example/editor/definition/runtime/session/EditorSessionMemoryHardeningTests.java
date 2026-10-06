package com.example.editor.definition.runtime.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.validation.model.CanonicalModelDocument;
import tools.jackson.databind.ObjectMapper;

class EditorSessionMemoryHardeningTests {
    @TempDir Path temp;

    @Test
    void pendingEventKeepsOnlyCompactContextInsteadOfTheCompleteSessionModel() {
        List<Class<?>> componentTypes=Arrays.stream(PendingEditorEvent.class.getRecordComponents())
                .<Class<?>>map(component -> component.getType()).toList();
        assertFalse(componentTypes.contains(EditorModelSession.class));
        assertFalse(componentTypes.contains(CanonicalModelDocument.class));
        assertTrue(componentTypes.contains(EditorSessionEventContext.class));
    }

    @Test
    void pendingEventsSpillAfterTheConfiguredRamThresholdAndFlushInOrder() throws Exception {
        EditorSessionProperties properties=new EditorSessionProperties();
        properties.setDirectory(temp);
        properties.setMaximumPendingEventsInMemory(2);
        EditorSessionPendingEventBuffer buffer=new EditorSessionPendingEventBuffer(properties,new ObjectMapper());
        EditorSessionEventContext context=new EditorSessionEventContext("session-one","people","model-one","people-editor",1);
        for(int index=0;index<5;index++) buffer.enqueue(new PendingEditorEvent("core.model.updated",context,"element-"+index,"people.Person",Map.of("index",index)));

        EditorSessionPendingEventBuffer.PendingEventBufferStatus status=buffer.status("session-one");
        assertEquals(2,status.inMemoryEvents());
        assertTrue(status.spooledBytes()>0);
        assertTrue(status.spooling());

        List<Integer> flushed=new ArrayList<>();
        buffer.flush("session-one",event->flushed.add((Integer)event.payload().get("index")));
        assertEquals(List.of(0,1,2,3,4),flushed);
        assertEquals(0,buffer.status("session-one").inMemoryEvents());
        try(var files=Files.list(properties.getPendingEventSpoolDirectory())) { assertEquals(0,files.count()); }
    }

    @Test
    void unsavedWorkingCopiesAreBoundedWithoutSilentlyEvictingUserWork() {
        EditorSessionProperties properties=new EditorSessionProperties();
        properties.setMaximumWorkingCopiesInMemory(1);
        EditorSessionWorkingCopyStore store=new EditorSessionWorkingCopyStore(properties);
        store.put(new PendingEditorSession(session("session-one"),"base-1","draft-1",temp.resolve("one.json")));
        EditorSessionApplicationException exception=assertThrows(EditorSessionApplicationException.class,()->
                store.put(new PendingEditorSession(session("session-two"),"base-2","draft-2",temp.resolve("two.json"))));
        assertEquals(EditorSessionApplicationException.Reason.CONFLICT,exception.getReason());
        assertEquals(1,store.size());
        assertTrue(store.contains("session-one"));
        assertFalse(store.contains("session-two"));
    }

    @Test
    void undoHistoryUsesAnLruBoundAcrossSessions() {
        EditorSessionProperties properties=new EditorSessionProperties();
        properties.setMaximumHistoryEntries(3);
        properties.setMaximumHistorySessionsInMemory(2);
        EditorSessionHistoryStore history=new EditorSessionHistoryStore(properties);
        history.recordMutation("session-one",session("session-one"));
        history.recordMutation("session-two",session("session-two"));
        assertTrue(history.undoAvailable("session-one")); // refresh LRU order
        history.recordMutation("session-three",session("session-three"));
        assertEquals(2,history.sessionCount());
        assertTrue(history.undoAvailable("session-one"));
        assertFalse(history.undoAvailable("session-two"));
        assertTrue(history.undoAvailable("session-three"));
    }

    private EditorModelSession session(String id) {
        CanonicalModelDocument model=new CanonicalModelDocument("1.0","model-"+id,id.equals("session-two")?"people":"people","","Model",List.of(),List.of(),List.of(),Map.of());
        return new EditorModelSession("1.0",id,"people","people-editor","etag","representation:main",model,0,Instant.EPOCH,Instant.EPOCH,Map.of());
    }
}
