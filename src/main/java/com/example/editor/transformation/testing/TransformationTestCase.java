package com.example.editor.transformation.testing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Caso de prueba persistente y neutral para M2M, M2T o T2M. */
public record TransformationTestCase(
        String schemaVersion,
        String id,
        String transformationId,
        String name,
        boolean enabled,
        CanonicalModelDocument sourceModel,
        String sourceText,
        CanonicalModelDocument expectedModel,
        List<TransformationExpectedArtifact> expectedArtifacts,
        Map<String, String> annotations,
        TransformationExecutionDirection executionDirection) {

    public static final String CURRENT_SCHEMA_VERSION = "1.1";
    public static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("1.0", CURRENT_SCHEMA_VERSION);

    public TransformationTestCase {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? CURRENT_SCHEMA_VERSION : schemaVersion.trim();
        id = text(id);
        transformationId = text(transformationId);
        name = text(name);
        sourceText = sourceText == null ? "" : sourceText;
        expectedArtifacts = expectedArtifacts == null ? List.of() : List.copyOf(expectedArtifacts);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
        executionDirection = executionDirection == null ? TransformationExecutionDirection.FORWARD : executionDirection;
    }

    /** Compatibilidad con casos de prueba 1.0, que siempre ejecutaban M2M forward. */
    /** Descripción: Inicializa una instancia de TransformationTestCase con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; transformationId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; enabled: dato de entrada necesario para la operación; sourceModel: dato de entrada necesario para la operación; sourceText: dato de entrada necesario para la operación; expectedModel: dato de entrada necesario para la operación; expectedArtifacts: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationTestCase(String schemaVersion, String id, String transformationId, String name, boolean enabled,
            CanonicalModelDocument sourceModel, String sourceText, CanonicalModelDocument expectedModel,
            List<TransformationExpectedArtifact> expectedArtifacts, Map<String, String> annotations) {
        this(schemaVersion, id, transformationId, name, enabled, sourceModel, sourceText, expectedModel,
                expectedArtifacts, annotations, TransformationExecutionDirection.FORWARD);
    }

    /** Descripción: Normaliza un texto del contrato de pruebas. Entradas: value: valor de entrada. Salida: devuelve texto no nulo y sin espacios externos. */
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TransformationTestCase. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
