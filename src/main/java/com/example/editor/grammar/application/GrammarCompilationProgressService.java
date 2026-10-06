package com.example.editor.grammar.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.runtime.GrammarCompilationStage;
import com.example.editor.grammar.runtime.GrammarCompilationProgress;

/** Ephemeral bounded progress registry. It never persists compilation payloads or generated artifacts. */
@Service
public class GrammarCompilationProgressService {
    private final int maximumEntries;
    private final int maximumDetailCharacters;
    private final LinkedHashMap<String,GrammarCompilationProgress> entries=new LinkedHashMap<>(32,0.75f,true);

    /** Descripción: Inicializa un registro efímero de progreso con retención máxima acotada por configuración. Entradas: properties: configuración de gramáticas. Salida: deja el servicio listo para registrar compilaciones. */
    /** Descripción: Inicializa una instancia de GrammarCompilationProgressService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarCompilationProgressService(GrammarProperties properties){
        maximumEntries=Math.max(Math.max(16,properties.getMaximumCompilationProgressEntries()),Math.max(1,properties.getMaximumConcurrentCompilations()));
        maximumDetailCharacters=properties.getMaximumCompilationProgressDetailCharacters();
    }

    /** Descripción: Inicia una petición de compilación nueva sin permitir reutilizar un requestId activo o retenido y sin expulsar compilaciones activas. Entradas: requestId y grammarId. Salida: progreso inicial RUNNING. */
    public synchronized GrammarCompilationProgress start(String requestId,String grammarId){
        validateIdentity(requestId,grammarId);
        if(entries.containsKey(requestId)) throw duplicate(requestId);
        if(!ensureCapacityForNewEntry())throw new IllegalStateException("Compilation progress capacity is exhausted by active requests");
        return put(new GrammarCompilationProgress(requestId,grammarId,GrammarCompilationStage.FREEZE_DRAFT.name(),"RUNNING",0,GrammarCompilationStage.values().length,"",Instant.now()));
    }

    /** Descripción: Registra un rechazo por falta de capacidad antes de iniciar el pipeline, manteniendo el historial terminal acotado. Entradas: requestId, grammarId y detalle. Salida: progreso FAILED retenido. */
    public synchronized GrammarCompilationProgress reject(String requestId,String grammarId,String detail){
        return recordBeforeStart(requestId,grammarId,"FAILED",detail);
    }

    /** Descripción: Registra una cancelación ocurrida mientras la compilación esperaba capacidad, sin crear una entrada RUNNING artificial. Entradas: requestId, grammarId y detalle. Salida: progreso CANCELLED retenido. */
    public synchronized GrammarCompilationProgress cancelBeforeStart(String requestId,String grammarId,String detail){
        return recordBeforeStart(requestId,grammarId,"CANCELLED",detail);
    }

    /** Descripción: Avanza una compilación únicamente mientras siga RUNNING; etapa y contador nunca retroceden y un terminal nunca puede revivirse. Entradas: requestId, grammarId, etapa, completadas y detalle. Salida: progreso efectivo tras la transición. */
    public synchronized GrammarCompilationProgress stage(String requestId,String grammarId,GrammarCompilationStage stage,int completed,String detail){
        GrammarCompilationProgress current=requireExisting(requestId,grammarId);
        if(terminal(current)) return current;
        GrammarCompilationStage effectiveStage=laterStage(current.stage(),stage);
        int effectiveCompleted=Math.min(GrammarCompilationStage.values().length,Math.max(current.completedStages(),Math.max(0,completed)));
        return put(new GrammarCompilationProgress(requestId,grammarId,effectiveStage.name(),"RUNNING",effectiveCompleted,GrammarCompilationStage.values().length,detail(detail),Instant.now()));
    }

    /** Descripción: Marca la compilación como SUCCESS sólo si aún no había alcanzado un estado terminal. Entradas: requestId, grammarId y detalle. Salida: progreso terminal efectivo. */
    public synchronized GrammarCompilationProgress complete(String requestId,String grammarId,String detail){
        GrammarCompilationProgress current=requireExisting(requestId,grammarId);
        if(terminal(current)) return current;
        return put(new GrammarCompilationProgress(requestId,grammarId,GrammarCompilationStage.PROMOTE_SNAPSHOT.name(),"SUCCESS",GrammarCompilationStage.values().length,GrammarCompilationStage.values().length,detail(detail),Instant.now()));
    }

