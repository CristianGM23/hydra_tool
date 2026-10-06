package com.example.editor.grammar.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import com.example.editor.concurrent.BoundedPermitPool;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.diagnostic.GrammarDiagnostic;
import com.example.editor.grammar.diagnostic.GrammarDiagnosticCatalog;
import com.example.editor.grammar.provider.GrammarProvider;
import com.example.editor.grammar.provider.GrammarProviderRegistry;
import com.example.editor.grammar.runtime.GrammarProviderHealthService;

/**
 * Coordinates bounded parallel analysis, cancellation, timeout, stale-response suppression, cache and circuit breaking.
 * The selected technology provider can degrade while HYDRA's neutral parser continues to provide basic diagnostics.
 */
@Service
public class GrammarAnalysisCoordinator implements AutoCloseable {
    private final GrammarToolchainProfileService profiles;
    private final GrammarProviderRegistry providers;
    private final GrammarAnalysisCache cache;
    private final GrammarProviderHealthService health;
    private final GrammarProperties properties;
    private final GrammarSourceAnalyzer neutralFallback;
    private final BoundedPermitPool permits;
    private final ExecutorService executor;
    private final ConcurrentMap<String, Future<GrammarAnalysisResult>> running = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> latestRevision = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> revisionInsertionOrder = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    /** Descripción: Inicializa una instancia de GrammarAnalysisCoordinator con sus dependencias y datos de entrada. Entradas: profiles: archivo o contenido que debe procesarse; providers: dato de entrada necesario para la operación; cache: dato de entrada necesario para la operación; health: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarAnalysisCoordinator(GrammarToolchainProfileService profiles, GrammarProviderRegistry providers,
            GrammarAnalysisCache cache, GrammarProviderHealthService health, GrammarProperties properties) {
        this.profiles=profiles; this.providers=providers; this.cache=cache; this.health=health; this.properties=properties;
        this.neutralFallback=new GrammarSourceAnalyzer(properties);
        int workers=properties.getMaximumConcurrentAnalyses();
        this.permits=new BoundedPermitPool(workers);
        BlockingQueue<Runnable> queue=properties.getMaximumQueuedAnalyses()==0
                ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(properties.getMaximumQueuedAnalyses());
        ThreadFactory factory=runnable -> {
            Thread thread=new Thread(runnable,"hydra-grammar-analysis"); thread.setDaemon(true); return thread;
        };
        this.executor=new ThreadPoolExecutor(workers,workers,0L,TimeUnit.MILLISECONDS,queue,factory,new ThreadPoolExecutor.AbortPolicy());
    }

    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de GrammarAnalysisCoordinator. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarAnalysisResult analyze(GrammarDefinitionModel grammar, GrammarAnalysisRequest request) {
        if (grammar == null) throw new IllegalArgumentException("Grammar is required");
        GrammarAnalysisRequest effective = normalize(grammar, request);
        trackRevision(grammar.id(), effective.draftRevision());
        GrammarToolchainProfile profile=profiles.require(effective.toolchainProfileId(),grammar.technology());
        GrammarProvider provider=providers.require(grammar.technology());
        String instanceId=provider.descriptor().id()+":"+profile.id();
        if(closed.get()) return cancelled(grammar,effective,instanceId);
        GrammarAnalysisCache.Key cacheKey=new GrammarAnalysisCache.Key(grammar.id(),provider.descriptor().id(),provider.descriptor().version(),
                GrammarHashing.toolchain(profile),effective.sourceHash(),effective.mode(),GrammarHashing.settings(effective.settings()));
        Optional<GrammarAnalysisResult> cached=cache.get(cacheKey);
        if(cached.isPresent()) return envelope(cached.get(),effective,instanceId,health.health(provider.descriptor().id(),instanceId,effective.sourceHash()),true);

        if(!health.allow(provider.descriptor().id(),effective.sourceHash())) {
            ProviderHealth current=health.health(provider.descriptor().id(),instanceId,effective.sourceHash());
            GrammarAnalysisResult fallback=neutralFallback.analyze(grammar,effective).withEnvelope(effective.requestId(),instanceId,isStale(effective),current,Map.of("circuitBreaker","OPEN","fallback","HYDRA_NEUTRAL"));
            return truncate(fallback);
        }

        BoundedPermitPool.Permit permit=null;
        Future<GrammarAnalysisResult> activeFuture=null;
        try {
            permit=permits.tryAcquire(Duration.ofMillis(properties.getPermitAcquireTimeoutMillis()));
            if(permit==null) return capacityResult(grammar,effective,instanceId);
            Callable<GrammarAnalysisResult> task=()->provider.analyze(grammar,effective);
            FutureTask<GrammarAnalysisResult> future=new FutureTask<>(task);
            activeFuture=future;
            Future<GrammarAnalysisResult> previous=running.putIfAbsent(effective.requestId(),future);
            if(previous!=null) return cancelled(grammar,effective,instanceId,"DUPLICATE_REQUEST_ID");
            try {
                if(closed.get()) {
                    running.remove(effective.requestId(),future);
                    future.cancel(true);
                    return cancelled(grammar,effective,instanceId,"COORDINATOR_CLOSED");
                }
                executor.execute(future);
            } catch(RejectedExecutionException exception) {
                running.remove(effective.requestId(),future);
                future.cancel(true);
                if(closed.get()) return cancelled(grammar,effective,instanceId,"COORDINATOR_CLOSED");
                return capacityResult(grammar,effective,instanceId,"EXECUTOR_SATURATED");
            }
            GrammarAnalysisResult result;
            try {
                result=future.get(profile.analysisTimeoutMs(),TimeUnit.MILLISECONDS);
                ProviderHealth current=health.success(provider.descriptor().id(),instanceId,effective.sourceHash());
                result=envelope(result,effective,instanceId,current,false);
            } catch(TimeoutException exception) {
                future.cancel(true);
                ProviderHealth current=health.failure(provider.descriptor().id(),instanceId,effective.sourceHash(),"Analysis exceeded "+profile.analysisTimeoutMs()+" ms",List.of("ANALYZE"));
                result=fallback(grammar,effective,instanceId,current,"TIMEOUT");
            } catch(CancellationException exception) {
                return cancelled(grammar,effective,instanceId,"CANCELLED");
            } catch(ExecutionException exception) {
                Throwable cause=exception.getCause()==null?exception:exception.getCause();
                ProviderHealth current=health.failure(provider.descriptor().id(),instanceId,effective.sourceHash(),cause.getClass().getSimpleName()+": "+safe(cause.getMessage()),List.of("ANALYZE"));
                result=fallback(grammar,effective,instanceId,current,"PROVIDER_FAILURE");
            } finally {
                running.remove(effective.requestId());
            }
            result=truncate(result);
            if(!result.stale() && !result.hasErrors()) cache.put(cacheKey,result);
            else if(!result.stale() && result.metadata().getOrDefault("fallback","").isBlank()) cache.put(cacheKey,result);
            return result;
        } catch(InterruptedException exception) {
            if(activeFuture!=null) activeFuture.cancel(true);
            running.remove(effective.requestId(),activeFuture);
            Thread.currentThread().interrupt();
            return cancelled(grammar,effective,instanceId,"CALLER_INTERRUPTED");
        } finally {
            if(permit!=null) permit.close();
        }
    }

    /** Descripción: Evalúa la condición «cancel» sin modificar el estado externo. Entradas: requestId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean cancel(String requestId) {
        Future<GrammarAnalysisResult> future=running.remove(requestId==null?"":requestId.trim());
        return future!=null && future.cancel(true);
    }
    /** Descripción: Ejecuta la operación «cacheStatistics» correspondiente a la responsabilidad de GrammarAnalysisCoordinator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarAnalysisCache.Statistics cacheStatistics(){return cache.statistics();}
    /** Descripción: Ejecuta la operación «capacityStatus» correspondiente a la responsabilidad de GrammarAnalysisCoordinator. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public BoundedPermitPool.Snapshot capacityStatus(){return permits.snapshot();}

    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de GrammarAnalysisCoordinator. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarAnalysisRequest normalize(GrammarDefinitionModel grammar, GrammarAnalysisRequest request) {
        List<GrammarSourceDocument> docs=request==null||request.documents().isEmpty()?grammar.documents():request.documents();
        String profile=request==null||request.toolchainProfileId().isBlank()?grammar.toolchainProfileId():request.toolchainProfileId();
        return new GrammarAnalysisRequest(request==null?"":request.requestId(),grammar.id(),request==null?"":request.documentId(),
                request==null?0:request.draftRevision(),request==null?GrammarAnalysisMode.FAST:request.mode(),
                request==null?List.of():request.changedDocuments(),docs,profile,GrammarHashing.sources(docs),request==null?Map.of():request.settings());
    }

    /** Descripción: Ejecuta la operación «fallback» correspondiente a la responsabilidad de GrammarAnalysisCoordinator. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; instanceId: identificador que delimita el recurso objetivo; current: dato de entrada necesario para la operación; reason: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarAnalysisResult fallback(GrammarDefinitionModel grammar, GrammarAnalysisRequest request, String instanceId, ProviderHealth current, String reason) {
        GrammarAnalysisResult neutral=neutralFallback.analyze(grammar,request);
        return neutral.withEnvelope(request.requestId(),instanceId,isStale(request),current,Map.of("fallback","HYDRA_NEUTRAL","degradedReason",reason));
    }
    /** Descripción: Ejecuta la operación «envelope» correspondiente a la responsabilidad de GrammarAnalysisCoordinator. Entradas: result: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; instanceId: identificador que delimita el recurso objetivo; current: dato de entrada necesario para la operación; cacheHit: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarAnalysisResult envelope(GrammarAnalysisResult result, GrammarAnalysisRequest request, String instanceId, ProviderHealth current, boolean cacheHit) {
        return result.withEnvelope(request.requestId(),instanceId,isStale(request),current,Map.of("cacheHit",Boolean.toString(cacheHit),"requestSourceHash",request.sourceHash()));
    }
    /** Descripción: Evalúa la condición «isStale» sin modificar el estado externo. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isStale(GrammarAnalysisRequest request){AtomicLong latest=latestRevision.get(request.grammarId());return latest!=null&&request.draftRevision()<latest.get();}
    /** Descripción: Ejecuta la operación «trackRevision» correspondiente a la responsabilidad de GrammarAnalysisCoordinator. Entradas: grammarId: identificador que delimita el recurso objetivo; revision: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void trackRevision(String grammarId, long revision) {
        AtomicLong created = new AtomicLong(revision);
        AtomicLong state = latestRevision.putIfAbsent(grammarId, created);
        if (state == null) { state = created; revisionInsertionOrder.offer(grammarId); }
        else state.accumulateAndGet(revision, Math::max);
        int maximum = properties.getMaximumTrackedGrammarRevisions();
        while (latestRevision.size() > maximum) {
            String victim = revisionInsertionOrder.poll();
            if (victim == null) break;
            if (victim.equals(grammarId)) { revisionInsertionOrder.offer(victim); continue; }
            latestRevision.remove(victim);
        }
    }
    /** Descripción: Ejecuta la operación «truncate» correspondiente a la responsabilidad de GrammarAnalysisCoordinator. Entradas: result: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarAnalysisResult truncate(GrammarAnalysisResult result){
        if(result.diagnostics().size()<=properties.getMaximumDiagnostics()) return result;
        int retained=Math.max(0,properties.getMaximumDiagnostics()-1);
        List<GrammarDiagnostic> diagnostics=new ArrayList<>(result.diagnostics().subList(0,retained));
        diagnostics.add(GrammarDiagnosticCatalog.simple("GRAMMAR_DIAGNOSTIC_LIMIT",GrammarSeverity.WARNING,"Se ha alcanzado el límite de diagnósticos",
                "Se han retenido los primeros "+retained+" diagnósticos y una evidencia explícita de truncado.","El borrador produce más incidencias de las configuradas para una respuesta.",
                "La edición continúa disponible, pero la lista se ha acotado para proteger el runtime.","Resolver primero los errores iniciales o aumentar el límite de forma controlada.","",new TextRange(null,null),Map.of("total",Integer.toString(result.diagnostics().size()),"retained",Integer.toString(retained))));
        return new GrammarAnalysisResult(result.requestId(),result.draftRevision(),result.sourceHash(),result.mode(),result.providerInstanceId(),result.stale(),diagnostics,result.symbols(),result.semanticCandidate(),result.providerHealth(),result.codeActions(),result.metadata());
    }
    /** Descripción: Ejecuta la operación «capacityResult» correspondiente a la responsabilidad de GrammarAnalysisCoordinator. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; instanceId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarAnalysisResult capacityResult(GrammarDefinitionModel grammar, GrammarAnalysisRequest request, String instanceId){
        return capacityResult(grammar,request,instanceId,"CAPACITY");
    }
    /** Devuelve degradación controlada cuando el límite de ejecución o cola evita aceptar trabajo adicional. */ /** Descripción: Ejecuta la operación «capacityResult» correspondiente a la responsabilidad de GrammarAnalysisCoordinator. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; instanceId: identificador que delimita el recurso objetivo; reason: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarAnalysisResult capacityResult(GrammarDefinitionModel grammar, GrammarAnalysisRequest request, String instanceId, String reason){
        String effectiveReason=reason==null||reason.isBlank()?"CAPACITY":reason.trim();
        ProviderHealth current=new ProviderHealth("DEGRADED",false,instanceId,0,"Analysis capacity is busy",List.of("ANALYZE"),java.time.Instant.now(),Map.of("capacityReason",effectiveReason));
        GrammarAnalysisResult neutral=neutralFallback.analyze(grammar,request);
        return truncate(neutral.withEnvelope(request.requestId(),instanceId,isStale(request),current,Map.of("fallback","HYDRA_NEUTRAL","degradedReason",effectiveReason)));
    }
    /** Construye una respuesta terminal de cancelación conservando una causa observable y estable. */ /** Descripción: Evalúa la condición «cancelled» sin modificar el estado externo. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; instanceId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private GrammarAnalysisResult cancelled(GrammarDefinitionModel grammar, GrammarAnalysisRequest request, String instanceId){
        return cancelled(grammar,request,instanceId,"CANCELLED");
    }
    /** Construye una respuesta terminal de cancelación con una causa explícita para diagnóstico y tests. */ /** Descripción: Evalúa la condición «cancelled» sin modificar el estado externo. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación; instanceId: identificador que delimita el recurso objetivo; reason: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private GrammarAnalysisResult cancelled(GrammarDefinitionModel grammar, GrammarAnalysisRequest request, String instanceId, String reason){
        String effectiveReason=reason==null||reason.isBlank()?"CANCELLED":reason.trim();
        ProviderHealth current=new ProviderHealth("DEGRADED",false,instanceId,0,"Analysis was cancelled",List.of(),java.time.Instant.now(),Map.of("cancelReason",effectiveReason));
        return new GrammarAnalysisResult(request.requestId(),request.draftRevision(),request.sourceHash(),request.mode(),instanceId,true,List.of(),List.of(),grammar.semanticBindings(),current,List.of(),Map.of("cancelled","true","cancelReason",effectiveReason));
    }
    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de GrammarAnalysisCoordinator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value){return value==null?"":value.replace('\n',' ').replace('\r',' ');}
    /** Descripción: Elimina o libera el recurso gestionado por «close» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void close(){
        if(!closed.compareAndSet(false,true)) return;
        for(Future<GrammarAnalysisResult> future : List.copyOf(running.values())) future.cancel(true);
        running.clear();
        executor.shutdownNow();
        try {
            if(!executor.awaitTermination(properties.getAnalysisShutdownWaitMillis(),TimeUnit.MILLISECONDS)) executor.shutdownNow();
        } catch(InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
