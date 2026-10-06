package com.example.editor.audit;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Caché LRU temporal y acotada de estadísticas de auditoría. */
final class AuditStatisticsCache {
    private final AuditLogProperties properties;
    private final ReentrantLock lock=new ReentrantLock();
    private final LinkedHashMap<AuditQuery,Cached> values=new LinkedHashMap<>(32,.75f,true);
    /** Descripción: Inicializa una instancia de AuditStatisticsCache con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ AuditStatisticsCache(AuditLogProperties properties){this.properties=properties;}

    /** Descripción: Obtiene la información correspondiente a «get» dentro de AuditStatisticsCache. Entradas: query: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ AuditStatistics get(AuditQuery query){
        long now=System.nanoTime();lock.lock();
        try{
            Cached cached=values.get(query);if(cached==null)return null;
            if((now-cached.createdNanos())/1_000_000L<=properties.getStatisticsCacheMillis())return cached.statistics();
            values.remove(query);return null;
        }finally{lock.unlock();}
    }
    /** Descripción: Ejecuta la operación «put» correspondiente a la responsabilidad de AuditStatisticsCache. Entradas: query: dato de entrada necesario para la operación; statistics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void put(AuditQuery query,AuditStatistics statistics){
        lock.lock();
        try{
            values.put(query,new Cached(statistics,System.nanoTime()));
            while(values.size()>properties.getStatisticsCacheEntries()){
                Iterator<AuditQuery> iterator=values.keySet().iterator();if(!iterator.hasNext())break;iterator.next();iterator.remove();
            }
        }finally{lock.unlock();}
    }
    /** Descripción: Elimina o libera el recurso gestionado por «clear» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void clear(){lock.lock();try{values.clear();}finally{lock.unlock();}}
    private record Cached(AuditStatistics statistics,long createdNanos){}
}
