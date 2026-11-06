package com.example.editor.integration.adapter.langium;

import com.example.editor.metamodel.core.Multiplicity;

/** Emite gramáticas con cardinalidad exacta, sin convertir inadvertidamente 1..* en 0..*. */
public final class LangiumMultiplicityEmitter {
    private static final int MAX_FINITE_OCCURRENCES = 64;
    /** Descripción: Evita la creación de instancias del generador utilitario. Entrada: sin parámetros. Salida: constructor privado no instanciable externamente. */
    private LangiumMultiplicityEmitter() {}

    /** Descripción: Genera una propiedad escalar o multivaluada respetando las cotas del metamodelo. Entradas: nombre, gramática del valor y multiplicidad. Salida: fragmento de gramática Langium. */
    public static String fragment(String name, String valueGrammar, Multiplicity multiplicity) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid Langium property name");
        }
        if (valueGrammar == null || valueGrammar.isBlank()) {
            throw new IllegalArgumentException("Missing Langium value grammar");
        }
        int min = multiplicity == null ? 0 : multiplicity.lowerBound();
        Integer upper = multiplicity == null ? Integer.valueOf(1) : multiplicity.upperBound();
        if (min < 0 || (upper != null && (upper < min || upper < 0))) {
            throw new IllegalArgumentException("Invalid multiplicity for " + name);
        }
        if (min > MAX_FINITE_OCCURRENCES || (upper != null && upper > MAX_FINITE_OCCURRENCES)) {
            throw new IllegalArgumentException("Multiplicity exceeds bounded Langium grammar budget: " + name);
        }
        if (upper != null && upper == 0) return "";
        boolean many = upper == null || upper > 1;
        String assignment = name + (many ? "+=" : "=") + valueGrammar;
        String item = " ('" + name + "' " + assignment + ")";
        StringBuilder grammar = new StringBuilder();
        for (int i = 0; i < min; i++) grammar.append(item);
        if (upper == null) grammar.append(item).append('*');
        else for (int i = min; i < upper; i++) grammar.append(item).append('?');
        return grammar.toString();
    }
}
