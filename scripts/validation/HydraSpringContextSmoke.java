import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.example.editor.EditorApplication;
import com.example.editor.event.diagram.EventArchitectureDiagramService;
import com.example.editor.event.flow.EventFlowApplicationService;
import com.example.editor.event.flow.diagram.EventFlowDiagramService;
import com.example.editor.event.flow.export.EventArchitectureExportService;
import com.example.editor.event.flow.runtime.EventFlowRuntimeService;
import com.example.editor.gamification.GamificationApplicationService;
import com.example.editor.gamification.diagram.GamificationDiagramService;
import com.example.editor.gamification.export.GamificationExportService;
import com.example.editor.gamification.runtime.GamificationRuntimeService;
import com.example.editor.project.ProjectLayout;
import com.example.editor.project.storage.ProjectWorkspaceContributionRegistry;
import com.example.editor.projection.drawio.DrawioArtifactExporter;

/** Abre el contexto web real para detectar dependencias, beans, SPI y colisiones REST. */
public final class HydraSpringContextSmoke {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("hydra-next-context-");
        SpringApplication application = new SpringApplication(EditorApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setDefaultProperties(Map.ofEntries(
                Map.entry("server.port", "0"),
                Map.entry("spring.main.banner-mode", "off"),
                Map.entry("logging.level.root", "WARN"),
                Map.entry("dsl.projects.local.native-path-picker-enabled", "false"),
                Map.entry("dsl.storage.metamodels", root.resolve("metamodels").toString()),
                Map.entry("dsl.storage.models", root.resolve("models").toString()),
                Map.entry("dsl.storage.notations", root.resolve("notations").toString()),
                Map.entry("dsl.storage.validations", root.resolve("validations").toString()),
                Map.entry("dsl.storage.quick-fixes", root.resolve("quickfixes").toString()),
                Map.entry("dsl.storage.generators", root.resolve("generators").toString()),
                Map.entry("dsl.storage.assets", root.resolve("assets").toString()),
                Map.entry("dsl.storage.exports", root.resolve("exports").toString()),
                Map.entry("dsl.events.directory", root.resolve("events").toString()),
                Map.entry("dsl.events.flows.directory", root.resolve("event-flows").toString()),
                Map.entry("dsl.editors.directory", root.resolve("editors").toString()),
                Map.entry("dsl.integrations.directory", root.resolve("integrations").toString()),
                Map.entry("dsl.gamification.directory", root.resolve("gamification").toString()),
                Map.entry("dsl.projects.local.registry-directory", root.resolve("projects").toString())
        ));
        try(ConfigurableApplicationContext context = application.run("--server.port=0")) {
            context.getBean(EventArchitectureDiagramService.class);
            check(true,"event architecture diagram service");
            context.getBean(EventFlowApplicationService.class);
            context.getBean(EventFlowDiagramService.class);
            context.getBean(EventFlowRuntimeService.class);
            context.getBean(EventArchitectureExportService.class);
            context.getBean(GamificationApplicationService.class);
            context.getBean(GamificationDiagramService.class);
            check(true,"gamification diagram service");
            context.getBean(GamificationRuntimeService.class);
            context.getBean(GamificationExportService.class);

            Set<String> drawioTypes=context.getBeansOfType(DrawioArtifactExporter.class).values().stream()
                    .map(DrawioArtifactExporter::artifactType).collect(Collectors.toSet());
            check(drawioTypes.containsAll(Set.of("event-catalog","event-bindings","event-flow",
                    "gamification-definition","gamification-bindings","gamification-ledger")),"draw.io modular exporters");

            ProjectWorkspaceContributionRegistry registry=context.getBean(ProjectWorkspaceContributionRegistry.class);
            var snapshot=registry.snapshot(ProjectLayout.standard());
            check(snapshot.contributions().stream().anyMatch(value -> value.id().equals("event-flows")),"event flow project contribution");
            check(snapshot.contributions().stream().anyMatch(value -> value.id().equals("gamification-definitions")),"gamification project contribution");
            check(snapshot.contributions().stream().anyMatch(value -> value.id().equals("event-architecture-diagrams")),"event architecture diagram project contribution");
            check(snapshot.contributions().stream().anyMatch(value -> value.id().equals("event-flow-diagrams")),"event flow diagram project contribution");
            check(snapshot.contributions().stream().anyMatch(value -> value.id().equals("gamification-diagrams")),"gamification diagram project contribution");

            RequestMappingHandlerMapping mappings=context.getBean("requestMappingHandlerMapping",RequestMappingHandlerMapping.class);
            String routes=mappings.getHandlerMethods().keySet().stream().map(Object::toString).collect(Collectors.joining("\n"));
            check(routes.contains("/api/canonical-metamodels/{metamodelId}/event-flows"),"event flow REST mapping");
            check(routes.contains("/api/canonical-metamodels/{metamodelId}/gamification"),"gamification REST mapping");
            check(routes.contains("/api/canonical-metamodels/{metamodelId}/events/diagram"),"event architecture diagram REST mapping");
            check(routes.contains("/api/canonical-metamodels/{metamodelId}/event-flows/diagram"),"event flow diagram REST mapping");
            check(routes.contains("/api/canonical-metamodels/{metamodelId}/gamification/diagram"),"gamification diagram REST mapping");
            check(routes.contains("/runtime/events"),"flow runtime REST mapping");
            System.out.println("HYDRA_SPRING_CONTEXT_OK " + checks + "/" + checks);
        }
    }

    private static void check(boolean condition,String message) {
        checks++;
        if(!condition) throw new IllegalStateException("Context smoke failed: "+message);
    }
}
