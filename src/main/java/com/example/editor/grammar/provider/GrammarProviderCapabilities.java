package com.example.editor.grammar.provider;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable negotiated capabilities.
 *
 * <p>HYDRA deliberately separates features callable by the running HYDRA provider
 * ({@code liveFeatures}) from features materialized into a generated native host
 * ({@code generatedHostFeatures}).  This prevents the UI/API from advertising a
 * completion/hover/LSP operation as live merely because a generated project is
 * capable of providing it after it is built and started.</p>
 */
public record GrammarProviderCapabilities(Set<GrammarProviderCapability> features,
        List<String> supportedTargets, List<String> supportedBuildSystems,
        Set<GrammarProviderCapability> liveFeatures,
        Set<GrammarProviderCapability> generatedHostFeatures) {

    /** Compatibility constructor used by older descriptors: every supplied feature is live. */
    /** Descripción: Inicializa una instancia de GrammarProviderCapabilities con sus dependencias y datos de entrada. Entradas: features: dato de entrada necesario para la operación; supportedTargets: dato de entrada necesario para la operación; supportedBuildSystems: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarProviderCapabilities(Set<GrammarProviderCapability> features,
            List<String> supportedTargets, List<String> supportedBuildSystems) {
        this(features, supportedTargets, supportedBuildSystems, features, Set.of());
    }

    public GrammarProviderCapabilities {
        liveFeatures = immutable(liveFeatures);
        generatedHostFeatures = immutable(generatedHostFeatures);
        Set<GrammarProviderCapability> declared = immutable(features);
        LinkedHashSet<GrammarProviderCapability> union = new LinkedHashSet<>();
        union.addAll(declared);
        union.addAll(liveFeatures);
        union.addAll(generatedHostFeatures);
        features = immutable(union);
        supportedTargets = supportedTargets == null ? List.of() : supportedTargets.stream()
                .map(GrammarProviderCapabilities::text).filter(value -> !value.isBlank()).distinct().toList();
        supportedBuildSystems = supportedBuildSystems == null ? List.of() : supportedBuildSystems.stream()
                .map(GrammarProviderCapabilities::text).filter(value -> !value.isBlank()).distinct().toList();
    }

    /** True only when HYDRA can invoke the capability now through this provider instance. */
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: capability: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean supports(GrammarProviderCapability capability) { return supportsLive(capability); }
    /** Descripción: Evalúa la condición «supportsLive» sin modificar el estado externo. Entradas: capability: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean supportsLive(GrammarProviderCapability capability) { return capability != null && liveFeatures.contains(capability); }
    /** Descripción: Evalúa la condición «supportsGeneratedHost» sin modificar el estado externo. Entradas: capability: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean supportsGeneratedHost(GrammarProviderCapability capability) { return capability != null && generatedHostFeatures.contains(capability); }
    /** Descripción: Ejecuta la operación «declares» correspondiente a la responsabilidad de GrammarProviderCapabilities. Entradas: capability: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean declares(GrammarProviderCapability capability) { return capability != null && features.contains(capability); }
    /** Descripción: Ejecuta la operación «availability» correspondiente a la responsabilidad de GrammarProviderCapabilities. Entradas: capability: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String availability(GrammarProviderCapability capability) {
        if (supportsLive(capability)) return "LIVE";
        if (supportsGeneratedHost(capability)) return "GENERATED_HOST";
        return "UNAVAILABLE";
    }
    /** Descripción: Ejecuta la operación «featureNames» correspondiente a la responsabilidad de GrammarProviderCapabilities. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> featureNames() { return features.stream().map(Enum::name).sorted().toList(); }
    /** Descripción: Ejecuta la operación «liveFeatureNames» correspondiente a la responsabilidad de GrammarProviderCapabilities. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> liveFeatureNames() { return liveFeatures.stream().map(Enum::name).sorted().toList(); }
    /** Descripción: Crea o prepara el resultado de «generatedHostFeatureNames» dentro de GrammarProviderCapabilities. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> generatedHostFeatureNames() { return generatedHostFeatures.stream().map(Enum::name).sorted().toList(); }

    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de GrammarProviderCapabilities. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Set<GrammarProviderCapability> immutable(Set<GrammarProviderCapability> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarProviderCapabilities. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
