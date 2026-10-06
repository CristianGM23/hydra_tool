package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Regla declarativa y segura para interpretar una extensión Eclipse personalizada. */
public record UpdateSiteExtensionAdapterDescriptor(String id, String label,
        List<String> extensionPointTokens, List<String> elementNames,
        UpdateSiteSemanticKind semanticKind, String neutralTarget, String purpose,
        List<String> idAttributes, List<String> implementationAttributes,
        UpdateSiteConfidence confidence, boolean enabled) {
    public UpdateSiteExtensionAdapterDescriptor {
        id = text(id);
        label = label == null || label.isBlank() ? id : label.trim();
        extensionPointTokens = normalized(extensionPointTokens);
        elementNames = normalized(elementNames);
        semanticKind = semanticKind == null ? UpdateSiteSemanticKind.UNREPRESENTABLE : semanticKind;
        neutralTarget = text(neutralTarget);
        purpose = text(purpose);
        idAttributes = normalized(idAttributes);
        implementationAttributes = normalized(implementationAttributes);
        confidence = confidence == null ? UpdateSiteConfidence.DERIVED : confidence;
    }

    /** Descripción: Evalúa la condición «matches» sin modificar el estado externo. Entradas: extensionPoint: dato de entrada necesario para la operación; elementName: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean matches(String extensionPoint, String elementName) {
        if(!enabled) return false;
        String point = text(extensionPoint).toLowerCase();
        String element = text(elementName).toLowerCase();
        boolean pointMatches = extensionPointTokens.isEmpty()
                || extensionPointTokens.stream().anyMatch(point::contains);
        boolean elementMatches = elementNames.isEmpty() || elementNames.contains(element);
        return pointMatches && elementMatches;
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteExtensionAdapterDescriptor. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    /** Descripción: Ejecuta la operación «normalized» correspondiente a la responsabilidad de UpdateSiteExtensionAdapterDescriptor. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> normalized(List<String> values) {
        if(values == null) return List.of();
        return values.stream().map(UpdateSiteExtensionAdapterDescriptor::text)
                .filter(value -> !value.isBlank()).map(String::toLowerCase).distinct().toList();
    }
}
