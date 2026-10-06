package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Token, XML element and attribute coverage for one adapter version. */
public record UpdateSiteAdapterCoverage(int coveredTokens, int totalTokens,
        int coveredElements, int totalElements, int coveredIdAttributes, int totalIdAttributes,
        int coveredImplementationAttributes, int totalImplementationAttributes,
        List<String> uncoveredTokens, List<String> uncoveredElements,
        List<String> uncoveredIdAttributes, List<String> uncoveredImplementationAttributes,
        List<String> redundantRules, List<String> unreachableRules) {
    public UpdateSiteAdapterCoverage {
        uncoveredTokens = copy(uncoveredTokens); uncoveredElements = copy(uncoveredElements);
        uncoveredIdAttributes = copy(uncoveredIdAttributes); uncoveredImplementationAttributes = copy(uncoveredImplementationAttributes);
        redundantRules = copy(redundantRules); unreachableRules = copy(unreachableRules);
    }
    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de UpdateSiteAdapterCoverage. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> copy(List<String> value){return value == null ? List.of() : List.copyOf(value);}
}