    /** Descripción: Marca la compilación como FAILED sólo si aún no había alcanzado un estado terminal. Entradas: requestId, grammarId, etapa, completadas y detalle. Salida: progreso terminal efectivo. */
    public synchronized GrammarCompilationProgress fail(String requestId,String grammarId,GrammarCompilationStage stage,int completed,String detail){
        GrammarCompilationProgress current=requireExisting(requestId,grammarId);
        if(terminal(current)) return current;
        GrammarCompilationStage effectiveStage=stage==null?stage(current):laterStage(current.stage(),stage);
        int effectiveCompleted=Math.min(GrammarCompilationStage.values().length,Math.max(Math.max(0,completed),current.completedStages()));
        return put(new GrammarCompilationProgress(requestId,grammarId,effectiveStage.name(),"FAILED",effectiveCompleted,GrammarCompilationStage.values().length,detail(detail),Instant.now()));
    }

    /** Descripción: Marca explícitamente una compilación cancelada sin confundir cancelación con fallo y sin sobrescribir terminales previos. Entradas: requestId, grammarId y detalle. Salida: progreso terminal CANCELLED. */
    public synchronized GrammarCompilationProgress cancel(String requestId,String grammarId,String detail){
        GrammarCompilationProgress current=requireExisting(requestId,grammarId);
        if(terminal(current)) return current;
        return put(new GrammarCompilationProgress(requestId,grammarId,current.stage(),"CANCELLED",current.completedStages(),GrammarCompilationStage.values().length,detail(detail),Instant.now()));
    }

    /** Descripción: Garantiza desde bloques finally que ninguna petición iniciada quede RUNNING después de abandonar la compilación por una excepción no prevista. Entradas: requestId, grammarId y detalle. Salida: progreso terminal existente o FAILED de salvaguarda. */
    public synchronized Optional<GrammarCompilationProgress> ensureTerminal(String requestId,String grammarId,String detail){
        validateIdentity(requestId,grammarId);
        GrammarCompilationProgress current=entries.get(requestId);
        if(current==null)return Optional.empty();
        if(!current.grammarId().equals(grammarId))throw new IllegalStateException("Compilation requestId belongs to another grammar: "+requestId);
        if(terminal(current)) return Optional.of(current);
        return Optional.of(put(new GrammarCompilationProgress(requestId,grammarId,current.stage(),"FAILED",current.completedStages(),GrammarCompilationStage.values().length,detail(detail),Instant.now())));
    }

    /** Descripción: Obtiene el último snapshot retenido para una petición. Entradas: requestId. Salida: progreso opcional. */
    public synchronized Optional<GrammarCompilationProgress> find(String requestId){return Optional.ofNullable(entries.get(requestId));}

    /** Descripción: Devuelve el número de entradas retenidas para observabilidad y pruebas de capacidad. Entradas: ninguna. Salida: tamaño actual acotado. */
    public synchronized int retainedEntries(){return entries.size();}

    /** Descripción: Devuelve cuántas compilaciones retenidas siguen RUNNING para comprobar que el registro no conserva actividad fantasma. Entradas: ninguna. Salida: número de entradas activas. */
    public synchronized int activeEntries(){return (int)entries.values().stream().filter(value->!terminal(value)).count();}

