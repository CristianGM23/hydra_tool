package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Diferencia estructural y de contenido entre la versión más antigua y la más reciente. */
public record UpdateSiteVersionDelta(String id, String artifactType, String fromVersion, String toVersion,
        List<String> addedArtifacts, List<String> removedArtifacts, List<String> changedArtifacts,
        int unchangedArtifacts, boolean modelBearing, UpdateSiteReleaseRisk risk,
        String explanation, String recommendation) {
    public UpdateSiteVersionDelta {
        id = text(id);
        artifactType = text(artifactType);
        fromVersion = text(fromVersion);
        toVersion = text(toVersion);
        addedArtifacts = copy(addedArtifacts);
        removedArtifacts = copy(removedArtifacts);
        changedArtifacts = copy(changedArtifacts);
        unchangedArtifacts = Math.max(0, unchangedArtifacts);
        risk = risk == null ? UpdateSiteReleaseRisk.NONE : risk;
        explanation = text(explanation);
        recommendation = text(recommendation);
    }

    /** Descripción: Inicializa una instancia de UpdateSiteVersionDelta con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; artifactType: dato de entrada necesario para la operación; fromVersion: dato de entrada necesario para la operación; toVersion: dato de entrada necesario para la operación; addedArtifacts: dato de entrada necesario para la operación; removedArtifacts: dato de entrada necesario para la operación; modelBearing: dato de entrada necesario para la operación; explanation: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteVersionDelta(String id, String artifactType, String fromVersion, String toVersion,
            List<String> addedArtifacts, List<String> removedArtifacts, boolean modelBearing,
            String explanation) {
        this(id, artifactType, fromVersion, toVersion, addedArtifacts, removedArtifacts, List.of(), 0,
                modelBearing, modelBearing ? UpdateSiteReleaseRisk.HIGH : UpdateSiteReleaseRisk.MEDIUM,
                explanation, "Review the affected artifacts before importing the latest version.");
    }

    /** Descripción: Ejecuta la operación «changedItemCount» correspondiente a la responsabilidad de UpdateSiteVersionDelta. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int changedItemCount() {
        return addedArtifacts.size() + removedArtifacts.size() + changedArtifacts.size();
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteVersionDelta. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) {
        return value == null ? "" : value;
    }

    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de UpdateSiteVersionDelta. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
