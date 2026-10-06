package com.example.editor.event;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
/** Configura la persistencia de contratos de eventos. */
@Component
@ConfigurationProperties(prefix = "dsl.events")
public class EventModelProperties {
    private boolean enabled = true;
    private Path directory = Path.of("./data/events");
    private String definitionFileSuffix = ".event-definitions.json";
    private String bindingFileSuffix = ".event-bindings.json";
    private String diagramFileSuffix = ".event-architecture-diagram.json";
    private boolean useEtag = true;
    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled() { return enabled; }
    /** Descripción: Actualiza el estado gestionado por EventModelProperties mediante la operación «setEnabled». Entradas: enabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** Descripción: Obtiene la información correspondiente a «getDirectory» dentro de EventModelProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getDirectory() { return directory; }
    /** Descripción: Actualiza el estado gestionado por EventModelProperties mediante la operación «setDirectory». Entradas: directory: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDirectory(Path directory) { this.directory = directory; }
    /** Descripción: Obtiene la información correspondiente a «getDefinitionFileSuffix» dentro de EventModelProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getDefinitionFileSuffix() { return definitionFileSuffix; }
    /** Descripción: Actualiza el estado gestionado por EventModelProperties mediante la operación «setDefinitionFileSuffix». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDefinitionFileSuffix(String value) { definitionFileSuffix = value; }
    /** Descripción: Obtiene la información correspondiente a «getBindingFileSuffix» dentro de EventModelProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getBindingFileSuffix() { return bindingFileSuffix; }
    /** Descripción: Actualiza el estado gestionado por EventModelProperties mediante la operación «setBindingFileSuffix». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setBindingFileSuffix(String value) { bindingFileSuffix = value; }
    /** Descripción: Obtiene la información correspondiente a «getDiagramFileSuffix» dentro de EventModelProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getDiagramFileSuffix() { return diagramFileSuffix; }
    /** Descripción: Actualiza el estado gestionado por EventModelProperties mediante la operación «setDiagramFileSuffix». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDiagramFileSuffix(String value) { diagramFileSuffix = value; }
    /** Descripción: Evalúa la condición «isUseEtag» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isUseEtag() { return useEtag; }
    /** Descripción: Actualiza el estado gestionado por EventModelProperties mediante la operación «setUseEtag». Entradas: useEtag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setUseEtag(boolean useEtag) { this.useEtag = useEtag; }
}
