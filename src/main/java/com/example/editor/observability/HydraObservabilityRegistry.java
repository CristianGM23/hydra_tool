package com.example.editor.observability;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/** Fixed-cardinality metrics registry for the major HYDRA operational surfaces. */
@Component
public class HydraObservabilityRegistry {
    public enum Area { IMPORT, RUNTIME, STORAGE, GIT, AUDIT, RENDERING, OTHER }
    private final EnumMap<Area,Metric> metrics=new EnumMap<>(Area.class);
    /** Descripción: Inicializa una instancia de HydraObservabilityRegistry con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public HydraObservabilityRegistry(){for(Area area:Area.values())metrics.put(area,new Metric());}
    /** Descripción: Ejecuta la operación «record» correspondiente a la responsabilidad de Area. Entradas: area: dato de entrada necesario para la operación; durationMillis: dato de entrada necesario para la operación; failure: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void record(Area area,long durationMillis,boolean failure){Metric m=metrics.get(area);m.count.incrementAndGet();m.totalMillis.addAndGet(Math.max(0,durationMillis));if(failure)m.failures.incrementAndGet();}
    /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de Area. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Map<String,Object> snapshot(){Map<String,Object> out=new LinkedHashMap<>();for(var e:metrics.entrySet()){Metric m=e.getValue();long count=m.count.get();out.put(e.getKey().name().toLowerCase(),Map.of("count",count,"failures",m.failures.get(),"totalMillis",m.totalMillis.get(),"averageMillis",count==0?0d:(double)m.totalMillis.get()/count));}out.put("bestEffortFailures",HydraBestEffortDiagnostics.snapshot());return Map.copyOf(out);}
    /** Descripción: Ejecuta la operación «classify» correspondiente a la responsabilidad de Area. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Area classify(String path){String p=path==null?"":path.toLowerCase();if(p.contains("import"))return Area.IMPORT;if(p.contains("runtime")||p.contains("session"))return Area.RUNTIME;if(p.contains("remote-storage")||p.contains("project")||p.contains("registry"))return Area.STORAGE;if(p.contains("git"))return Area.GIT;if(p.contains("audit"))return Area.AUDIT;return Area.OTHER;}
    private static final class Metric{final AtomicLong count=new AtomicLong();final AtomicLong failures=new AtomicLong();final AtomicLong totalMillis=new AtomicLong();}
}