    /** Descripción: Registra un terminal antes de iniciar el pipeline, rechazando colisiones de requestId y expulsando sólo terminales antiguos. Entradas: identidad, estado y detalle. Salida: progreso terminal. */
    /** Descripción: Ejecuta la operación «recordBeforeStart» correspondiente a la responsabilidad de GrammarCompilationProgressService. Entradas: requestId: identificador que delimita el recurso objetivo; grammarId: identificador que delimita el recurso objetivo; status: dato de entrada necesario para la operación; detail: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarCompilationProgress recordBeforeStart(String requestId,String grammarId,String status,String detail){
        validateIdentity(requestId,grammarId);
        if(entries.containsKey(requestId)) throw duplicate(requestId);
        if(!ensureCapacityForNewEntry())return new GrammarCompilationProgress(requestId,grammarId,GrammarCompilationStage.FREEZE_DRAFT.name(),status,0,GrammarCompilationStage.values().length,detail(detail),Instant.now());
        return put(new GrammarCompilationProgress(requestId,grammarId,GrammarCompilationStage.FREEZE_DRAFT.name(),status,0,GrammarCompilationStage.values().length,detail(detail),Instant.now()));
    }

    /** Descripción: Garantiza capacidad para una entrada nueva eliminando únicamente el terminal menos recientemente usado; nunca recicla trabajo RUNNING. Entradas: ninguna. Salida: deja espacio o comunica saturación explícita. */
    /** Descripción: Valida las condiciones necesarias para «ensureCapacityForNewEntry» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean ensureCapacityForNewEntry(){
        if(entries.size()<maximumEntries)return true;
        String victim=null;
        for(Map.Entry<String,GrammarCompilationProgress> entry:entries.entrySet()){if(terminal(entry.getValue())){victim=entry.getKey();break;}}
        if(victim!=null){entries.remove(victim);return true;}
        return false;
    }

    /** Descripción: Obtiene una entrada existente y verifica que el requestId no se reutilice entre gramáticas. Entradas: requestId y grammarId. Salida: progreso actual. */
    /** Descripción: Valida las condiciones necesarias para «requireExisting» y rechaza entradas no válidas. Entradas: requestId: identificador que delimita el recurso objetivo; grammarId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarCompilationProgress requireExisting(String requestId,String grammarId){
        validateIdentity(requestId,grammarId);
        GrammarCompilationProgress current=entries.get(requestId);
        if(current==null)throw new IllegalStateException("Compilation progress was not started: "+requestId);
        if(!current.grammarId().equals(grammarId))throw new IllegalStateException("Compilation requestId belongs to another grammar: "+requestId);
        return current;
    }

    /** Descripción: Comprueba la identidad mínima de una petición de progreso. Entradas: requestId y grammarId. Salida: no devuelve valor; rechaza identidades vacías. */
    /** Descripción: Valida las condiciones necesarias para «validateIdentity» y rechaza entradas no válidas. Entradas: requestId: identificador que delimita el recurso objetivo; grammarId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void validateIdentity(String requestId,String grammarId){
        if(requestId==null||requestId.isBlank())throw new IllegalArgumentException("Compilation requestId is required");
        if(grammarId==null||grammarId.isBlank())throw new IllegalArgumentException("Compilation grammarId is required");
    }

    /** Descripción: Construye el error estable utilizado para requestId duplicados. Entradas: requestId. Salida: excepción de colisión. */
    /** Descripción: Ejecuta la operación «duplicate» correspondiente a la responsabilidad de GrammarCompilationProgressService. Entradas: requestId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static IllegalStateException duplicate(String requestId){return new IllegalStateException("Compilation requestId already exists: "+requestId);}

    /** Descripción: Comprueba si un snapshot de progreso ya alcanzó un estado terminal. Entradas: value: snapshot opcional. Salida: true para SUCCESS, FAILED o CANCELLED. */
    /** Descripción: Ejecuta la operación «terminal» correspondiente a la responsabilidad de GrammarCompilationProgressService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean terminal(GrammarCompilationProgress value){return value!=null&&!"RUNNING".equals(value.status());}

    /** Descripción: Interpreta la etapa retenida o usa FREEZE_DRAFT como fallback defensivo. Entradas: value: progreso. Salida: etapa válida. */
    /** Descripción: Ejecuta la operación «stage» correspondiente a la responsabilidad de GrammarCompilationProgressService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static GrammarCompilationStage stage(GrammarCompilationProgress value){try{return GrammarCompilationStage.valueOf(value.stage());}catch(RuntimeException exception){return GrammarCompilationStage.FREEZE_DRAFT;}}

    /** Descripción: Conserva la etapa más avanzada entre el snapshot actual y una transición candidata. Entradas: currentStage y candidate. Salida: etapa monotónica. */
    /** Descripción: Ejecuta la operación «laterStage» correspondiente a la responsabilidad de GrammarCompilationProgressService. Entradas: currentStage: dato de entrada necesario para la operación; candidate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static GrammarCompilationStage laterStage(String currentStage,GrammarCompilationStage candidate){GrammarCompilationStage current;try{current=GrammarCompilationStage.valueOf(currentStage);}catch(RuntimeException exception){current=GrammarCompilationStage.FREEZE_DRAFT;}return candidate==null||candidate.ordinal()<current.ordinal()?current:candidate;}

    /** Descripción: Acota el texto de progreso para impedir payloads de diagnóstico sin límite. Entradas: value. Salida: detalle normalizado y truncado. */
    /** Descripción: Ejecuta la operación «detail» correspondiente a la responsabilidad de GrammarCompilationProgressService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String detail(String value){String safe=value==null?"":value.trim();return safe.length()<=maximumDetailCharacters?safe:safe.substring(0,maximumDetailCharacters);}

    /** Descripción: Sustituye o inserta un snapshot ya validado sin ejecutar política de expulsión implícita. Entradas: value: progreso. Salida: el mismo snapshot registrado. */
    /** Descripción: Ejecuta la operación «put» correspondiente a la responsabilidad de GrammarCompilationProgressService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarCompilationProgress put(GrammarCompilationProgress value){entries.put(value.requestId(),value);return value;}
}
