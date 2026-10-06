package com.example.editor.integration;
import java.util.List;
import java.util.Map;
import java.util.Set;
/** Capacidades declaradas para una tecnología sin cargar sus librerías. */
public record IntegrationTechnologyDescriptor(IntegrationTechnology technology, String displayName, IntegrationTargetKind targetKind,
        IntegrationDeliveryMode recommendedDeliveryMode, InjectionStrategy recommendedInjectionStrategy,
        IntegrationBuildSystem recommendedBuildSystem, Set<IntegrationDeliveryMode> deliveryModes, Set<InjectionStrategy> injectionStrategies,
        Set<IntegrationLocatorStrategy> locatorStrategies, Map<IntegrationCapability, IntegrationSupportLevel> capabilities,
        List<String> notes) {
    public IntegrationTechnologyDescriptor {
        if(technology == null || targetKind == null) throw new IllegalArgumentException("technology and targetKind are required");
        displayName = displayName == null || displayName.isBlank() ? technology.name() : displayName.trim();
        deliveryModes = deliveryModes == null ? Set.of() : Set.copyOf(deliveryModes);
        injectionStrategies = injectionStrategies == null ? Set.of() : Set.copyOf(injectionStrategies);
        locatorStrategies = locatorStrategies == null ? Set.of() : Set.copyOf(locatorStrategies);
        capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
        notes = notes == null ? List.of() : List.copyOf(notes);
        if(recommendedDeliveryMode == null || !deliveryModes.contains(recommendedDeliveryMode))
            throw new IllegalArgumentException("recommendedDeliveryMode must be supported by " + technology);
        if(recommendedInjectionStrategy == null || !injectionStrategies.contains(recommendedInjectionStrategy))
            throw new IllegalArgumentException("recommendedInjectionStrategy must be supported by " + technology);
        if(recommendedBuildSystem == null) throw new IllegalArgumentException("recommendedBuildSystem is required");
    }
    /** Descripción: Ejecuta la operación «support» correspondiente a la responsabilidad de IntegrationTechnologyDescriptor. Entradas: capability: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public IntegrationSupportLevel support(IntegrationCapability capability) { return capabilities.getOrDefault(capability, IntegrationSupportLevel.UNSUPPORTED); }
}
