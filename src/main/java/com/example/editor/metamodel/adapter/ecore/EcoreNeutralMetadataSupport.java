package com.example.editor.metamodel.adapter.ecore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;

import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.Multiplicity;

/**
 * Conserva dentro de Ecore la información neutral que no tiene una propiedad nativa.
 */
public final class EcoreNeutralMetadataSupport {

    public static final String ANNOTATIONS_SOURCE = "https://example.com/editor/neutral/annotations";
    public static final String METADATA_SOURCE = "https://example.com/editor/neutral/metadata";
    public static final String RELATION_SOURCE_PREFIX = "https://example.com/editor/neutral/relation/";
    public static final String EXTERNAL_TYPE_KEY = "externalType";
    public static final String OPERATION_BEHAVIOR_KIND_KEY = "operationBehaviorKind";
    public static final String OPERATION_EXTERNAL_EXCEPTION_TYPES_KEY = "operationExternalExceptionTypes";

    /** Descripción: Inicializa una instancia de EcoreNeutralMetadataSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private EcoreNeutralMetadataSupport() {
    }

    /**
     * Descripción:
     * Guarda anotaciones configurables en un elemento Ecore.
     *
     * Entrada:
     * @param element contiene el elemento.
     * @param annotations contiene las anotaciones.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Actualiza el estado gestionado por EcoreNeutralMetadataSupport mediante la operación «writeAnnotations». Entradas: element: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void writeAnnotations(EModelElement element, Map<String, String> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }

        EAnnotation annotation = getOrCreate(element, ANNOTATIONS_SOURCE);
        annotation.getDetails().clear();
        annotation.getDetails().putAll(annotations);
    }

    /**
     * Descripción:
     * Lee anotaciones configurables.
     *
     * Entrada:
     * @param element contiene el elemento.
     *
     * Salida:
     * @return las anotaciones.
     */
    /** Descripción: Obtiene la información correspondiente a «readAnnotations» dentro de EcoreNeutralMetadataSupport. Entradas: element: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Map<String, String> readAnnotations(EModelElement element) {
        EAnnotation annotation = element.getEAnnotation(ANNOTATIONS_SOURCE);

        if (annotation == null || annotation.getDetails().isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        result.putAll(annotation.getDetails().map());
        return Collections.unmodifiableMap(result);
    }

    /**
     * Descripción:
     * Guarda un detalle técnico en un elemento.
     *
     * Entrada:
     * @param element contiene el elemento.
     * @param key contiene la clave.
     * @param value contiene el valor.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Actualiza el estado gestionado por EcoreNeutralMetadataSupport mediante la operación «writeMetadata». Entradas: element: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void writeMetadata(EModelElement element, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        EAnnotation annotation = getOrCreate(element, METADATA_SOURCE);
        annotation.getDetails().put(key, value);
    }

    /**
     * Descripción:
     * Lee un detalle técnico.
     *
     * Entrada:
     * @param element contiene el elemento.
     * @param key contiene la clave.
     *
     * Salida:
     * @return el valor o una cadena vacía.
     */
    /** Descripción: Obtiene la información correspondiente a «readMetadata» dentro de EcoreNeutralMetadataSupport. Entradas: element: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static String readMetadata(EModelElement element, String key) {
        EAnnotation annotation = element.getEAnnotation(METADATA_SOURCE);

        if (annotation == null) {
            return "";
        }

        String value = annotation.getDetails().get(key);
        return value == null ? "" : value;
    }


    /**
     * Guarda una lista posicional de nombres de tipos externos. Las posiciones vacías
     * representan tipos que Ecore puede describir de forma nativa.
     *
     * @param element elemento propietario.
     * @param key clave de metadatos.
     * @param values valores posicionales.
     */
    /** Descripción: Actualiza el estado gestionado por EcoreNeutralMetadataSupport mediante la operación «writeExternalTypeList». Entradas: element: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; values: colección de elementos que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void writeExternalTypeList(EModelElement element, String key, List<String> values) {
        if(values == null || values.stream().allMatch(value -> value == null || value.isBlank())) {
            return;
        }
        String serialized = values.stream().map(value -> encode(value == null ? "" : value)).reduce((left, right) -> left + "," + right).orElse("");
        writeMetadata(element, key, serialized);
    }

    /**
     * Lee una lista posicional de nombres de tipos externos.
     *
     * @param element elemento propietario.
     * @param key clave de metadatos.
     * @return lista inmutable de valores.
     */
    /** Descripción: Obtiene la información correspondiente a «readExternalTypeList» dentro de EcoreNeutralMetadataSupport. Entradas: element: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public static List<String> readExternalTypeList(EModelElement element, String key) {
        String serialized = readMetadata(element, key);
        if(serialized.isBlank()) {
            return List.of();
        }
        String[] encodedValues = serialized.split(",", -1);
        List<String> result = new ArrayList<>(encodedValues.length);
        for(String encodedValue : encodedValues) {
            result.add(encodedValue.isBlank() ? "" : decode(encodedValue));
        }
        return List.copyOf(result);
    }

    /**
     * Descripción:
     * Guarda los metadatos completos de una relación en el paquete.
     *
     * Entrada:
     * @param ePackage contiene el paquete.
     * @param relation contiene la relación neutral.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Actualiza el estado gestionado por EcoreNeutralMetadataSupport mediante la operación «writeRelation». Entradas: ePackage: dato de entrada necesario para la operación; relation: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void writeRelation(EPackage ePackage, MetaRelation relation) {
        String source = RELATION_SOURCE_PREFIX + encode(relation.id());
        EAnnotation annotation = getOrCreate(ePackage, source);
        annotation.getDetails().clear();
        put(annotation, "id", relation.id());
        put(annotation, "name", relation.name());
        put(annotation, "sourceTypeId", relation.sourceTypeId());
        put(annotation, "targetTypeId", relation.targetTypeId());
        put(annotation, "kind", relation.kind().name());
        writeMultiplicity(annotation, "source", relation.sourceMultiplicity());
        writeMultiplicity(annotation, "target", relation.targetMultiplicity());
        put(annotation, "oppositeRelationId", relation.oppositeRelationId());
        put(annotation, "ordered", String.valueOf(relation.ordered()));
        put(annotation, "unique", String.valueOf(relation.unique()));

        for (Map.Entry<String, String> entry : relation.annotations().entrySet()) {
            put(annotation, "annotation." + encode(entry.getKey()), entry.getValue());
        }
    }

    /**
     * Descripción:
     * Lee todas las relaciones neutrales guardadas en el paquete.
     *
     * Entrada:
     * @param ePackage contiene el paquete.
     *
     * Salida:
     * @return las relaciones indexadas por identificador.
     */
    /** Descripción: Obtiene la información correspondiente a «readRelations» dentro de EcoreNeutralMetadataSupport. Entradas: ePackage: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Map<String, MetaRelation> readRelations(EPackage ePackage) {
        Map<String, MetaRelation> result = new LinkedHashMap<>();

        for (EAnnotation annotation : ePackage.getEAnnotations()) {
            if (annotation.getSource() == null || !annotation.getSource().startsWith(RELATION_SOURCE_PREFIX)) {
                continue;
            }

            MetaRelation relation = readRelation(annotation);

            if (relation != null) {
                result.put(relation.id(), relation);
            }
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Descripción:
     * Busca relaciones de un tipo entre dos clases.
     *
     * Entrada:
     * @param relations contiene las relaciones.
     * @param sourceTypeId identifica el origen.
     * @param targetTypeId identifica el destino.
     * @param kinds contiene los tipos admitidos.
     *
     * Salida:
     * @return la primera relación encontrada o null.
     */
    /** Descripción: Obtiene la información correspondiente a «findRelation» dentro de EcoreNeutralMetadataSupport. Entradas: relations: dato de entrada necesario para la operación; sourceTypeId: identificador que delimita el recurso objetivo; targetTypeId: identificador que delimita el recurso objetivo; kinds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static MetaRelation findRelation(Map<String, MetaRelation> relations, String sourceTypeId, String targetTypeId, List<MetaRelationKind> kinds) {
        for (MetaRelation relation : relations.values()) {
            if (relation.sourceTypeId().equals(sourceTypeId) && relation.targetTypeId().equals(targetTypeId) && kinds.contains(relation.kind())) {
                return relation;
            }
        }

        return null;
    }

    /** Descripción: Obtiene la información correspondiente a «readRelation» dentro de EcoreNeutralMetadataSupport. Entradas: annotation: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static MetaRelation readRelation(EAnnotation annotation) {
        String id = value(annotation, "id");
        String sourceTypeId = value(annotation, "sourceTypeId");
        String targetTypeId = value(annotation, "targetTypeId");
        String kindText = value(annotation, "kind");

        if (id.isBlank() || sourceTypeId.isBlank() || targetTypeId.isBlank() || kindText.isBlank()) {
            return null;
        }

        MetaRelationKind kind;

        try {
            kind = MetaRelationKind.valueOf(kindText);
        } catch (IllegalArgumentException exception) {
            return null;
        }

        Map<String, String> annotations = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : annotation.getDetails()) {
            if (entry.getKey().startsWith("annotation.")) {
                String encodedKey = entry.getKey().substring("annotation.".length());
                annotations.put(decode(encodedKey), entry.getValue());
            }
        }

        return new MetaRelation(
                id,
                value(annotation, "name"),
                sourceTypeId,
                targetTypeId,
                kind,
                readMultiplicity(annotation, "source"),
                readMultiplicity(annotation, "target"),
                value(annotation, "oppositeRelationId"),
                booleanValue(annotation, "ordered", true),
                booleanValue(annotation, "unique", true),
                annotations);
    }

    /** Descripción: Actualiza el estado gestionado por EcoreNeutralMetadataSupport mediante la operación «writeMultiplicity». Entradas: annotation: dato de entrada necesario para la operación; prefix: dato de entrada necesario para la operación; multiplicity: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void writeMultiplicity(EAnnotation annotation, String prefix, Multiplicity multiplicity) {
        if (multiplicity == null) {
            return;
        }

        put(annotation, prefix + "Lower", String.valueOf(multiplicity.lowerBound()));
        put(annotation, prefix + "Upper", multiplicity.unbounded() ? "*" : String.valueOf(multiplicity.upperBound()));
    }

    /** Descripción: Obtiene la información correspondiente a «readMultiplicity» dentro de EcoreNeutralMetadataSupport. Entradas: annotation: dato de entrada necesario para la operación; prefix: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Multiplicity readMultiplicity(EAnnotation annotation, String prefix) {
        String lowerText = value(annotation, prefix + "Lower");
        String upperText = value(annotation, prefix + "Upper");

        if (lowerText.isBlank() && upperText.isBlank()) {
            return null;
        }

        int lower = lowerText.isBlank() ? 0 : Integer.parseInt(lowerText);
        Integer upper = null;

        if (!upperText.isBlank() && !"*".equals(upperText)) {
            upper = Integer.valueOf(upperText);
        }

        return new Multiplicity(lower, upper);
    }

    /** Descripción: Ejecuta la operación «booleanValue» correspondiente a la responsabilidad de EcoreNeutralMetadataSupport. Entradas: annotation: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; fallback: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean booleanValue(EAnnotation annotation, String key, boolean fallback) {
        String value = value(annotation, key);

        if (value.isBlank()) {
            return fallback;
        }

        return Boolean.parseBoolean(value);
    }

    /** Descripción: Ejecuta la operación «put» correspondiente a la responsabilidad de EcoreNeutralMetadataSupport. Entradas: annotation: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void put(EAnnotation annotation, String key, String value) {
        if (value != null && !value.isBlank()) {
            annotation.getDetails().put(key, value);
        }
    }

    /** Descripción: Ejecuta la operación «value» correspondiente a la responsabilidad de EcoreNeutralMetadataSupport. Entradas: annotation: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static String value(EAnnotation annotation, String key) {
        String value = annotation.getDetails().get(key);
        return value == null ? "" : value;
    }

    /** Descripción: Obtiene la información correspondiente a «getOrCreate» dentro de EcoreNeutralMetadataSupport. Entradas: element: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static EAnnotation getOrCreate(EModelElement element, String source) {
        EAnnotation annotation = element.getEAnnotation(source);

        if (annotation == null) {
            annotation = EcoreFactory.eINSTANCE.createEAnnotation();
            annotation.setSource(source);
            element.getEAnnotations().add(annotation);
        }

        return annotation;
    }

    /** Descripción: Ejecuta la operación «encode» correspondiente a la responsabilidad de EcoreNeutralMetadataSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Descripción: Ejecuta la operación «decode» correspondiente a la responsabilidad de EcoreNeutralMetadataSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
