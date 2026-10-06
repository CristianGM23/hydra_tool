package com.example.editor.definition.runtime.session;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Copias de trabajo con límite duro de sesiones para impedir que modelos completos
 * no guardados crezcan sin cota en heap. Nunca expulsa una copia silenciosamente.
 */
@Component
public class EditorSessionWorkingCopyStore {
    private final EditorSessionProperties properties;
    private final ConcurrentHashMap<String,PendingEditorSession> copies=new ConcurrentHashMap<>();
    /** Descripción: Inicializa una instancia de EditorSessionWorkingCopyStore con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionWorkingCopyStore(EditorSessionProperties properties){this.properties=properties;}

    /** Descripción: Obtiene la información correspondiente a «find» dentro de CapacityStatus. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public PendingEditorSession find(String sessionId){return copies.get(sessionId);}
    /** Descripción: Evalúa la condición «contains» sin modificar el estado externo. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean contains(String sessionId){return copies.containsKey(sessionId);}

    /** Descripción: Ejecuta la operación «put» correspondiente a la responsabilidad de CapacityStatus. Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void put(PendingEditorSession value) {
        if(value==null) return;
        synchronized(copies) {
            if(!copies.containsKey(value.document().id())&&copies.size()>=properties.getMaximumWorkingCopiesInMemory()) {
                throw new EditorSessionApplicationException(EditorSessionApplicationException.Reason.CONFLICT,
                        "Too many unsaved editor sessions are open. Save or discard a working copy before editing another session.");
            }
            copies.put(value.document().id(),value);
        }
    }

    /** Descripción: Elimina o libera el recurso gestionado por «remove» de forma controlada. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public PendingEditorSession remove(String sessionId){return copies.remove(sessionId);}
    /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de CapacityStatus. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int size(){return copies.size();}
    /** Descripción: Ejecuta la operación «capacityStatus» correspondiente a la responsabilidad de CapacityStatus. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public CapacityStatus capacityStatus(){return new CapacityStatus(copies.size(),properties.getMaximumWorkingCopiesInMemory());}
    public record CapacityStatus(int current,int maximum){public boolean saturated(){return current>=maximum;}}
}
