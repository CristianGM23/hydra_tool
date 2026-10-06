package com.example.editor.definition.runtime.session;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Historial undo/redo acotado por sesión y por número de sesiones. La expulsión
 * LRU afecta únicamente a la capacidad de deshacer, nunca al modelo persistido.
 */
@Component
public class EditorSessionHistoryStore {
    private final EditorSessionProperties properties;
    private final LinkedHashMap<String,SessionHistory> sessions=new LinkedHashMap<>(16,0.75f,true);

    /** Descripción: Inicializa una instancia de EditorSessionHistoryStore con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionHistoryStore(EditorSessionProperties properties){this.properties=properties;}

    public synchronized void recordMutation(String sessionId,EditorModelSession previous) {
        SessionHistory history=history(sessionId,true);
        push(history.undo,previous);
        history.redo.clear();
        trimSessions();
    }

    public synchronized EditorModelSession takeUndo(String sessionId) {
        SessionHistory history=history(sessionId,false);
        if(history==null||history.undo.isEmpty()) throw conflict("Undo is not available");
        return history.undo.removeLast();
    }

    public synchronized void restoreUndo(String sessionId,EditorModelSession snapshot){push(history(sessionId,true).undo,snapshot);trimSessions();}
    public synchronized void recordRedo(String sessionId,EditorModelSession snapshot){push(history(sessionId,true).redo,snapshot);trimSessions();}

    /** Descripción: Ejecuta la operación «takeRedo» correspondiente a la responsabilidad de EditorSessionHistoryStore. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public synchronized EditorModelSession takeRedo(String sessionId) {
        SessionHistory history=history(sessionId,false);
        if(history==null||history.redo.isEmpty()) throw conflict("Redo is not available");
        return history.redo.removeLast();
    }

    /** Descripción: Ejecuta la operación «restoreRedo» correspondiente a la responsabilidad de EditorSessionHistoryStore. Entradas: sessionId: identificador que delimita el recurso objetivo; snapshot: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public synchronized void restoreRedo(String sessionId,EditorModelSession snapshot){push(history(sessionId,true).redo,snapshot);trimSessions();}
    /** Descripción: Ejecuta la operación «recordUndoFromRedo» correspondiente a la responsabilidad de EditorSessionHistoryStore. Entradas: sessionId: identificador que delimita el recurso objetivo; snapshot: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public synchronized void recordUndoFromRedo(String sessionId,EditorModelSession snapshot){push(history(sessionId,true).undo,snapshot);trimSessions();}
    /** Descripción: Ejecuta la operación «undoAvailable» correspondiente a la responsabilidad de EditorSessionHistoryStore. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public synchronized boolean undoAvailable(String sessionId){SessionHistory history=history(sessionId,false);return history!=null&&!history.undo.isEmpty();}
    /** Descripción: Ejecuta la operación «redoAvailable» correspondiente a la responsabilidad de EditorSessionHistoryStore. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public synchronized boolean redoAvailable(String sessionId){SessionHistory history=history(sessionId,false);return history!=null&&!history.redo.isEmpty();}
    /** Descripción: Elimina o libera el recurso gestionado por «clear» de forma controlada. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public synchronized void clear(String sessionId){if(sessionId!=null)sessions.remove(sessionId);}
    /** Descripción: Ejecuta la operación «sessionCount» correspondiente a la responsabilidad de EditorSessionHistoryStore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public synchronized int sessionCount(){return sessions.size();}

    /** Descripción: Ejecuta la operación «history» correspondiente a la responsabilidad de SessionHistory. Entradas: sessionId: identificador que delimita el recurso objetivo; create: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private SessionHistory history(String sessionId,boolean create) {
        SessionHistory current=sessions.get(sessionId);
        if(current==null&&create){current=new SessionHistory();sessions.put(sessionId,current);}
        return current;
    }

    /** Descripción: Ejecuta la operación «push» correspondiente a la responsabilidad de SessionHistory. Entradas: stack: dato de entrada necesario para la operación; session: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void push(Deque<EditorModelSession> stack,EditorModelSession session) {
        stack.addLast(session);
        while(stack.size()>properties.getMaximumHistoryEntries()) stack.removeFirst();
    }

    /** Descripción: Ejecuta la operación «trimSessions» correspondiente a la responsabilidad de SessionHistory. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trimSessions() {
        int maximum=properties.getMaximumHistorySessionsInMemory();
        while(sessions.size()>maximum) {
            String eldest=sessions.entrySet().iterator().next().getKey();
            sessions.remove(eldest);
        }
    }

    /** Descripción: Ejecuta la operación «conflict» correspondiente a la responsabilidad de SessionHistory. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static EditorSessionApplicationException conflict(String message){return new EditorSessionApplicationException(EditorSessionApplicationException.Reason.CONFLICT,message);}
    private static final class SessionHistory { private final Deque<EditorModelSession> undo=new ArrayDeque<>();private final Deque<EditorModelSession> redo=new ArrayDeque<>(); }
}
