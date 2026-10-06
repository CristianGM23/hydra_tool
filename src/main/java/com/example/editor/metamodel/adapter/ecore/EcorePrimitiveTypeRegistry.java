package com.example.editor.metamodel.adapter.ecore;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EcorePackage;
import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Resuelve tipos primitivos neutrales sin dispersar reglas Ecore por los adaptadores.
 */
@Component
public class EcorePrimitiveTypeRegistry {

    private final Map<MetaPrimitiveType, String> neutralToEcore;
    private final Map<String, MetaPrimitiveType> ecoreToNeutral;

    /**
     * Descripción:
     * Crea el registro utilizando valores predeterminados y sustituciones configuradas.
     *
     * Entrada:
     * @param properties contiene la configuración.
     *
     * Salida:
     * Crea una instancia del registro.
     */
    /** Descripción: Inicializa una instancia de EcorePrimitiveTypeRegistry con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public EcorePrimitiveTypeRegistry(EcoreAdapterProperties properties) {
        Map<MetaPrimitiveType, String> mappings = createDefaultMappings();

        for (Map.Entry<String, String> entry : properties.getPrimitiveMappings().entrySet()) {
            MetaPrimitiveType primitive = parsePrimitive(entry.getKey());
            mappings.put(primitive, entry.getValue());
        }

        this.neutralToEcore = Map.copyOf(mappings);
        Map<String, MetaPrimitiveType> reverseMappings = createDefaultReverseMappings();

        for (Map.Entry<String, String> entry : properties.getPrimitiveMappings().entrySet()) {
            MetaPrimitiveType primitive = parsePrimitive(entry.getKey());
            reverseMappings.put(entry.getValue(), primitive);
        }

        this.ecoreToNeutral = Map.copyOf(reverseMappings);
    }

    /**
     * Descripción:
     * Resuelve un tipo neutral a un EDataType de Ecore.
     *
     * Entrada:
     * @param primitive contiene el tipo neutral.
     *
     * Salida:
     * @return el tipo Ecore.
     */
    /** Descripción: Ejecuta la operación «toEcore» correspondiente a la responsabilidad de EcorePrimitiveTypeRegistry. Entradas: primitive: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EDataType toEcore(MetaPrimitiveType primitive) {
        String classifierName = neutralToEcore.get(primitive);

        if (classifierName == null || classifierName.isBlank()) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Ecore primitive mapping was not found for: " + primitive);
        }

        EClassifier classifier = EcorePackage.eINSTANCE.getEClassifier(classifierName);

        if (classifier instanceof EDataType dataType) {
            return dataType;
        }

        throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Configured Ecore primitive is not an EDataType: " + classifierName);
    }

    /**
     * Descripción:
     * Resuelve un EDataType estándar al tipo neutral.
     *
     * Entrada:
     * @param dataType contiene el tipo Ecore.
     *
     * Salida:
     * @return el tipo neutral o null cuando no existe equivalencia.
     */
    /** Descripción: Ejecuta la operación «fromEcore» correspondiente a la responsabilidad de EcorePrimitiveTypeRegistry. Entradas: dataType: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetaPrimitiveType fromEcore(EDataType dataType) {
        if (dataType == null || dataType.getEPackage() != EcorePackage.eINSTANCE) {
            return null;
        }

        return ecoreToNeutral.get(dataType.getName());
    }

    /**
     * Descripción:
     * Devuelve la tabla efectiva de conversión.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return un mapa inmutable.
     */
    /** Descripción: Ejecuta la operación «mappings» correspondiente a la responsabilidad de EcorePrimitiveTypeRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Map<MetaPrimitiveType, String> mappings() {
        return neutralToEcore;
    }

    /** Descripción: Ejecuta la operación «parsePrimitive» correspondiente a la responsabilidad de EcorePrimitiveTypeRegistry. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MetaPrimitiveType parsePrimitive(String value) {
        if (value == null || value.isBlank()) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Primitive mapping key must not be empty");
        }

        try {
            return MetaPrimitiveType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unknown neutral primitive mapping key: " + value, exception);
        }
    }

    /** Descripción: Crea o prepara el resultado de «createDefaultReverseMappings» dentro de EcorePrimitiveTypeRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, MetaPrimitiveType> createDefaultReverseMappings() {
        Map<String, MetaPrimitiveType> result = new LinkedHashMap<>();
        result.put("EString", MetaPrimitiveType.STRING);
        result.put("EBoolean", MetaPrimitiveType.BOOLEAN);
        result.put("EBooleanObject", MetaPrimitiveType.BOOLEAN);
        result.put("EByte", MetaPrimitiveType.BYTE);
        result.put("EByteObject", MetaPrimitiveType.BYTE);
        result.put("EShort", MetaPrimitiveType.SHORT);
        result.put("EShortObject", MetaPrimitiveType.SHORT);
        result.put("EInt", MetaPrimitiveType.INTEGER);
        result.put("EIntegerObject", MetaPrimitiveType.INTEGER);
        result.put("ELong", MetaPrimitiveType.LONG);
        result.put("ELongObject", MetaPrimitiveType.LONG);
        result.put("EFloat", MetaPrimitiveType.FLOAT);
        result.put("EFloatObject", MetaPrimitiveType.FLOAT);
        result.put("EDouble", MetaPrimitiveType.DOUBLE);
        result.put("EDoubleObject", MetaPrimitiveType.DOUBLE);
        result.put("EBigDecimal", MetaPrimitiveType.DECIMAL);
        result.put("EBigInteger", MetaPrimitiveType.BIG_INTEGER);
        result.put("EChar", MetaPrimitiveType.CHARACTER);
        result.put("ECharacterObject", MetaPrimitiveType.CHARACTER);
        result.put("EDate", MetaPrimitiveType.DATE);
        result.put("EByteArray", MetaPrimitiveType.BINARY);
        result.put("EJavaObject", MetaPrimitiveType.OBJECT);
        result.put("EObject", MetaPrimitiveType.OBJECT);
        return result;
    }

    /** Descripción: Crea o prepara el resultado de «createDefaultMappings» dentro de EcorePrimitiveTypeRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Map<MetaPrimitiveType, String> createDefaultMappings() {
        Map<MetaPrimitiveType, String> result = new EnumMap<>(MetaPrimitiveType.class);
        result.put(MetaPrimitiveType.STRING, "EString");
        result.put(MetaPrimitiveType.BOOLEAN, "EBoolean");
        result.put(MetaPrimitiveType.BYTE, "EByte");
        result.put(MetaPrimitiveType.SHORT, "EShort");
        result.put(MetaPrimitiveType.INTEGER, "EInt");
        result.put(MetaPrimitiveType.LONG, "ELong");
        result.put(MetaPrimitiveType.FLOAT, "EFloat");
        result.put(MetaPrimitiveType.DOUBLE, "EDouble");
        result.put(MetaPrimitiveType.DECIMAL, "EBigDecimal");
        result.put(MetaPrimitiveType.BIG_INTEGER, "EBigInteger");
        result.put(MetaPrimitiveType.CHARACTER, "EChar");
        result.put(MetaPrimitiveType.DATE, "EDate");
        result.put(MetaPrimitiveType.BINARY, "EByteArray");
        result.put(MetaPrimitiveType.OBJECT, "EJavaObject");
        return result;
    }
}
