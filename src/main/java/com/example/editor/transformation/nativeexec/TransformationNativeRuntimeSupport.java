package com.example.editor.transformation.nativeexec;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportLanguage;

/**
 * Single source of truth for transformation toolchain capabilities.
 * Keeping this policy out of controllers, executors and parity services prevents
 * technology lists and semantic guarantees from drifting apart.
 */
public final class TransformationNativeRuntimeSupport {
    private static final Map<TransformationExportLanguage, Capability> CAPABILITIES;

    static {
        EnumMap<TransformationExportLanguage, Capability> capabilities =
                new EnumMap<>(TransformationExportLanguage.class);
        capabilities.put(TransformationExportLanguage.ATL,
                nativeM2mDirectionalCapability());
        capabilities.put(TransformationExportLanguage.ETL,
                nativeM2mDirectionalCapability());
        capabilities.put(TransformationExportLanguage.ACCELEO,
                nativeForwardCapability(TransformationKind.M2T));
        capabilities.put(TransformationExportLanguage.EGL,
                nativeForwardCapability(TransformationKind.M2T));
        capabilities.put(TransformationExportLanguage.JAVA,
                nativeCodeM2mM2tCapability());
        capabilities.put(TransformationExportLanguage.XTEND,
                nativeCodeM2mM2tCapability());
        capabilities.put(TransformationExportLanguage.PYTHON,
                nativeCodeM2mM2tCapability());
        capabilities.put(TransformationExportLanguage.GO,
                nativeCodeM2mM2tCapability());
        capabilities.put(TransformationExportLanguage.JAVASCRIPT,
                nativeCodeM2mM2tCapability());
        capabilities.put(TransformationExportLanguage.TYPESCRIPT,
                nativeCodeM2mM2tCapability());
        CAPABILITIES = Map.copyOf(capabilities);
    }

    /** Descripción: Inicializa una instancia de TransformationNativeRuntimeSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TransformationNativeRuntimeSupport() {}

    /** Returns every transformation language that participates in managed runtime discovery. */
    /** Descripción: Ejecuta la operación «languages» correspondiente a la responsabilidad de TransformationNativeRuntimeSupport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static List<TransformationExportLanguage> languages() {
        return CAPABILITIES.keySet().stream()
                .sorted(Comparator.comparing(TransformationExportLanguage::name))
                .toList();
    }

    /** Returns whether HYDRA has a managed runtime capability definition for the language. */
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean supports(TransformationExportLanguage language) {
        return language != null && CAPABILITIES.containsKey(language);
    }

    /** Returns whether the language can represent the requested transformation family. */
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: language: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean supports(TransformationExportLanguage language, TransformationKind kind) {
        return language != null && kind != null && supportedKinds(language).contains(kind);
    }

