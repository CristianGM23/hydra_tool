package com.example.editor.testing.fault;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
/** Configuración desactivada por defecto para inyectar fallos reproducibles en pruebas de resiliencia. */
@Component
@ConfigurationProperties(prefix="hydra.fault-injection")
public class HydraFaultInjectionProperties {
    private boolean enabled=false; private Set<String> activePoints=new LinkedHashSet<>();
    /** Descripción: Indica si la inyección de fallos está globalmente habilitada. Entradas: ninguna. Salida: estado. */ public boolean isEnabled(){return enabled;}
    /** Descripción: Activa o desactiva la inyección de fallos. Entradas: value: estado. Salida: actualiza configuración. */ public void setEnabled(boolean value){enabled=value;}
    /** Descripción: Obtiene los puntos activos. Entradas: ninguna. Salida: conjunto inmutable. */ public Set<String> getActivePoints(){return Set.copyOf(activePoints);}
    /** Descripción: Configura puntos de fallo permitidos por nombre. Entradas: values: nombres. Salida: normaliza el conjunto. */ public void setActivePoints(Set<String> values){activePoints=new LinkedHashSet<>();if(values!=null)for(String v:values)if(v!=null&&!v.isBlank())activePoints.add(v.trim().toUpperCase());}
}
