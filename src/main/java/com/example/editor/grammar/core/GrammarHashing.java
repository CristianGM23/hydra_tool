package com.example.editor.grammar.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Deterministic hashes used by grammar drafts, caches, snapshots and generated artifacts. */
public final class GrammarHashing {
    /** Descripción: Inicializa una instancia de GrammarHashing con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private GrammarHashing() { }

    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de GrammarHashing. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Descripción: Ejecuta la operación «sources» correspondiente a la responsabilidad de GrammarHashing. Entradas: documents: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String sources(List<GrammarSourceDocument> documents) {
        MessageDigest digest = newDigest();
        (documents == null ? List.<GrammarSourceDocument>of() : documents).stream()
                .sorted(Comparator.comparing(GrammarSourceDocument::relativePath).thenComparing(GrammarSourceDocument::id))
                .forEach(document -> {
                    update(digest, document.id());
                    update(digest, document.relativePath());
                    update(digest, document.languageId());
                    update(digest, document.content());
                    digest.update((byte) (document.entryPoint() ? 1 : 0));
                    digest.update((byte) (document.generated() ? 1 : 0));
                });
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Descripción: Actualiza el estado gestionado por GrammarHashing mediante la operación «settings». Entradas: settings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String settings(Map<String, String> settings) {
        MessageDigest digest = newDigest();
        new TreeMap<>(settings == null ? Map.of() : settings).forEach((key, value) -> {
            update(digest, key);
            update(digest, value);
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Descripción: Ejecuta la operación «toolchain» correspondiente a la responsabilidad de GrammarHashing. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String toolchain(GrammarToolchainProfile profile) {
        if (profile == null) return sha256("");
        return sha256(String.join("\u0000",
                profile.id(), profile.technology().name(), profile.providerVersion(), profile.runtimeVersion(),
                profile.javaVersion(), profile.nodeVersion(), profile.buildSystem(), profile.targetLanguage(),
                Boolean.toString(profile.warningsAsErrors()), Long.toString(profile.analysisTimeoutMs()),
                Long.toString(profile.compileTimeoutMs()), Long.toString(profile.maximumMemoryBytes()),
                profile.executionMode().name(), settings(profile.settings())));
    }

    /** Descripción: Crea o prepara el resultado de «newDigest» dentro de GrammarHashing. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Descripción: Actualiza el estado gestionado por GrammarHashing mediante la operación «update». Entradas: digest: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void update(MessageDigest digest, String value) {
        digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
