import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.diagram.EventArchitectureDiagramDocument;
import com.example.editor.event.diagram.EventArchitectureDiagramEdgeView;
import com.example.editor.event.diagram.EventArchitectureDiagramNodeView;
import com.example.editor.event.diagram.JsonEventArchitectureDiagramRepository;
import com.example.editor.gamification.GamificationProperties;
import com.example.editor.gamification.diagram.GamificationDiagramDocument;
import com.example.editor.gamification.diagram.GamificationDiagramEdgeView;
import com.example.editor.gamification.diagram.GamificationDiagramNodeView;
import com.example.editor.gamification.diagram.JsonGamificationDiagramRepository;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import tools.jackson.databind.json.JsonMapper;

/** Comprueba sin JUnit que las geometrías visuales son documentos separados y concurrentes por ETag. */
public final class HydraVisualDiagramPersistenceSmoke {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("hydra-visual-diagrams-");
        var mapper = JsonMapper.builder().findAndAddModules().build();

        EventModelProperties eventProperties = new EventModelProperties();
        eventProperties.setDirectory(root.resolve("events"));
        var eventRepository = new JsonEventArchitectureDiagramRepository(eventProperties, mapper);
        EventArchitectureDiagramDocument eventDiagram = new EventArchitectureDiagramDocument("1.0", "people-event-diagram", "people",
                "people-events", "people-event-bindings",
                List.of(new EventArchitectureDiagramNodeView("core.model.saved", 20, 30, 230, 92, false)),
                List.of(new EventArchitectureDiagramEdgeView("binding:quality", List.of())));
        var eventCreated = eventRepository.create(eventDiagram);
        check(Files.isRegularFile(root.resolve("events/people.event-architecture-diagram.json")), "event diagram file");
        check(eventRepository.find("people").orElseThrow().document().equals(eventDiagram), "event diagram round trip");
        check(eventCreated.etag().matches("\"[0-9a-f]{64}\""), "event diagram etag");
        var eventReplaced = eventRepository.replace("people", eventDiagram, eventCreated.etag());
        check(eventReplaced.etag().equals(eventCreated.etag()), "same event bytes preserve etag");
        check(stale(() -> eventRepository.replace("people", eventDiagram, "\"stale\"")), "event stale etag rejected");

        GamificationProperties gamificationProperties = new GamificationProperties();
        gamificationProperties.setDirectory(root.resolve("gamification"));
        var gamificationRepository = new JsonGamificationDiagramRepository(gamificationProperties, mapper);
        GamificationDiagramDocument gamificationDiagram = new GamificationDiagramDocument("1.0", "people-gamification-diagram", "people",
                "people-gamification", "people-gamification-bindings",
                List.of(new GamificationDiagramNodeView("rule:quality", 40, 50, 210, 84, false)),
                List.of(new GamificationDiagramEdgeView("rule-points:quality", List.of())));
        var gamificationCreated = gamificationRepository.create(gamificationDiagram);
        check(Files.isRegularFile(root.resolve("gamification/people.gamification-diagram.json")), "gamification diagram file");
        check(gamificationRepository.find("people").orElseThrow().document().equals(gamificationDiagram), "gamification diagram round trip");
        check(gamificationCreated.etag().matches("\"[0-9a-f]{64}\""), "gamification diagram etag");
        check(stale(() -> gamificationRepository.replace("people", gamificationDiagram, "\"stale\"")), "gamification stale etag rejected");
        gamificationRepository.delete("people", gamificationCreated.etag());
        check(gamificationRepository.find("people").isEmpty(), "gamification diagram deleted");
        eventRepository.delete("people", eventReplaced.etag());
        check(eventRepository.find("people").isEmpty(), "event diagram deleted");
        check(eventDiagram.nodes().getFirst().width() == 230, "event node width retained");
        check(gamificationDiagram.nodes().getFirst().height() == 84, "gamification node height retained");
        check(eventDiagram.edges().getFirst().points().isEmpty(), "event edge route optional");
        check(gamificationDiagram.edges().getFirst().points().isEmpty(), "gamification edge route optional");
        System.out.println("HYDRA_VISUAL_DIAGRAM_PERSISTENCE_OK " + checks + "/" + checks);
    }
    private static boolean stale(Runnable operation) {
        try { operation.run(); return false; }
        catch(CanonicalDocumentStoreException exception) { return exception.getReason() == CanonicalDocumentStoreException.Reason.PRECONDITION_FAILED; }
    }
    private static void check(boolean condition, String message) {
        checks++;
        if(!condition) throw new IllegalStateException("Visual diagram smoke failed: " + message);
    }
}
