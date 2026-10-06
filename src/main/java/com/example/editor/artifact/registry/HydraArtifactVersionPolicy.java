package com.example.editor.artifact.registry;

import java.math.BigInteger;
import java.util.regex.Pattern;

/** Comparador conservador de versiones de artefactos: ordena versiones numéricas y exige igualdad para formatos opacos. */
public final class HydraArtifactVersionPolicy {
    private static final Pattern NUMERIC = Pattern.compile("[0-9]+(?:\\.[0-9]+)*");
    /** Descripción: Impide instanciar la política de versiones. Entradas: no requiere parámetros. Salida: no crea instancias públicas. */
    private HydraArtifactVersionPolicy() { }

    /** Descripción: Evalúa si una versión disponible satisface la versión mínima declarada. Entradas: actual: versión disponible; minimum: mínimo requerido. Salida: true si la dependencia es compatible por versión. */
    public static boolean supports(String actual, String minimum) {
        String current = text(actual);
        String required = text(minimum);
        if (required.isBlank()) return true;
        if (current.isBlank()) return false;
        if (!NUMERIC.matcher(current).matches() || !NUMERIC.matcher(required).matches()) return current.equals(required);
        String[] left = current.split("\\.");
        String[] right = required.split("\\.");
        int length = Math.max(left.length, right.length);
        for (int index = 0; index < length; index++) {
            BigInteger a = index < left.length ? new BigInteger(left[index]) : BigInteger.ZERO;
            BigInteger b = index < right.length ? new BigInteger(right[index]) : BigInteger.ZERO;
            int compared = a.compareTo(b);
            if (compared != 0) return compared > 0;
        }
        return true;
    }

    /** Descripción: Normaliza una versión opcional antes de compararla. Entradas: value: versión. Salida: cadena no nula. */
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
