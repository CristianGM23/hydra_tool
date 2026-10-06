package com.example.editor.transformation.nativeexec;

import java.util.List;

import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportLanguage;

/** Self-description emitted by a native bridge when HYDRA invokes --hydra-describe. */
public record TransformationNativeBridgeDescription(
        String schemaVersion,
        TransformationExportLanguage language,
        String bridgeName,
        String bridgeVersion,
        String toolchainName,
        String toolchainVersion,
        List<String> protocolVersions,
        List<TransformationKind> supportedKinds,
        List<TransformationExecutionDirection> supportedDirections,
        boolean fullBidirectionalSynchronization,
        boolean incrementalSynchronization,
        boolean toolAvailable,
        boolean executionAvailable,
        List<String> diagnostics) {
    /** Descripción: Normaliza de forma defensiva la autodescripción emitida por un bridge nativo, conservando colecciones inmutables y valores textuales limpios. Entradas: componentes del record de autodescripción. Salida: construye una descripción canónica lista para validación. */
    public TransformationNativeBridgeDescription {
        schemaVersion = clean(schemaVersion);
        bridgeName = clean(bridgeName);
        bridgeVersion = clean(bridgeVersion);
        toolchainName = clean(toolchainName);
        toolchainVersion = clean(toolchainVersion);
        protocolVersions = cleanStrings(protocolVersions);
        supportedKinds = supportedKinds == null ? List.of() : List.copyOf(supportedKinds);
        supportedDirections = supportedDirections == null ? List.of() : List.copyOf(supportedDirections);
        diagnostics = cleanStrings(diagnostics);
    }

    /** Descripción: Construye una autodescripción compatible con bridges del esquema 1.0, asumiendo disponibilidad de herramienta y ejecución como en el contrato legado. Entradas: identidad, versiones, protocolos y capacidades del bridge. Salida: descripción normalizada equivalente en el modelo actual. */
    /** Descripción: Inicializa una instancia de TransformationNativeBridgeDescription con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; language: dato de entrada necesario para la operación; bridgeName: dato de entrada necesario para la operación; bridgeVersion: dato de entrada necesario para la operación; toolchainName: dato de entrada necesario para la operación; toolchainVersion: dato de entrada necesario para la operación; protocolVersions: dato de entrada necesario para la operación; supportedKinds: dato de entrada necesario para la operación; supportedDirections: dato de entrada necesario para la operación; fullBidirectionalSynchronization: dato de entrada necesario para la operación; incrementalSynchronization: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationNativeBridgeDescription(String schemaVersion, TransformationExportLanguage language,
            String bridgeName, String bridgeVersion, String toolchainName, String toolchainVersion,
            List<String> protocolVersions, List<TransformationKind> supportedKinds,
            List<TransformationExecutionDirection> supportedDirections, boolean fullBidirectionalSynchronization,
            boolean incrementalSynchronization) {
        this(schemaVersion, language, bridgeName, bridgeVersion, toolchainName, toolchainVersion,
                protocolVersions, supportedKinds, supportedDirections, fullBidirectionalSynchronization,
                incrementalSynchronization, true, true, List.of());
    }

    /** Descripción: Ejecuta la operación «clean» correspondiente a la responsabilidad de TransformationNativeBridgeDescription. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    /** Descripción: Ejecuta la operación «cleanStrings» correspondiente a la responsabilidad de TransformationNativeBridgeDescription. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> cleanStrings(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
    }
}
