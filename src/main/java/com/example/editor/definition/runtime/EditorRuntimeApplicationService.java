package com.example.editor.definition.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionLoadResult;

/** Mantiene planes compilados en una caché LRU acotada por el hash/ETag de su fuente. */
@Service
public class EditorRuntimeApplicationService {
    private final EditorDefinitionApplicationService definitions;
    private final EditorDefinitionCompiler compiler;
    private final EditorRuntimeProperties properties;
    private final Map<CacheKey, ExecutableEditorPlan> cache = new LinkedHashMap<>(16, 0.75F, true);
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong compilations = new AtomicLong();
    private final AtomicLong compilationNanos = new AtomicLong();

    /** Descripción: Inicializa una instancia de EditorRuntimeApplicationService con sus dependencias y datos de entrada. Entradas: definitions: dato de entrada necesario para la operación; compiler: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorRuntimeApplicationService(EditorDefinitionApplicationService definitions,
            EditorDefinitionCompiler compiler) {
        this(definitions, compiler, new EditorRuntimeProperties());
    }

    @Autowired
    /** Descripción: Inicializa una instancia de EditorRuntimeApplicationService con sus dependencias y datos de entrada. Entradas: definitions: dato de entrada necesario para la operación; compiler: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public EditorRuntimeApplicationService(EditorDefinitionApplicationService definitions,
            EditorDefinitionCompiler compiler, EditorRuntimeProperties properties) {
        this.definitions = definitions;
        this.compiler = compiler;
        this.properties = properties;
    }

    /** Descripción: Ejecuta la operación «compile» correspondiente a la responsabilidad de EditorRuntimeApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo; force: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EditorDefinitionCompilationResult compile(String metamodelId, String editorId,
            boolean force) {
        EditorDefinitionLoadResult loaded = definitions.find(metamodelId, editorId);
        CacheKey key = new CacheKey(metamodelId, editorId, normalizedHash(loaded.etag()));
        if(!force) {
            synchronized(cache) {
                ExecutableEditorPlan cached = cache.get(key);
                if(cached != null) {
                    hits.incrementAndGet();
                    return new EditorDefinitionCompilationResult(cached, loaded.checkReport(), true);
                }
            }
        }
        misses.incrementAndGet();
        long started = System.nanoTime();
        ExecutableEditorPlan plan = compiler.compile(loaded.editorDefinition(), loaded.etag(),
                loaded.checkReport());
        compilationNanos.addAndGet(System.nanoTime() - started);
        compilations.incrementAndGet();
        synchronized(cache) {
            cache.keySet().removeIf(existing -> existing.metamodelId().equals(metamodelId)
                    && existing.editorId().equals(editorId) && !existing.equals(key));
            cache.put(key, plan);
            while(cache.size() > properties.getMaximumCachedPlans()) {
                CacheKey eldest = cache.keySet().iterator().next();
                cache.remove(eldest);
            }
        }
        return new EditorDefinitionCompilationResult(plan, loaded.checkReport(), false);
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de EditorRuntimeApplicationService. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EditorDefinitionCompilationResult find(String metamodelId, String editorId) {
        return compile(metamodelId, editorId, false);
    }

    /** Descripción: Elimina o libera el recurso gestionado por «invalidate» de forma controlada. Entradas: metamodelId: identificador que delimita el recurso objetivo; editorId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void invalidate(String metamodelId, String editorId) {
        synchronized(cache) {
            cache.keySet().removeIf(key -> key.metamodelId().equals(metamodelId)
                    && key.editorId().equals(editorId));
        }
    }

    /** Descripción: Ejecuta la operación «cacheSize» correspondiente a la responsabilidad de EditorRuntimeApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int cacheSize() {
        synchronized(cache) { return cache.size(); }
    }

    /** Descripción: Ejecuta la operación «metrics» correspondiente a la responsabilidad de EditorRuntimeApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public EditorRuntimeCacheMetrics metrics() {
        return new EditorRuntimeCacheMetrics(cacheSize(), properties.getMaximumCachedPlans(),
                hits.get(), misses.get(), compilations.get(), compilationNanos.get());
    }

    /** Descripción: Ejecuta la operación «normalizedHash» correspondiente a la responsabilidad de EditorRuntimeApplicationService. Entradas: etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizedHash(String etag) {
        return etag == null ? "" : etag.replace("\"", "").trim().toLowerCase();
    }

    private record CacheKey(String metamodelId, String editorId, String sourceHash) { }
}
