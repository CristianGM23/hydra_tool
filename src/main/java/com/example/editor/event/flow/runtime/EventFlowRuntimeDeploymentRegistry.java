package com.example.editor.event.flow.runtime;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import com.example.editor.event.flow.EventFlowApplicationException;

/** Registro local acotado de deployments. Nunca expulsa un deployment activo. */
final class EventFlowRuntimeDeploymentRegistry {
    private final ConcurrentHashMap<String, EventFlowRuntimeDeployment> values = new ConcurrentHashMap<>();
    private final int maximum;
    /** Descripción: Inicializa una instancia de EventFlowRuntimeDeploymentRegistry con sus dependencias y datos de entrada. Entradas: maximum: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ EventFlowRuntimeDeploymentRegistry(int maximum){this.maximum=Math.max(1, maximum);}
    /** Descripción: Obtiene la información correspondiente a «get» dentro de EventFlowRuntimeDeploymentRegistry. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ EventFlowRuntimeDeployment get(String id){return values.get(id);}
    /** Descripción: Ejecuta la operación «put» correspondiente a la responsabilidad de EventFlowRuntimeDeploymentRegistry. Entradas: id: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ EventFlowRuntimeDeployment put(String id, EventFlowRuntimeDeployment value){
        synchronized(values){ensureCapacityLocked(id); return values.put(id, value);}
    }
    /** Descripción: Elimina o libera el recurso gestionado por «remove» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ EventFlowRuntimeDeployment remove(String id){return values.remove(id);}
    /** Descripción: Ejecuta la operación «compute» correspondiente a la responsabilidad de EventFlowRuntimeDeploymentRegistry. Entradas: id: identificador que delimita el recurso objetivo; factory: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ EventFlowRuntimeDeployment compute(String id, Function<String, EventFlowRuntimeDeployment> factory){
        EventFlowRuntimeDeployment current=values.get(id); if(current!=null){return current;}
        synchronized(values){
            current=values.get(id); if(current!=null){return current;} ensureCapacityLocked(id);
            EventFlowRuntimeDeployment created=factory.apply(id); values.put(id, created); return created;
        }
    }
    /** Descripción: Ejecuta la operación «all» correspondiente a la responsabilidad de EventFlowRuntimeDeploymentRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<EventFlowRuntimeDeployment> all(){return List.copyOf(values.values());}
    /** Descripción: Elimina o libera el recurso gestionado por «clear» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void clear(){values.clear();}
    /** Descripción: Valida las condiciones necesarias para «ensureCapacityLocked» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureCapacityLocked(String id){
        if(values.containsKey(id) || values.size()<maximum){return;}
        List<EventFlowRuntimeDeployment> inactive=values.values().stream().filter(item -> !item.active)
                .sorted(Comparator.comparing(item -> item.updatedAt)).toList();
        for(EventFlowRuntimeDeployment candidate:inactive){if(values.size()<maximum){break;} if(values.remove(candidate.metamodelId,candidate)){candidate.close();}}
        if(values.size()>=maximum){throw new EventFlowApplicationException(EventFlowApplicationException.Reason.PRECONDITION_FAILED,
                "EventFlow runtime deployment limit reached: " + maximum);}
    }
}
