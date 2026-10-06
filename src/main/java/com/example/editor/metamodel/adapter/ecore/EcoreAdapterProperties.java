package com.example.editor.metamodel.adapter.ecore;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configura el adaptador bidireccional entre el núcleo neutral y Ecore.
 */
@Component
@ConfigurationProperties(prefix = "dsl.adapters.ecore")
public class EcoreAdapterProperties {

    private EcoreExternalTypePolicy externalTypePolicy = EcoreExternalTypePolicy.JAVA_OBJECT_WITH_ANNOTATION;
    private boolean preserveNeutralAnnotations = true;
    private boolean reportAdaptations = true;
    private Map<String, String> primitiveMappings = new LinkedHashMap<>();

    /**
     * Descripción:
     * Devuelve la política para tipos externos.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return la política configurada.
     */
    /** Descripción: Obtiene la información correspondiente a «getExternalTypePolicy» dentro de EcoreAdapterProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public EcoreExternalTypePolicy getExternalTypePolicy() {
        return externalTypePolicy;
    }

    /**
     * Descripción:
     * Modifica la política para tipos externos.
     *
     * Entrada:
     * @param externalTypePolicy contiene la política.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Actualiza el estado gestionado por EcoreAdapterProperties mediante la operación «setExternalTypePolicy». Entradas: externalTypePolicy: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setExternalTypePolicy(EcoreExternalTypePolicy externalTypePolicy) {
        this.externalTypePolicy = externalTypePolicy;
    }

    /**
     * Descripción:
     * Indica si se conservan anotaciones neutrales.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return true cuando se conservan.
     */
    /** Descripción: Evalúa la condición «isPreserveNeutralAnnotations» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isPreserveNeutralAnnotations() {
        return preserveNeutralAnnotations;
    }

    /**
     * Descripción:
     * Modifica la conservación de anotaciones.
     *
     * Entrada:
     * @param preserveNeutralAnnotations contiene el valor.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Actualiza el estado gestionado por EcoreAdapterProperties mediante la operación «setPreserveNeutralAnnotations». Entradas: preserveNeutralAnnotations: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setPreserveNeutralAnnotations(boolean preserveNeutralAnnotations) {
        this.preserveNeutralAnnotations = preserveNeutralAnnotations;
    }

    /**
     * Descripción:
     * Indica si las adaptaciones generan advertencias.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return true cuando se generan.
     */
    /** Descripción: Evalúa la condición «isReportAdaptations» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isReportAdaptations() {
        return reportAdaptations;
    }

    /**
     * Descripción:
     * Modifica la generación de advertencias.
     *
     * Entrada:
     * @param reportAdaptations contiene el valor.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Actualiza el estado gestionado por EcoreAdapterProperties mediante la operación «setReportAdaptations». Entradas: reportAdaptations: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setReportAdaptations(boolean reportAdaptations) {
        this.reportAdaptations = reportAdaptations;
    }

    /**
     * Descripción:
     * Devuelve las sustituciones de tipos primitivos.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return el mapa editable por Spring.
     */
    /** Descripción: Obtiene la información correspondiente a «getPrimitiveMappings» dentro de EcoreAdapterProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Map<String, String> getPrimitiveMappings() {
        return primitiveMappings;
    }

    /**
     * Descripción:
     * Modifica las sustituciones de tipos primitivos.
     *
     * Entrada:
     * @param primitiveMappings contiene las sustituciones.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Actualiza el estado gestionado por EcoreAdapterProperties mediante la operación «setPrimitiveMappings». Entradas: primitiveMappings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setPrimitiveMappings(Map<String, String> primitiveMappings) {
        if (primitiveMappings == null) {
            this.primitiveMappings = new LinkedHashMap<>();
        } else {
            this.primitiveMappings = new LinkedHashMap<>(primitiveMappings);
        }
    }
}
