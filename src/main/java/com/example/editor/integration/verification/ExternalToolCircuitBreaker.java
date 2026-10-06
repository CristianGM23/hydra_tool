package com.example.editor.integration.verification;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Circuit breaker local por ejecutable para evitar repetir procesos externos que fallan de forma sostenida. */
@Component
public class ExternalToolCircuitBreaker {
    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(45);
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final int maximumEntries;
    /** Descripción: Inicializa una instancia de ExternalToolCircuitBreaker con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalToolCircuitBreaker(){this(64);}
    /** Descripción: Ejecuta la operación «ExternalToolCircuitBreaker» correspondiente a la responsabilidad de State. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Autowired public ExternalToolCircuitBreaker(TechnologyProjectValidationProperties properties){this(properties==null?64:properties.getMaximumCircuitBreakerEntries());}
    /** Descripción: Inicializa una instancia de ExternalToolCircuitBreaker con sus dependencias y datos de entrada. Entradas: maximumEntries: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ ExternalToolCircuitBreaker(int maximumEntries){this.maximumEntries=Math.max(1,maximumEntries);}

    /** Descripción: Ejecuta la operación «allow» correspondiente a la responsabilidad de State. Entradas: executable: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean allow(String executable){
        State state = states.get(executable);
        if(state == null){return true;}
        if(state.failures < FAILURE_THRESHOLD){return true;}
        if(state.openedAt != null && state.openedAt.plus(OPEN_DURATION).isBefore(Instant.now())){
            states.remove(executable);
            return true;
        }
        return false;
    }

    /** Descripción: Ejecuta la operación «success» correspondiente a la responsabilidad de State. Entradas: executable: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void success(String executable){states.remove(executable);}

    /** Descripción: Ejecuta la operación «failure» correspondiente a la responsabilidad de State. Entradas: executable: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void failure(String executable){
        synchronized(states){
            if(!states.containsKey(executable) && states.size() >= maximumEntries) {
                states.entrySet().stream().min(Comparator.comparing(entry -> entry.getValue().lastTouched))
                        .ifPresent(entry -> states.remove(entry.getKey(),entry.getValue()));
            }
            states.compute(executable, (key, current) -> {
                int failures=current==null?1:current.failures+1; Instant now=Instant.now();
                return new State(failures,failures>=FAILURE_THRESHOLD?now:null,now);
            });
        }
    }

    /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de State. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int size(){return states.size();}
    /** Descripción: Ejecuta la operación «maximumEntries» correspondiente a la responsabilidad de State. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int maximumEntries(){return maximumEntries;}
    private record State(int failures, Instant openedAt, Instant lastTouched){ }
}
