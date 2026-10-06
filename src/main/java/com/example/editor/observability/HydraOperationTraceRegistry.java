package com.example.editor.observability;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Registro acotado de hitos operativos correlacionados entre HTTP, jobs y procesos. */
@Component
public class HydraOperationTraceRegistry {
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private final int maximumEvents = 2048;

    /** Descripción: Registra un hito operativo con cardinalidad acotada. Entradas: correlationId: correlación; area: área; phase: fase; detail: detalle seguro. Salida: no devuelve valor. */
    public synchronized void record(String correlationId,String area,String phase,String detail){
        events.addLast(new Event(text(correlationId),text(area),text(phase),bounded(detail),Instant.now()));
        while(events.size()>maximumEvents) events.removeFirst();
    }

    /** Descripción: Devuelve los hitos de una correlación en orden temporal. Entradas: correlationId: correlación solicitada. Salida: lista inmutable acotada. */
    public synchronized List<Event> find(String correlationId){
        String id=text(correlationId); List<Event> out=new ArrayList<>();
        for(Event event:events) if(event.correlationId().equals(id)) out.add(event);
        return List.copyOf(out);
    }

    /** Descripción: Devuelve los últimos hitos operativos sin superar el límite solicitado. Entradas: limit: número máximo. Salida: eventos recientes. */
    public synchronized List<Event> recent(int limit){
        int wanted=Math.max(1,Math.min(500,limit)); List<Event> all=new ArrayList<>(events);
        return List.copyOf(all.subList(Math.max(0,all.size()-wanted),all.size()));
    }
    /** Descripción: Normaliza texto opcional. Entradas: value: texto. Salida: cadena no nula. */ private static String text(String value){return value==null?"":value.trim();}
    /** Descripción: Acota y sanea un detalle de diagnóstico. Entradas: value: texto. Salida: detalle seguro. */ private static String bounded(String value){String v=text(value).replace('\n',' ').replace('\r',' ');return v.length()>500?v.substring(0,500)+"…":v;}
    public record Event(String correlationId,String area,String phase,String detail,Instant at){}
}
