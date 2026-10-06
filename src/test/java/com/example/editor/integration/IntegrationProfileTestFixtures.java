package com.example.editor.integration;
import java.util.List;
import java.util.Map;
import java.util.Set;
public final class IntegrationProfileTestFixtures {
    public static IntegrationProfileModel plantUml() {
        return profile(IntegrationTechnology.PLANTUML, IntegrationTargetKind.DIAGRAM_PROJECTION,
                IntegrationDeliveryMode.PROJECTION_ONLY, IntegrationBuildSystem.NONE,
                InjectionStrategy.GENERATE_PROJECTION,
                Set.of(IntegrationFeature.EVENT_DEFINITION_PROJECTION, IntegrationFeature.PROJECT_EXPORT),
                List.of(new IntegrationArtifactSelection(IntegrationArtifactKind.METAMODEL, "people", true),
                        new IntegrationArtifactSelection(IntegrationArtifactKind.EVENT_DEFINITIONS, "people-events", true)),
                List.of());
    }
    public static IntegrationProfileModel besserSdk() {
        return profile(IntegrationTechnology.BESSER_SDK, IntegrationTargetKind.MODELING_RUNTIME,
                IntegrationDeliveryMode.GENERATED_MODULE, IntegrationBuildSystem.PIP,
                InjectionStrategy.ADD_PACKAGE,
                Set.of(IntegrationFeature.EVENT_PUBLICATION, IntegrationFeature.EVENT_CONSUMPTION,
                        IntegrationFeature.GAMIFICATION_RUNTIME),
                List.of(new IntegrationArtifactSelection(IntegrationArtifactKind.METAMODEL, "people", true),
                        new IntegrationArtifactSelection(IntegrationArtifactKind.EVENT_DEFINITIONS, "people-events", true),
                        new IntegrationArtifactSelection(IntegrationArtifactKind.GAMIFICATION_DEFINITIONS, "people-gamification", false)),
                List.of(mapping(IntegrationLocatorStrategy.BESSER_ELEMENT_ID, "Person")));
    }
    public static IntegrationProfileModel emf() {
        return profile(IntegrationTechnology.EMF, IntegrationTargetKind.MODELING_RUNTIME,
                IntegrationDeliveryMode.GENERATED_MODULE, IntegrationBuildSystem.MAVEN,
                InjectionStrategy.ADD_MODULE,
                Set.of(IntegrationFeature.EVENT_PUBLICATION, IntegrationFeature.VALIDATION_RUNTIME,
                        IntegrationFeature.QUICK_FIX_RUNTIME),
                List.of(new IntegrationArtifactSelection(IntegrationArtifactKind.METAMODEL, "people", true),
                        new IntegrationArtifactSelection(IntegrationArtifactKind.EVENT_DEFINITIONS, "people-events", true),
                        new IntegrationArtifactSelection(IntegrationArtifactKind.VALIDATIONS, "people", true),
                        new IntegrationArtifactSelection(IntegrationArtifactKind.ACTIONS, "people", true)),
                List.of(mapping(IntegrationLocatorStrategy.NS_URI_AND_CLASSIFIER, "Person")));
    }
    public static IntegrationElementMapping mapping(IntegrationLocatorStrategy locator, String hostName) {
        return new IntegrationElementMapping("mapping:person", IntegrationMappingKind.TYPE, "people", "class:Person",
                new HostElementReference(locator, "", "http://example.org/people", hostName, hostName, "", "", Map.of()), true, Map.of());
    }
    private static IntegrationProfileModel profile(IntegrationTechnology technology, IntegrationTargetKind kind,
            IntegrationDeliveryMode delivery, IntegrationBuildSystem build, InjectionStrategy strategy,
            Set<IntegrationFeature> features, List<IntegrationArtifactSelection> artifacts, List<IntegrationElementMapping> mappings) {
        IntegrationTechnologyDescriptor descriptor = IntegrationTechnologyCatalog.descriptor(technology);
        IntegrationTarget target = new IntegrationTarget(technology, kind, "", "people-host", "integration", build,
                List.of(), List.of(), "", Map.of());
        InjectionPolicy policy = new InjectionPolicy(strategy, technology == IntegrationTechnology.PLANTUML ? "docs/diagrams" : "generated-integration",
                "integration", true, true, true, false, false, true);
        return new IntegrationProfileModel("1.0", "people-" + technology.name().toLowerCase().replace('_','-') + "-integration",
                "people", descriptor.displayName(), target, delivery, features, artifacts, mappings, policy, Set.of(), Map.of());
    }
    private IntegrationProfileTestFixtures() { }
}
