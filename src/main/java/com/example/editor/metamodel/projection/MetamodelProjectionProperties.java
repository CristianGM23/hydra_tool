package com.example.editor.metamodel.projection;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.target.EcoreGenerationMode;
import com.example.editor.metamodel.target.EcoreProjectionConfiguration;

/**
 * Define los valores predeterminados de las proyecciones técnicas.
 * La configuración específica de cada metamodelo se guarda después en targets.json.
 */
@Component
@ConfigurationProperties(prefix = "dsl.metamodel.projections")
public class MetamodelProjectionProperties {

    private final Ecore ecore = new Ecore();

    /** Descripción: Obtiene la información correspondiente a «getEcore» dentro de Ecore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Ecore getEcore() {
        return ecore;
    }

    /** Descripción: Ejecuta la operación «defaultsFor» correspondiente a la responsabilidad de Ecore. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EcoreProjectionConfiguration defaultsFor(MetaModel metamodel) {
        String id = metamodel == null || metamodel.id() == null || metamodel.id().isBlank() ? "metamodel" : metamodel.id();
        String suffix = ecore.fileSuffix == null || ecore.fileSuffix.isBlank() ? ".ecore" : ecore.fileSuffix;
        EcoreProjectionConfiguration structuralDefaults = EcoreProjectionConfiguration.defaultsFor(metamodel);
        return new EcoreProjectionConfiguration(
                ecore.enabled,
                ecore.generationMode,
                ecore.requiredForRuntime,
                id + suffix,
                "urn:" + id,
                id,
                structuralDefaults.attributeOptions(),
                structuralDefaults.relationOptions(),
                structuralDefaults.dataTypeOptions());
    }

    public static class Ecore {

        private boolean enabled = true;
        private EcoreGenerationMode generationMode = EcoreGenerationMode.ON_SAVE;
        private boolean requiredForRuntime = true;
        private String fileSuffix = ".ecore";
        private boolean continueOnError = true;

        /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled() {
            return enabled;
        }

        /** Descripción: Actualiza el estado gestionado por Ecore mediante la operación «setEnabled». Entradas: enabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Descripción: Obtiene la información correspondiente a «getGenerationMode» dentro de Ecore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public EcoreGenerationMode getGenerationMode() {
            return generationMode;
        }

        /** Descripción: Actualiza el estado gestionado por Ecore mediante la operación «setGenerationMode». Entradas: generationMode: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setGenerationMode(EcoreGenerationMode generationMode) {
            this.generationMode = generationMode == null ? EcoreGenerationMode.ON_SAVE : generationMode;
        }

        /** Descripción: Evalúa la condición «isRequiredForRuntime» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isRequiredForRuntime() {
            return requiredForRuntime;
        }

        /** Descripción: Actualiza el estado gestionado por Ecore mediante la operación «setRequiredForRuntime». Entradas: requiredForRuntime: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRequiredForRuntime(boolean requiredForRuntime) {
            this.requiredForRuntime = requiredForRuntime;
        }

        /** Descripción: Obtiene la información correspondiente a «getFileSuffix» dentro de Ecore. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getFileSuffix() {
            return fileSuffix;
        }

        /** Descripción: Actualiza el estado gestionado por Ecore mediante la operación «setFileSuffix». Entradas: fileSuffix: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setFileSuffix(String fileSuffix) {
            this.fileSuffix = fileSuffix;
        }

        /** Descripción: Evalúa la condición «isContinueOnError» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isContinueOnError() {
            return continueOnError;
        }

        /** Descripción: Actualiza el estado gestionado por Ecore mediante la operación «setContinueOnError». Entradas: continueOnError: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setContinueOnError(boolean continueOnError) {
            this.continueOnError = continueOnError;
        }
    }
}
