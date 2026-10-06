package com.example.editor.grammar.provider;

import java.util.List;
import java.util.Map;
import com.example.editor.grammar.core.GrammarAnalysisMode;
import com.example.editor.grammar.core.GrammarTechnology;

/** Stable descriptor consumed by backend and frontend capability negotiation. */
public record GrammarProviderDescriptor(String id, GrammarTechnology technology, String displayName, String version,
        GrammarProviderCapabilities capabilityDescriptor, List<String> analysisModes, Map<String,String> metadata) {
    public GrammarProviderDescriptor {
        id = text(id);
        if (id.isBlank()) throw new IllegalArgumentException("Grammar provider id must not be blank");
        if (technology == null) throw new IllegalArgumentException("Grammar provider technology is required");
        displayName = text(displayName);
        version = text(version);
        capabilityDescriptor = capabilityDescriptor == null ? new GrammarProviderCapabilities(java.util.Set.of(), List.of(), List.of()) : capabilityDescriptor;
        analysisModes = analysisModes == null || analysisModes.isEmpty()
                ? java.util.Arrays.stream(GrammarAnalysisMode.values()).map(Enum::name).toList()
                : analysisModes.stream().map(GrammarProviderDescriptor::text).filter(value -> !value.isBlank()).distinct().toList();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Compatibility constructor for code written against the initial V4.32.97 draft. */
    /** Descripción: Inicializa una instancia de GrammarProviderDescriptor con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; technology: dato de entrada necesario para la operación; displayName: dato de entrada necesario para la operación; version: dato de entrada necesario para la operación; capabilities: dato de entrada necesario para la operación; analysisModes: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarProviderDescriptor(String id, GrammarTechnology technology, String displayName, String version,
            List<String> capabilities, List<String> analysisModes, Map<String,String> metadata) {
        this(id, technology, displayName, version,
                new GrammarProviderCapabilities(toFeatures(capabilities), List.of(), List.of()), analysisModes, metadata);
    }

    /** Descripción: Ejecuta la operación «capabilities» correspondiente a la responsabilidad de GrammarProviderDescriptor. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> capabilities() { return capabilityDescriptor.featureNames(); }
    /** Descripción: Ejecuta la operación «supportedTargets» correspondiente a la responsabilidad de GrammarProviderDescriptor. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> supportedTargets() { return capabilityDescriptor.supportedTargets(); }
    /** Descripción: Ejecuta la operación «supportedBuildSystems» correspondiente a la responsabilidad de GrammarProviderDescriptor. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> supportedBuildSystems() { return capabilityDescriptor.supportedBuildSystems(); }
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: capability: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean supports(GrammarProviderCapability capability) { return capabilityDescriptor.supportsLive(capability); }
    /** Descripción: Evalúa la condición «supportsLive» sin modificar el estado externo. Entradas: capability: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean supportsLive(GrammarProviderCapability capability) { return capabilityDescriptor.supportsLive(capability); }
    /** Descripción: Evalúa la condición «supportsGeneratedHost» sin modificar el estado externo. Entradas: capability: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean supportsGeneratedHost(GrammarProviderCapability capability) { return capabilityDescriptor.supportsGeneratedHost(capability); }

    /** Descripción: Ejecuta la operación «toFeatures» correspondiente a la responsabilidad de GrammarProviderDescriptor. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static java.util.Set<GrammarProviderCapability> toFeatures(List<String> values) {
        java.util.EnumSet<GrammarProviderCapability> result = java.util.EnumSet.noneOf(GrammarProviderCapability.class);
        if (values != null) for (String value : values) {
            try { result.add(GrammarProviderCapability.valueOf(text(value).toUpperCase(java.util.Locale.ROOT))); }
            catch (IllegalArgumentException ignored) { /* legacy capability not part of the typed descriptor */ }
        }
        return result;
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarProviderDescriptor. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