    /** Returns whether the runtime provides evidence independent from the canonical HYDRA runtime. */
    /** Descripción: Evalúa la condición «supportsIndependentParity» sin modificar el estado externo. Entradas: language: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean supportsIndependentParity(TransformationExportLanguage language, TransformationKind kind) {
        return supports(language, kind) && mode(language) == TransformationNativeRuntimeMode.NATIVE_EXPORT;
    }

    /** Returns whether independent native parity can be executed for one explicit direction. */
    /** Descripción: Evalúa la condición «supportsDirection» sin modificar el estado externo. Entradas: language: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; direction: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean supportsDirection(TransformationExportLanguage language, TransformationKind kind,
            TransformationExecutionDirection direction) {
        if (!supportsIndependentParity(language, kind) || direction == null) return false;
        if (kind != TransformationKind.M2M && direction == TransformationExecutionDirection.REVERSE) return false;
        return supportedDirections(language).contains(direction);
    }

    /** Returns the transformation families advertised by the managed runtime. */
    /** Descripción: Ejecuta la operación «supportedKinds» correspondiente a la responsabilidad de TransformationNativeRuntimeSupport. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Set<TransformationKind> supportedKinds(TransformationExportLanguage language) {
        Capability capability = CAPABILITIES.get(language);
        return capability == null ? Set.of() : EnumSet.copyOf(capability.kinds());
    }

    /** Returns directions for which the runtime can produce independent parity evidence. */
    /** Descripción: Ejecuta la operación «supportedDirections» correspondiente a la responsabilidad de TransformationNativeRuntimeSupport. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Set<TransformationExecutionDirection> supportedDirections(TransformationExportLanguage language) {
        Capability capability = CAPABILITIES.get(language);
        if (capability == null || capability.directions().isEmpty()) return Set.of();
        return EnumSet.copyOf(capability.directions());
    }

    /** Returns the runtime integration mode used by the export/toolchain family. */
    /** Descripción: Ejecuta la operación «mode» correspondiente a la responsabilidad de TransformationNativeRuntimeSupport. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static TransformationNativeRuntimeMode mode(TransformationExportLanguage language) {
        Capability capability = CAPABILITIES.get(language);
        return capability == null ? TransformationNativeRuntimeMode.CANONICAL_BUNDLE : capability.mode();
    }

    /** Full synchronization parity remains false until a bridge preserves both sides and correspondence state in one run. */
    /** Descripción: Evalúa la condición «supportsFullBidirectionalSynchronization» sin modificar el estado externo. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean supportsFullBidirectionalSynchronization(TransformationExportLanguage language) {
        Capability capability = CAPABILITIES.get(language);
        return capability != null && capability.fullBidirectionalSynchronization();
    }

    /** Incremental external synchronization remains false until a bridge consumes and returns managed baseline/trace evidence. */
    /** Descripción: Evalúa la condición «supportsIncrementalSynchronization» sin modificar el estado externo. Entradas: language: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean supportsIncrementalSynchronization(TransformationExportLanguage language) {
        Capability capability = CAPABILITIES.get(language);
        return capability != null && capability.incrementalSynchronization();
    }

    /** Human-readable list used in bounded parity diagnostics. */
    /** Descripción: Ejecuta la operación «nativeParityLanguageSummary» correspondiente a la responsabilidad de TransformationNativeRuntimeSupport. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String nativeParityLanguageSummary(TransformationKind kind) {
        List<String> names = languages().stream()
                .filter(language -> supportsIndependentParity(language, kind))
                .map(Enum::name).toList();
        if (names.isEmpty()) return "none";
        if (names.size() == 1) return names.getFirst();
        return String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.getLast();
    }

    /** Descripción: Ejecuta la operación «nativeM2mDirectionalCapability» correspondiente a la responsabilidad de TransformationNativeRuntimeSupport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static Capability nativeM2mDirectionalCapability() {
        return new Capability(TransformationNativeRuntimeMode.NATIVE_EXPORT,
                EnumSet.of(TransformationKind.M2M),
                EnumSet.of(TransformationExecutionDirection.FORWARD, TransformationExecutionDirection.REVERSE),
                false, false);
    }

    /** Java/Python/Go/JavaScript/TypeScript execute M2M in both explicit directions and M2T in FORWARD only. */
    /** Descripción: Ejecuta la operación «nativeCodeM2mM2tCapability» correspondiente a la responsabilidad de Capability. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static Capability nativeCodeM2mM2tCapability() {
        return new Capability(TransformationNativeRuntimeMode.NATIVE_EXPORT,
                EnumSet.of(TransformationKind.M2M, TransformationKind.M2T),
                EnumSet.of(TransformationExecutionDirection.FORWARD, TransformationExecutionDirection.REVERSE),
                false, false);
    }

    /** Descripción: Ejecuta la operación «nativeForwardCapability» correspondiente a la responsabilidad de TransformationNativeRuntimeSupport. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Capability nativeForwardCapability(TransformationKind kind) {
        return new Capability(TransformationNativeRuntimeMode.NATIVE_EXPORT,
                EnumSet.of(kind), EnumSet.of(TransformationExecutionDirection.FORWARD), false, false);
    }

    /** Descripción: Evalúa la condición «canonicalCapability» sin modificar el estado externo. Entradas: kinds: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static Capability canonicalCapability(TransformationKind... kinds) {
        return new Capability(TransformationNativeRuntimeMode.CANONICAL_BUNDLE,
                EnumSet.copyOf(List.of(kinds)), EnumSet.noneOf(TransformationExecutionDirection.class), false, false);
    }

    private record Capability(
            TransformationNativeRuntimeMode mode,
            Set<TransformationKind> kinds,
            Set<TransformationExecutionDirection> directions,
            boolean fullBidirectionalSynchronization,
            boolean incrementalSynchronization) {}
}
