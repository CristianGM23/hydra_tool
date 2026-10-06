package com.example.editor.integration.representable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared bounded mapper based on a server-owned native-kind catalog. */
abstract class AbstractCataloguedRepresentableMapper implements RepresentableImportMapper {
    // Technology-specific semantics without a neutral equivalent are preserved explicitly.
    private final String technology;
    private final Map<String, NeutralElementKind> kinds;
    private final Set<String> partialKinds;

    /** Descripción: Inicializa una instancia de AbstractCataloguedRepresentableMapper con sus dependencias y datos de entrada. Entradas: technology: dato de entrada necesario para la operación; kinds: dato de entrada necesario para la operación; partialKinds: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ protected AbstractCataloguedRepresentableMapper(String technology,
            Map<String, NeutralElementKind> kinds, Set<String> partialKinds){
        this.technology = technology; this.kinds = Map.copyOf(kinds); this.partialKinds = Set.copyOf(partialKinds);
    }

    /** Descripción: Ejecuta la operación «technology» correspondiente a la responsabilidad de AbstractCataloguedRepresentableMapper. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public String technology(){return technology;}

    /** Descripción: Ejecuta la operación «map» correspondiente a la responsabilidad de AbstractCataloguedRepresentableMapper. Entradas: candidate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public RepresentableMappingResult map(ExternalChangeCandidate candidate){
        String token = candidate.nativeKind().trim().toUpperCase(Locale.ROOT);
        NeutralElementKind neutralKind = kinds.get(token);
        if(neutralKind == null){
            return new RepresentableMappingResult(null, java.util.List.of(new RepresentableImportDiagnostic(
                    candidate.externalId(), Representability.NONE, "NATIVE_KIND_UNSUPPORTED",
                    "External element '" + candidate.qualifiedName() + "' uses a source-specific kind that the selected adapter cannot represent neutrally (" + candidate.nativeKind() + ").",
                    "Keep this construct native, remove it from the neutral package, or provide a HYDRA mapper for " + candidate.nativeKind() + ".")));
        }
        Map<String,Object> values = new LinkedHashMap<>(candidate.properties());
        String resolution = stringValue(values.remove("hydra:resolution"));
        String targetElementId = stringValue(values.remove("hydra:targetElementId"));
        if("MAP_EXISTING".equals(resolution) && targetElementId.isBlank()){
            return new RepresentableMappingResult(null, java.util.List.of(new RepresentableImportDiagnostic(
                    candidate.externalId(), Representability.NONE, "TARGET_ELEMENT_MISSING",
                    "The external element '" + candidate.qualifiedName() + "' was configured to reuse an existing HYDRA element, but no target element ID was provided.",
                    "Return to the guided import review and choose an existing compatible metamodel element.")));
        }
        String neutralPath = "MAP_EXISTING".equals(resolution) ? targetElementId : candidate.qualifiedName();
        ExternalChangeKind effectiveChange = "MAP_EXISTING".equals(resolution) && candidate.changeKind() == ExternalChangeKind.ADD
                ? ExternalChangeKind.UPDATE : candidate.changeKind();
        NeutralChangeOperation operation = new NeutralChangeOperation(candidate.externalId(), effectiveChange, neutralKind, neutralPath, values);
        if(partialKinds.contains(token)){
            return new RepresentableMappingResult(operation, java.util.List.of(new RepresentableImportDiagnostic(
                    candidate.externalId(), Representability.PARTIAL, "REPRESENTATION_PARTIAL",
                    "External element '" + candidate.qualifiedName() + "' can be represented as " + neutralKind + ", but some source-specific semantics have no neutral equivalent.",
                    "Review the generated values and destination. Keep the source construct native or add a custom HYDRA mapper if the omitted semantics are required.")));
        }
        String destination = "MAP_EXISTING".equals(resolution)
                ? " It will be reconciled with existing HYDRA element '" + targetElementId + "'."
                : " It will use neutral destination '" + neutralPath + "'.";
        return new RepresentableMappingResult(operation, java.util.List.of(new RepresentableImportDiagnostic(
                candidate.externalId(), Representability.FULL, "REPRESENTATION_FULL",
                "External element '" + candidate.qualifiedName() + "' is fully representable as " + neutralKind + "." + destination,
                "Review the destination and incoming properties; no compatibility correction is required when they are correct.")));
    }

    /**
     * Descripción: Convierte un valor opcional de metadatos de importación a texto normalizado.
     * Entradas: value: valor que puede contener una cadena o cualquier otro tipo.
     * Salida: devuelve la cadena recortada cuando el valor es texto o una cadena vacía en caso contrario.
     */
    /** Descripción: Ejecuta la operación «stringValue» correspondiente a la responsabilidad de AbstractCataloguedRepresentableMapper. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String stringValue(Object value){return value instanceof String text ? text.trim() : "";}
}
