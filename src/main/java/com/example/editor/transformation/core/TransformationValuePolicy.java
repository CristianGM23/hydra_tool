package com.example.editor.transformation.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Política ortogonal que adapta el valor de un binding sin cambiar su fuente. */
public record TransformationValuePolicy(
        TransformationCollectionMode collectionMode,
        TransformationConversionMode conversionMode,
        Map<String, String> enumMapping,
        TransformationBindingErrorPolicy errorPolicy,
        Object fallbackValue) {

    public TransformationValuePolicy {
        collectionMode = collectionMode == null ? TransformationCollectionMode.PRESERVE : collectionMode;
        conversionMode = conversionMode == null ? TransformationConversionMode.NONE : conversionMode;
        enumMapping = enumMapping == null || enumMapping.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(enumMapping));
        errorPolicy = errorPolicy == null ? TransformationBindingErrorPolicy.FAIL : errorPolicy;
    }

    /** Devuelve la política neutral por defecto, equivalente al comportamiento de Fix17. */
    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de TransformationValuePolicy. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static TransformationValuePolicy defaults() {
        return new TransformationValuePolicy(TransformationCollectionMode.PRESERVE, TransformationConversionMode.NONE,
                Map.of(), TransformationBindingErrorPolicy.FAIL, null);
    }
}
