package com.example.editor.standalone.distribution;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** User-selected distribution settings for one generated editor. */
public record StandaloneDistributionProfile(
        String productName,
        String version,
        Set<StandaloneDistributionTarget> targets,
        boolean offlineEnabled,
        List<String> fileAssociations,
        String updateChannel) {

    public StandaloneDistributionProfile {
        productName = requireText(productName, "productName");
        version = requireText(version, "version");
        targets = Set.copyOf(Objects.requireNonNull(targets, "targets"));
        fileAssociations = Objects.requireNonNull(fileAssociations, "fileAssociations").stream()
                .map(StandaloneDistributionProfile::normalizeExtension)
                .distinct()
                .sorted()
                .toList();
        updateChannel = requireText(updateChannel, "updateChannel");
        if(targets.isEmpty()){
            throw new IllegalArgumentException("targets must not be empty");
        }
    }

    /** Descripción: Ejecuta la operación «normalizeExtension» correspondiente a la responsabilidad de StandaloneDistributionProfile. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeExtension(String value){
        String normalized = requireText(value, "fileAssociation").toLowerCase();
        return normalized.startsWith(".") ? normalized : "." + normalized;
    }

    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field){
        Objects.requireNonNull(value, field);
        if(value.isBlank()){
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
