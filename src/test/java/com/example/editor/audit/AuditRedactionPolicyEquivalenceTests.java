package com.example.editor.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * DT-TXT-SAVE-EVENTS: la política de redacción se evalúa en cada nodo de cada snapshot de auditoría, y antes compilaba una expresión regular
 * por la ruta y otra por cada clave configurada en cada llamada. La versión optimizada debe decidir exactamente lo mismo que la original,
 * también cuando la lista de claves cambia en caliente o contiene entradas nulas, vacías o que se normalizan a nada.
 */
class AuditRedactionPolicyEquivalenceTests {
    private static final String ALPHABET = "abcXYZ_09.-[]$ :/İıßΣςÅéÉKＡ😀\uD800ﬀ";

    @Test
    void decidesExactlyLikeTheRegexImplementationOnRandomUnicodePaths() {
        AuditLogProperties properties = new AuditLogProperties();
        properties.getSensitiveKeys().addAll(List.of("İd", "ſecret", "Σ", "KKey"));
        ConfiguredAuditRedactionPolicy policy = new ConfiguredAuditRedactionPolicy(properties);
        Random random = new Random(20261030L);
        int sensitive = 0;
        for(int i = 0; i < 20000; i++) {
            String path = randomPath(random, properties.getSensitiveKeys());
            boolean expected = reference(properties.getSensitiveKeys(), path);
            assertEquals(expected, policy.sensitive(path), () -> "path=" + path);
            if(expected) sensitive++;
        }
        assertTrue(sensitive > 1000, "el corpus debe ejercitar los dos resultados: " + sensitive);
        assertEquals(reference(properties.getSensitiveKeys(), null), policy.sensitive(null));
    }

    @Test
    void normalizationMatchesTheRegexOnEveryBmpCharacterAndOnSurrogates() {
        StringBuilder all = new StringBuilder();
        for(char c = 1; c < Character.MAX_VALUE; c++) all.append(c);
        String text = all + "😀x\uDC00y\uD800";
        assertEquals(text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", ""), ConfiguredAuditRedactionPolicy.normalize(text));
    }

    @Test
    void followsChangesToTheConfiguredKeysAtRuntime() {
        AuditLogProperties properties = new AuditLogProperties();
        ConfiguredAuditRedactionPolicy policy = new ConfiguredAuditRedactionPolicy(properties);
        assertTrue(policy.sensitive("$.config.apiToken"));
        assertFalse(policy.sensitive("$.elements[3].attributes.serial"));
        properties.getSensitiveKeys().add("Serial");
        assertTrue(policy.sensitive("$.elements[3].attributes.serial"));
        properties.getSensitiveKeys().clear();
        assertFalse(policy.sensitive("$.config.apiToken"));
        properties.getSensitiveKeys().addAll(java.util.Arrays.asList(null, "", "   ", "--", "ÁÉ"));
        for(String path : List.of("", "$", "$.a.b", "$.--", "$.áé")) assertEquals(reference(properties.getSensitiveKeys(), path), policy.sensitive(path), path);
        properties.getSensitiveKeys().set(3, "a.b");
        assertTrue(policy.sensitive("$.a.b"));
    }

    @Test
    void producesTheSameSnapshotOfALargeDocumentAsTheRegexPolicy() {
        AuditLogProperties properties = new AuditLogProperties();
        JsonMapper mapper = JsonMapper.builder().build();
        AuditSnapshotService optimized = new AuditSnapshotService(mapper, properties,
                new AuditRedactionRegistry(List.of(new ConfiguredAuditRedactionPolicy(properties))));
        AuditSnapshotService original = new AuditSnapshotService(mapper, properties,
                new AuditRedactionRegistry(List.of(path -> reference(properties.getSensitiveKeys(), path))));
        Map<String, Object> document = largeDocument(5000);
        Object expected = null, actual = null;
        long originalNanos = Long.MAX_VALUE, optimizedNanos = Long.MAX_VALUE;
        // alternadas y con el mínimo de cuatro, para no atribuir a una el calentamiento de la JVM
        for(int round = 0; round < 4; round++) {
            long start = System.nanoTime();
            expected = original.snapshot(document);
            originalNanos = Math.min(originalNanos, System.nanoTime() - start);
            start = System.nanoTime();
            actual = optimized.snapshot(document);
            optimizedNanos = Math.min(optimizedNanos, System.nanoTime() - start);
            assertEquals(expected, actual);
        }
        assertTrue(String.valueOf(actual).contains(AuditRedactionRegistry.REDACTED), "el documento debe contener claves redactadas");
        System.out.printf("snapshot 5000 elementos: regex %.1f ms, optimizada %.1f ms%n", originalNanos / 1e6, optimizedNanos / 1e6);
    }

    /** La implementación anterior, literal, como oráculo. */
    private static boolean reference(List<String> configured, String path) {
        String normalized = path == null ? "" : path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        return configured.stream().filter(item -> item != null && !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", ""))
                .anyMatch(key -> !key.isBlank() && normalized.contains(key));
    }

    private static String randomPath(Random random, List<String> keys) {
        StringBuilder path = new StringBuilder("$");
        int segments = 1 + random.nextInt(6);
        for(int s = 0; s < segments; s++) {
            path.append(random.nextBoolean() ? "." : "[" + random.nextInt(5000) + "].");
            if(random.nextInt(8) == 0) {
                String key = keys.get(random.nextInt(keys.size()));
                path.append(random.nextBoolean() ? key.toUpperCase(Locale.ROOT) : key);
            }
            int length = random.nextInt(12);
            for(int i = 0; i < length; i++) path.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return path.toString();
    }

    private static Map<String, Object> largeDocument(int elements) {
        List<Object> items = new ArrayList<>();
        for(int i = 0; i < elements; i++) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("code", "c" + i);
            attributes.put("serial", "s" + i);
            if(i % 97 == 0) attributes.put("apiToken", "secret-" + i);
            if(i % 131 == 0) attributes.put("connection-string", "jdbc:x" + i);
            Map<String, Object> element = new LinkedHashMap<>();
            element.put("id", "element" + i);
            element.put("type", "Sensor");
            element.put("attributes", attributes);
            items.add(element);
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("sessionId", "session-1");
        document.put("elements", items);
        return document;
    }
}
