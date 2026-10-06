package com.example.editor.testing.fault;
import org.springframework.stereotype.Service;
/** Punto único y explícito para fault injection; permanece inerte salvo habilitación deliberada. */
@Service
public class HydraFaultInjectionService {
    private final HydraFaultInjectionProperties properties;
    /** Descripción: Inicializa el servicio con configuración segura por defecto. Entradas: properties: configuración. Salida: instancia lista. */ public HydraFaultInjectionService(HydraFaultInjectionProperties properties){this.properties=properties;}
    /** Descripción: Lanza un fallo sintético solo si el punto está habilitado explícitamente. Entradas: point: identificador estable. Salida: no devuelve valor. */ public void check(Point point){if(point!=null&&properties.isEnabled()&&properties.getActivePoints().contains(point.name()))throw new HydraInjectedFaultException(point);}
    public enum Point { ARTIFACT_TRANSACTION_AFTER_PREPARE, PROJECT_PORTABLE_EXPORT, PROJECT_PORTABLE_IMPORT, EXTERNAL_PROCESS_BEFORE_START }
    public static final class HydraInjectedFaultException extends RuntimeException { public HydraInjectedFaultException(Point point){super("Injected HYDRA fault: "+point.name());} }
}
