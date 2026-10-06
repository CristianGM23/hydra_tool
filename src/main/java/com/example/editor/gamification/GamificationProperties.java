package com.example.editor.gamification;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dsl.gamification")
public class GamificationProperties {
    private boolean enabled = true;
    private Path directory = Path.of("./data/gamification");
    private String definitionFileSuffix = ".gamification-definitions.json";
    private String bindingFileSuffix = ".gamification-bindings.json";
    private String stateFileSuffix = ".gamification-state.json";
    private String ledgerFileSuffix = ".gamification-ledger.json";
    private String diagramFileSuffix = ".gamification-diagram.json";
    private boolean useEtag = true;
    private int maximumLedgerEntries = 10000;
    private int maximumFeedbackCacheEntries = 128;
    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled() { return enabled; }
    /** Descripción: Actualiza el estado gestionado por GamificationProperties mediante la operación «setEnabled». Entradas: enabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** Descripción: Obtiene la información correspondiente a «getDirectory» dentro de GamificationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getDirectory() { return directory; }
    /** Descripción: Actualiza el estado gestionado por GamificationProperties mediante la operación «setDirectory». Entradas: directory: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDirectory(Path directory) { this.directory = directory; }
    /** Descripción: Obtiene la información correspondiente a «getDefinitionFileSuffix» dentro de GamificationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getDefinitionFileSuffix() { return definitionFileSuffix; }
    /** Descripción: Actualiza el estado gestionado por GamificationProperties mediante la operación «setDefinitionFileSuffix». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDefinitionFileSuffix(String value) { definitionFileSuffix = value; }
    /** Descripción: Obtiene la información correspondiente a «getBindingFileSuffix» dentro de GamificationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getBindingFileSuffix() { return bindingFileSuffix; }
    /** Descripción: Actualiza el estado gestionado por GamificationProperties mediante la operación «setBindingFileSuffix». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setBindingFileSuffix(String value) { bindingFileSuffix = value; }
    /** Descripción: Obtiene la información correspondiente a «getStateFileSuffix» dentro de GamificationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getStateFileSuffix() { return stateFileSuffix; }
    /** Descripción: Actualiza el estado gestionado por GamificationProperties mediante la operación «setStateFileSuffix». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStateFileSuffix(String value) { stateFileSuffix = value; }
    /** Descripción: Obtiene la información correspondiente a «getLedgerFileSuffix» dentro de GamificationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getLedgerFileSuffix() { return ledgerFileSuffix; }
    /** Descripción: Actualiza el estado gestionado por GamificationProperties mediante la operación «setLedgerFileSuffix». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setLedgerFileSuffix(String value) { ledgerFileSuffix = value; }
    /** Descripción: Obtiene la información correspondiente a «getDiagramFileSuffix» dentro de GamificationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getDiagramFileSuffix() { return diagramFileSuffix; }
    /** Descripción: Actualiza el estado gestionado por GamificationProperties mediante la operación «setDiagramFileSuffix». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDiagramFileSuffix(String value) { diagramFileSuffix = value; }
    /** Descripción: Evalúa la condición «isUseEtag» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isUseEtag() { return useEtag; }
    /** Descripción: Actualiza el estado gestionado por GamificationProperties mediante la operación «setUseEtag». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setUseEtag(boolean value) { useEtag = value; }
    /** Descripción: Obtiene la información correspondiente a «getMaximumLedgerEntries» dentro de GamificationProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumLedgerEntries() { return maximumLedgerEntries; }
    /** Descripción: Actualiza el estado gestionado por GamificationProperties mediante la operación «setMaximumLedgerEntries». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumLedgerEntries(int value) { maximumLedgerEntries = Math.max(100, value); }
    /** Descripción: Obtiene el límite de proyecciones de retroalimentación retenidas en memoria. Entradas: no requiere parámetros. Salida: número máximo de entradas de caché. */ public int getMaximumFeedbackCacheEntries() { return maximumFeedbackCacheEntries; }
    /** Descripción: Configura un límite acotado para la caché de retroalimentación. Entradas: value: capacidad solicitada. Salida: no devuelve valor; conserva el límite dentro de márgenes seguros. */ public void setMaximumFeedbackCacheEntries(int value) { maximumFeedbackCacheEntries = Math.max(8, Math.min(value, 4096)); }
}
