package com.example.editor.remote.auth;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Resultado efímero del callback OAuth para que la ventana principal conozca éxito o error sin compartir opener ni secretos. */
@Component
public class RemoteAuthOutcomeStore {
    private final RemoteAuthProperties properties;
    private final Map<String,Outcome> outcomes=new ConcurrentHashMap<>();
    /** Descripción: Inicializa una instancia de RemoteAuthOutcomeStore con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public RemoteAuthOutcomeStore(RemoteAuthProperties properties){this.properties=properties;}

    public synchronized void success(String state,String profileId,RemoteAuthConnectionDescriptor connection,Instant expiresAt){put(new Outcome(normalize(state),profileId,true,connection,null,"",expires(expiresAt)));}
    public synchronized void failure(String state,String profileId,RemoteAuthException exception,Instant expiresAt){put(new Outcome(normalize(state),profileId,false,null,exception.getReason(),RemoteSecretRedactor.redact(exception.getMessage()),expires(expiresAt)));}
    /** Descripción: Obtiene la información correspondiente a «find» dentro de RemoteAuthOutcomeStore. Entradas: state: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Outcome find(String state){cleanup();return outcomes.get(normalize(state));}
    public synchronized void remove(String state){outcomes.remove(normalize(state));}
    public synchronized void cleanup(){Instant now=Instant.now();outcomes.entrySet().removeIf(entry->entry.getValue().expiresAt().isBefore(now));}
    /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de RemoteAuthOutcomeStore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int size(){cleanup();return outcomes.size();}

    /** Descripción: Ejecuta la operación «put» correspondiente a la responsabilidad de RemoteAuthOutcomeStore. Entradas: outcome: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void put(Outcome outcome){
        cleanup();int maximum=Math.max(1,properties.getMaximumPendingFlows());
        while(outcomes.size()>=maximum){Outcome oldest=outcomes.values().stream().min(Comparator.comparing(Outcome::expiresAt)).orElse(null);if(oldest==null)break;outcomes.remove(oldest.state(),oldest);}
        outcomes.put(outcome.state(),outcome);
    }
    /** Descripción: Ejecuta la operación «expires» correspondiente a la responsabilidad de RemoteAuthOutcomeStore. Entradas: requested: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Instant expires(Instant requested){return Instant.now().plus(properties.getFlowTtl());}
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de RemoteAuthOutcomeStore. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalize(String value){return value==null?"":value.trim();}

    public record Outcome(String state,String profileId,boolean success,RemoteAuthConnectionDescriptor connection,RemoteAuthException.Reason reason,String message,Instant expiresAt){ }
}
