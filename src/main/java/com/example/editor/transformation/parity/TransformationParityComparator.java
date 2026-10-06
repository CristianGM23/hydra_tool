package com.example.editor.transformation.parity;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.example.editor.transformation.text.TransformationTextArtifact;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;

/**
 * Normaliza resultados de ejecución antes de compararlos. Los identificadores y
 * anotaciones de runtime no participan en la equivalencia; sí participan el
 * metamodelo, las raíces, las clases, atributos, relaciones, rutas y contenido.
 */
@Component
public class TransformationParityComparator {
    private static final int MAX_DIFFERENCES = 64;
    private static final int MAX_PREVIEW = 320;

    /** Descripción: Ejecuta la operación «compareModels» correspondiente a la responsabilidad de TransformationParityComparator. Entradas: expected: dato de entrada necesario para la operación; actual: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Comparison compareModels(CanonicalModelDocument expected, CanonicalModelDocument actual) {
        if(expected == null || actual == null) return invalid("$.targetModel", expected == null ? "canonical model" : "native model");
        ModelSemantic left = semantic(expected);
        ModelSemantic right = semantic(actual);
        List<TransformationParityDifference> differences = new ArrayList<>();
        if(!expected.metamodelId().equals(actual.metamodelId())) add(differences, "$.targetModel.metamodelId", expected.metamodelId(), actual.metamodelId());
        compareMultiset("$.targetModel.rootElements", left.roots(), right.roots(), differences);
        compareMultiset("$.targetModel.elements", left.elements(), right.elements(), differences);
        compareMultiset("$.targetModel.relations", left.relations(), right.relations(), differences);
        String canonical = left.serialized(expected.metamodelId());
        String nativeValue = right.serialized(actual.metamodelId());
        return new Comparison(differences.isEmpty(), hash(canonical), hash(nativeValue), differences);
    }

    /** Descripción: Ejecuta la operación «compareArtifacts» correspondiente a la responsabilidad de TransformationParityComparator. Entradas: expected: dato de entrada necesario para la operación; actual: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Comparison compareArtifacts(List<TransformationTextArtifact> expected, List<TransformationParityArtifact> actual) {
        List<String> left = expected == null ? List.of() : expected.stream().map(item -> artifact(item.path(), item.content())).sorted().toList();
        List<String> right = actual == null ? List.of() : actual.stream().map(item -> artifact(item.path(), item.content())).sorted().toList();
        List<TransformationParityDifference> differences = new ArrayList<>();
        compareMultiset("$.artifacts", left, right, differences);
        return new Comparison(differences.isEmpty(), hash(String.join("\n", left)), hash(String.join("\n", right)), differences);
    }

    /** Descripción: Ejecuta la operación «semantic» correspondiente a la responsabilidad de TransformationParityComparator. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ModelSemantic semantic(CanonicalModelDocument model) {
        Map<String,String> elementById = new LinkedHashMap<>();
        List<String> elements = new ArrayList<>();
        for(CanonicalModelElement element : model.elements()) {
            String signature = "class=" + scalar(element.classId()) + ";attributes=" + value(element.attributes());
            elementById.put(element.id(), signature);
            elements.add(signature);
        }
        Collections.sort(elements);
        List<String> roots = model.rootElementIds().stream().map(id -> elementById.getOrDefault(id, "<missing:" + scalar(id) + ">" )).sorted().toList();
        List<String> relations = new ArrayList<>();
        for(CanonicalModelRelation relation : model.relations()) {
            String source = elementById.getOrDefault(relation.sourceElementId(), "<missing:" + scalar(relation.sourceElementId()) + ">");
            String target = elementById.getOrDefault(relation.targetElementId(), "<missing:" + scalar(relation.targetElementId()) + ">");
            relations.add("relation=" + scalar(relation.relationId()) + ";source={" + source + "};target={" + target + "}");
        }
        Collections.sort(relations);
        return new ModelSemantic(roots, List.copyOf(elements), List.copyOf(relations));
    }

    /** Descripción: Ejecuta la operación «artifact» correspondiente a la responsabilidad de TransformationParityComparator. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String artifact(String path, String content) {
        String normalizedPath = path == null ? "" : path.replace('\\', '/');
        while(normalizedPath.startsWith("./")) normalizedPath = normalizedPath.substring(2);
        String normalizedContent = content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
        return "path=" + scalar(normalizedPath) + ";content=" + scalar(normalizedContent);
    }

    /** Descripción: Ejecuta la operación «value» correspondiente a la responsabilidad de TransformationParityComparator. Entradas: raw: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String value(Object raw) {
        if(raw == null) return "null";
        if(raw instanceof CharSequence || raw instanceof Character || raw instanceof Enum<?>) return scalar(String.valueOf(raw));
        if(raw instanceof Boolean bool) return bool ? "true" : "false";
        if(raw instanceof Number number) return number(number);
        if(raw instanceof Map<?,?> map) {
            TreeMap<String,Object> ordered = new TreeMap<>();
            map.forEach((key, item) -> ordered.put(String.valueOf(key), item));
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for(var entry : ordered.entrySet()) {
                if(!first) out.append(',');
                first = false;
                out.append(scalar(entry.getKey())).append(':').append(value(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if(raw instanceof Collection<?> collection) {
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for(Object item : collection) {
                if(!first) out.append(',');
                first = false;
                out.append(value(item));
            }
            return out.append(']').toString();
        }
        if(raw.getClass().isArray()) {
            StringBuilder out = new StringBuilder("[");
            for(int i = 0; i < Array.getLength(raw); i++) {
                if(i > 0) out.append(',');
                out.append(value(Array.get(raw, i)));
            }
            return out.append(']').toString();
        }
        return scalar(String.valueOf(raw));
    }

    /** Descripción: Ejecuta la operación «number» correspondiente a la responsabilidad de TransformationParityComparator. Entradas: number: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String number(Number number) {
        try {
            return new BigDecimal(String.valueOf(number)).stripTrailingZeros().toPlainString();
        } catch(NumberFormatException exception) {
            return scalar(String.valueOf(number));
        }
    }

    /** Descripción: Ejecuta la operación «scalar» correspondiente a la responsabilidad de TransformationParityComparator. Entradas: text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String scalar(String text) {
        String value = text == null ? "" : text;
        return value.length() + ":" + value;
    }

    /** Descripción: Ejecuta la operación «compareMultiset» correspondiente a la responsabilidad de TransformationParityComparator. Entradas: path: ruta o ubicación utilizada por la operación; expected: dato de entrada necesario para la operación; actual: dato de entrada necesario para la operación; differences: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void compareMultiset(String path, List<String> expected, List<String> actual, List<TransformationParityDifference> differences) {
        Map<String,Integer> left = counts(expected);
        Map<String,Integer> right = counts(actual);
        TreeMap<String,Boolean> signatures = new TreeMap<>();
        left.keySet().forEach(item -> signatures.put(item, Boolean.TRUE));
        right.keySet().forEach(item -> signatures.put(item, Boolean.TRUE));
        for(String signature : signatures.keySet()) {
            int expectedCount = left.getOrDefault(signature, 0);
            int actualCount = right.getOrDefault(signature, 0);
            if(expectedCount != actualCount) {
                add(differences, path + "[" + preview(signature) + "]", String.valueOf(expectedCount), String.valueOf(actualCount));
                if(differences.size() >= MAX_DIFFERENCES) return;
            }
        }
    }

    /** Descripción: Ejecuta la operación «counts» correspondiente a la responsabilidad de TransformationParityComparator. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,Integer> counts(List<String> values) {
        Map<String,Integer> result = new LinkedHashMap<>();
        for(String value : values) result.merge(value, 1, Integer::sum);
        return result;
    }

    /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de TransformationParityComparator. Entradas: differences: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; expected: dato de entrada necesario para la operación; actual: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(List<TransformationParityDifference> differences, String path, String expected, String actual) {
        if(differences.size() < MAX_DIFFERENCES) differences.add(new TransformationParityDifference(path, preview(expected), preview(actual)));
    }

    /** Descripción: Ejecuta la operación «invalid» correspondiente a la responsabilidad de TransformationParityComparator. Entradas: path: ruta o ubicación utilizada por la operación; missing: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Comparison invalid(String path, String missing) {
        return new Comparison(false, "", "", List.of(new TransformationParityDifference(path, "present", missing + " is missing")));
    }

    /** Descripción: Ejecuta la operación «preview» correspondiente a la responsabilidad de TransformationParityComparator. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String preview(String value) {
        String normalized = value == null ? "" : value.replace('\n', ' ');
        return normalized.length() <= MAX_PREVIEW ? normalized : normalized.substring(0, MAX_PREVIEW) + "…";
    }

    /** Descripción: Evalúa la condición «hash» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch(NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Comparison(boolean matched, String canonicalFingerprint, String nativeFingerprint,
            List<TransformationParityDifference> differences) {
        public Comparison {
            canonicalFingerprint = canonicalFingerprint == null ? "" : canonicalFingerprint;
            nativeFingerprint = nativeFingerprint == null ? "" : nativeFingerprint;
            differences = differences == null ? List.of() : List.copyOf(differences);
        }
    }

    private record ModelSemantic(List<String> roots, List<String> elements, List<String> relations) {
        /** Descripción: Ejecuta la operación «serialized» correspondiente a la responsabilidad de TransformationParityComparator. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ String serialized(String metamodelId) {
            return "metamodel=" + metamodelId + "\nroots=" + String.join("\n", roots)
                    + "\nelements=" + String.join("\n", elements) + "\nrelations=" + String.join("\n", relations);
        }
    }
}
