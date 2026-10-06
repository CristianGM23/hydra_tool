package com.example.editor.event.flow.runtime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.editor.event.CanonicalEvent;
/** Estado CEP durable que permite reanudar ventanas, joins, patrones, secuencias y deduplicación. */
public record EventFlowRuntimeStateDocument(
        Map<String, Map<String, Instant>> deduplication,
        Map<String, List<CanonicalEvent>> windows,
        Map<String, Long> aggregates,
        Map<String, Double> sums,
        Map<String, Map<String, CanonicalEvent>> joins,
        Map<String, Integer> sequences,
        Map<String, Set<String>> patterns,
        List<FlowAbsenceContinuation> absenceWatches) {
    public EventFlowRuntimeStateDocument {
        deduplication = immutableNested(deduplication);
        windows = immutableLists(windows);
        aggregates = copy(aggregates); sums = copy(sums); joins = immutableNested(joins);
        sequences = copy(sequences); patterns = immutableSets(patterns);
        absenceWatches = absenceWatches == null ? List.of() : List.copyOf(absenceWatches);
    }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de EventFlowRuntimeStateDocument. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static EventFlowRuntimeStateDocument empty() { return new EventFlowRuntimeStateDocument(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),List.of()); }
    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de EventFlowRuntimeStateDocument. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static <K,V> Map<K,V> copy(Map<K,V> source) { return source == null || source.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source)); }
    /** Descripción: Ejecuta la operación «immutableNested» correspondiente a la responsabilidad de EventFlowRuntimeStateDocument. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static <K,A,B> Map<K,Map<A,B>> immutableNested(Map<K,? extends Map<A,B>> source) {
        if(source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<K,Map<A,B>> result=new LinkedHashMap<>(); source.forEach((key,value)->result.put(key,copy(value))); return Collections.unmodifiableMap(result);
    }
    /** Descripción: Ejecuta la operación «immutableLists» correspondiente a la responsabilidad de EventFlowRuntimeStateDocument. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static <K,V> Map<K,List<V>> immutableLists(Map<K,? extends java.util.Collection<V>> source) {
        if(source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<K,List<V>> result=new LinkedHashMap<>(); source.forEach((key,value)->result.put(key,value==null?List.of():List.copyOf(value))); return Collections.unmodifiableMap(result);
    }
    /** Descripción: Ejecuta la operación «immutableSets» correspondiente a la responsabilidad de EventFlowRuntimeStateDocument. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static <K,V> Map<K,Set<V>> immutableSets(Map<K,? extends Set<V>> source) {
        if(source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<K,Set<V>> result=new LinkedHashMap<>(); source.forEach((key,value)->result.put(key,value==null?Set.of():Collections.unmodifiableSet(new LinkedHashSet<>(value)))); return Collections.unmodifiableMap(result);
    }
}
