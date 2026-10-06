package com.example.editor.remote.auth;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Registro RAM acotado de flujos OAuth pendientes. Los estados completados se consumen una sola vez. */
@Component
public class RemoteAuthFlowStore {
    private final RemoteAuthProperties properties;
    private final Map<String,PendingRemoteAuthFlow> flows=new ConcurrentHashMap<>();
    /** Descripción: Inicializa una instancia de RemoteAuthFlowStore con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteAuthFlowStore(RemoteAuthProperties properties){this.properties=properties;}
    public synchronized void put(PendingRemoteAuthFlow flow){
        cleanup();
        while(flows.size()>=properties.getMaximumPendingFlows()){
            PendingRemoteAuthFlow oldest=flows.values().stream().min(Comparator.comparing(PendingRemoteAuthFlow::expiresAt)).orElse(null);
            if(oldest==null) break;
            flows.remove(oldest.state(),oldest);
        }
        flows.put(flow.state(),flow);
    }
    /** Descripción: Ejecuta la operación «peek» correspondiente a la responsabilidad de RemoteAuthFlowStore. Entradas: state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public PendingRemoteAuthFlow peek(String state){
        String key=normalized(state);PendingRemoteAuthFlow flow=flows.get(key);
        if(flow==null||flow.expiresAt().isBefore(Instant.now())){if(flow!=null)flows.remove(key,flow);throw expired();}
        return flow;
    }
    public synchronized void update(PendingRemoteAuthFlow flow){
        peek(flow.state());
        flows.put(flow.state(),flow);
    }
    /** Descripción: Ejecuta la operación «consume» correspondiente a la responsabilidad de RemoteAuthFlowStore. Entradas: state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public PendingRemoteAuthFlow consume(String state){
        String key=normalized(state);PendingRemoteAuthFlow flow=flows.remove(key);
        if(flow==null||flow.expiresAt().isBefore(Instant.now())) throw expired();
        return flow;
    }
    /** Descripción: Elimina o libera el recurso gestionado por «remove» de forma controlada. Entradas: state: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void remove(String state){flows.remove(normalized(state));}
    public synchronized void cleanup(){Instant now=Instant.now();flows.entrySet().removeIf(entry->entry.getValue().expiresAt().isBefore(now));}
    /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de RemoteAuthFlowStore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int size(){cleanup();return flows.size();}
    /** Descripción: Ejecuta la operación «normalized» correspondiente a la responsabilidad de RemoteAuthFlowStore. Entradas: state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String normalized(String state){return state==null?"":state.trim();}
    /** Descripción: Ejecuta la operación «expired» correspondiente a la responsabilidad de RemoteAuthFlowStore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private RemoteAuthException expired(){return new RemoteAuthException(RemoteAuthException.Reason.FLOW_EXPIRED,"The external authorization request expired or was already used");}
}
