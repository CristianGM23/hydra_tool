package com.example.editor.transformation.nativeexec;

import java.util.List;

import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportLanguage;

/** Safe public capability description; command paths and arguments remain server-side. */
public record TransformationNativeRuntimeDescriptor(
        TransformationExportLanguage language,
        boolean enabled,
        boolean configured,
        boolean parityReady,
        TransformationNativeRuntimeMode mode,
        String protocolVersion,
        List<TransformationKind> supportedKinds,
        List<TransformationExecutionDirection> supportedDirections,
        boolean fullBidirectionalSynchronization,
        boolean incrementalSynchronization,
        TransformationNativeRuntimeSource source,
        TransformationNativeRuntimeHealth health) {
    public TransformationNativeRuntimeDescriptor {
        mode = mode == null ? TransformationNativeRuntimeMode.CANONICAL_BUNDLE : mode;
        protocolVersion = protocolVersion == null || protocolVersion.isBlank() ? "1.0" : protocolVersion;
        supportedKinds = supportedKinds == null ? List.of() : List.copyOf(supportedKinds);
        supportedDirections = supportedDirections == null ? List.of() : List.copyOf(supportedDirections);
        source = source == null ? TransformationNativeRuntimeSource.NONE : source;
        health = health == null ? TransformationNativeRuntimeHealth.UNVERIFIED : health;
    }

    /** Backward-compatible constructor for callers compiled against the Fix42 descriptor shape. */
    /** Descripción: Inicializa una instancia de TransformationNativeRuntimeDescriptor con sus dependencias y datos de entrada. Entradas: language: dato de entrada necesario para la operación; enabled: dato de entrada necesario para la operación; configured: configuración que gobierna el comportamiento; parityReady: dato de entrada necesario para la operación; mode: dato de entrada necesario para la operación; protocolVersion: dato de entrada necesario para la operación; supportedKinds: dato de entrada necesario para la operación; supportedDirections: dato de entrada necesario para la operación; fullBidirectionalSynchronization: dato de entrada necesario para la operación; incrementalSynchronization: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationNativeRuntimeDescriptor(TransformationExportLanguage language, boolean enabled, boolean configured,
            boolean parityReady, TransformationNativeRuntimeMode mode, String protocolVersion,
            List<TransformationKind> supportedKinds, List<TransformationExecutionDirection> supportedDirections,
            boolean fullBidirectionalSynchronization, boolean incrementalSynchronization) {
        this(language, enabled, configured, parityReady, mode, protocolVersion, supportedKinds,
                supportedDirections, fullBidirectionalSynchronization, incrementalSynchronization,
                configured ? TransformationNativeRuntimeSource.EXPLICIT_COMMAND : TransformationNativeRuntimeSource.NONE,
                enabled ? (configured ? TransformationNativeRuntimeHealth.UNVERIFIED : TransformationNativeRuntimeHealth.NOT_CONFIGURED)
                        : TransformationNativeRuntimeHealth.DISABLED);
    }

    /** Backward-compatible constructor for callers compiled against the Fix36 descriptor shape. */
    /** Descripción: Inicializa una instancia de TransformationNativeRuntimeDescriptor con sus dependencias y datos de entrada. Entradas: language: dato de entrada necesario para la operación; enabled: dato de entrada necesario para la operación; configured: configuración que gobierna el comportamiento; parityReady: dato de entrada necesario para la operación; mode: dato de entrada necesario para la operación; protocolVersion: dato de entrada necesario para la operación; supportedKinds: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationNativeRuntimeDescriptor(TransformationExportLanguage language, boolean enabled, boolean configured,
            boolean parityReady, TransformationNativeRuntimeMode mode, String protocolVersion,
            List<TransformationKind> supportedKinds) {
        this(language, enabled, configured, parityReady, mode, protocolVersion, supportedKinds,
                List.of(), false, false);
    }
}
