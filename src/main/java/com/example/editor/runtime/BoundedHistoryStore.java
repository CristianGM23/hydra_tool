package com.example.editor.runtime;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Historial LRU acotado por número de recursos y profundidad por recurso. */
public final class BoundedHistoryStore<T> {
    private final int maximumKeys; private final int maximumDepth;
    private final LinkedHashMap<String, ArrayDeque<T>> values = new LinkedHashMap<>(16, 0.75f, true);
    /** Descripción: Inicializa una instancia de BoundedHistoryStore con sus dependencias y datos de entrada. Entradas: maximumKeys: dato de entrada necesario para la operación; maximumDepth: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public BoundedHistoryStore(int maximumKeys, int maximumDepth){this.maximumKeys=Math.max(1, maximumKeys); this.maximumDepth=Math.max(1, maximumDepth);}
    public synchronized void push(String key, T value){
        ArrayDeque<T> stack = values.computeIfAbsent(requireKey(key), ignored -> new ArrayDeque<>()); stack.addLast(value);
        while(stack.size()>maximumDepth){stack.removeFirst();}
        while(values.size()>maximumKeys){String eldest=values.keySet().iterator().next(); values.remove(eldest);}
    }
    public synchronized Optional<T> pop(String key){
        String normalized=requireKey(key); ArrayDeque<T> stack=values.get(normalized); T value=stack==null||stack.isEmpty()?null:stack.removeLast();
        if(stack!=null && stack.isEmpty()){values.remove(normalized);} return Optional.ofNullable(value);
    }
    public synchronized boolean has(String key){ArrayDeque<T> stack=values.get(requireKey(key)); return stack!=null&&!stack.isEmpty();}
    public synchronized void clear(String key){values.remove(requireKey(key));}
    public synchronized int trackedKeys(){return values.size();}
    /** Descripción: Valida las condiciones necesarias para «requireKey» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String requireKey(String value){if(value==null||value.isBlank()){throw new IllegalArgumentException("history key is required");} return value;}
}
