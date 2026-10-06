package com.example.editor.event.flow;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuración de persistencia y límites del módulo de flujos. */
@Component
@ConfigurationProperties(prefix = "dsl.events.flows")
public class EventFlowProperties {
    private boolean enabled = true;
    private Path directory = Path.of("./data/event-flows");
    private String fileSuffix = ".event-flows.json";
    private String diagramFileSuffix = ".event-flow-diagram.json";
    private boolean useEtag = true;
    private String runtimeFileSuffix = ".event-flow-runtime.json";
    private int maximumExecutionTraces = 200;
    private int maximumExecutionDepth = 32;
    private int maximumRuntimeStates = 256;
    private int maximumRuntimeDeployments = 128;
    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled() { return enabled; }
    /** Descripción: Actualiza el estado gestionado por EventFlowProperties mediante la operación «setEnabled». Entradas: enabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** Descripción: Obtiene la información correspondiente a «getDirectory» dentro de EventFlowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getDirectory() { return directory; }
    /** Descripción: Actualiza el estado gestionado por EventFlowProperties mediante la operación «setDirectory». Entradas: directory: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDirectory(Path directory) { this.directory = directory; }
    /** Descripción: Obtiene la información correspondiente a «getFileSuffix» dentro de EventFlowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getFileSuffix() { return fileSuffix; }
    /** Descripción: Actualiza el estado gestionado por EventFlowProperties mediante la operación «setFileSuffix». Entradas: fileSuffix: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setFileSuffix(String fileSuffix) { this.fileSuffix = fileSuffix; }
    /** Descripción: Obtiene la información correspondiente a «getDiagramFileSuffix» dentro de EventFlowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getDiagramFileSuffix() { return diagramFileSuffix; }
    /** Descripción: Actualiza el estado gestionado por EventFlowProperties mediante la operación «setDiagramFileSuffix». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDiagramFileSuffix(String value) { diagramFileSuffix = value; }
    /** Descripción: Obtiene la información correspondiente a «getRuntimeFileSuffix» dentro de EventFlowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getRuntimeFileSuffix() { return runtimeFileSuffix; }
    /** Descripción: Actualiza el estado gestionado por EventFlowProperties mediante la operación «setRuntimeFileSuffix». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRuntimeFileSuffix(String value) { runtimeFileSuffix = value; }
    /** Descripción: Obtiene la información correspondiente a «getMaximumExecutionTraces» dentro de EventFlowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumExecutionTraces() { return maximumExecutionTraces; }
    /** Descripción: Actualiza el estado gestionado por EventFlowProperties mediante la operación «setMaximumExecutionTraces». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumExecutionTraces(int value) { maximumExecutionTraces = Math.max(10, value); }
    /** Descripción: Evalúa la condición «isUseEtag» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isUseEtag() { return useEtag; }
    /** Descripción: Actualiza el estado gestionado por EventFlowProperties mediante la operación «setUseEtag». Entradas: useEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setUseEtag(boolean useEtag) { this.useEtag = useEtag; }
    /** Descripción: Obtiene la información correspondiente a «getMaximumExecutionDepth» dentro de EventFlowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumExecutionDepth() { return maximumExecutionDepth; }
    /** Descripción: Actualiza el estado gestionado por EventFlowProperties mediante la operación «setMaximumExecutionDepth». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumExecutionDepth(int value) { maximumExecutionDepth = Math.max(1, value); }
    /** Descripción: Obtiene la información correspondiente a «getMaximumRuntimeStates» dentro de EventFlowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumRuntimeStates() { return maximumRuntimeStates; }
    /** Descripción: Actualiza el estado gestionado por EventFlowProperties mediante la operación «setMaximumRuntimeStates». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumRuntimeStates(int value) { maximumRuntimeStates = Math.max(1, value); }
    /** Descripción: Obtiene la información correspondiente a «getMaximumRuntimeDeployments» dentro de EventFlowProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumRuntimeDeployments(){return maximumRuntimeDeployments;}
    /** Descripción: Actualiza el estado gestionado por EventFlowProperties mediante la operación «setMaximumRuntimeDeployments». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumRuntimeDeployments(int value){maximumRuntimeDeployments = Math.max(1, value);}
}
