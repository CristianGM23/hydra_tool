package com.example.editor.portability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Manifiesto reproducible de un conjunto portable de capacidades HYDRA. */
public record HydraCapabilityPackageManifest(String schemaVersion, String id, String name, String packageVersion,
        String metamodelId, String semanticScopeId, MetamodelOrigin metamodelOrigin, List<CapabilitySelection> capabilities,
        List<CapabilityDependency> dependencies, List<TargetCapabilityCompatibility> compatibility,
        Map<String, String> checksums, Map<String, String> annotations) {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public HydraCapabilityPackageManifest {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? CURRENT_SCHEMA_VERSION : schemaVersion;
        if(!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported capability package schemaVersion: " + schemaVersion);
        id = safe(id);
        name = safe(name);
        packageVersion = safe(packageVersion);
        metamodelId = safe(metamodelId);
        semanticScopeId = safe(semanticScopeId).isBlank() ? metamodelId : safe(semanticScopeId);
        metamodelOrigin = metamodelOrigin == null ? new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of()) : metamodelOrigin;
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        compatibility = compatibility == null ? List.of() : List.copyOf(compatibility);
        checksums = immutable(checksums);
        annotations = immutable(annotations);
    }

    /** Descripción: Mantiene compatibilidad con manifiestos anteriores a semanticScopeId. Entradas: contrato histórico completo. Salida: usa metamodelId como ámbito semántico. */
    /** Descripción: Inicializa una instancia de HydraCapabilityPackageManifest con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; packageVersion: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; metamodelOrigin: dato de entrada necesario para la operación; capabilities: dato de entrada necesario para la operación; dependencies: dato de entrada necesario para la operación; compatibility: dato de entrada necesario para la operación; checksums: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraCapabilityPackageManifest(String schemaVersion, String id, String name, String packageVersion,
            String metamodelId, MetamodelOrigin metamodelOrigin, List<CapabilitySelection> capabilities,
            List<CapabilityDependency> dependencies, List<TargetCapabilityCompatibility> compatibility,
            Map<String, String> checksums, Map<String, String> annotations) {
        this(schemaVersion, id, name, packageVersion, metamodelId, metamodelId, metamodelOrigin, capabilities,
                dependencies, compatibility, checksums, annotations);
    }

    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de HydraCapabilityPackageManifest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de HydraCapabilityPackageManifest. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutable(Map<String, String> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
