package com.example.editor.workbench.capability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.definition.EditorDefinitionApplicationService;
import com.example.editor.definition.EditorDefinitionSummary;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelSummary;
import com.example.editor.event.flow.EventFlowApplicationService;
import com.example.editor.event.flow.EventFlowSummary;
import com.example.editor.gamification.GamificationApplicationService;
import com.example.editor.gamification.GamificationCatalogResult;
import com.example.editor.gamification.GamificationSummary;
import com.example.editor.grammar.application.GrammarApplicationService;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjection;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.integration.IntegrationProfileApplicationService;
import com.example.editor.integration.IntegrationProfileSummary;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.validation.ValidationModelApplicationService;
import com.example.editor.validation.validation.ValidationModelSummary;

/** Aggregates independently portable capability layers for metamodel and grammar semantic contexts. */
@Service
public class HydraCapabilityReadinessService {
    private final CanonicalMetamodelApplicationService metamodels;
    private final GrammarApplicationService grammars;
    private final GrammarClassDiagramProjectionService grammarProjection;
    private final ValidationModelApplicationService validations;
    private final EventModelApplicationService events;
    private final EventFlowApplicationService eventFlows;
    private final GamificationApplicationService gamification;
    private final EditorDefinitionApplicationService editors;
    private final IntegrationProfileApplicationService integrations;

    @Autowired
    /** Descripción: Inicializa el agregador completo de capacidades para contextos de metamodelo y gramática. Entradas: servicios de metamodelos, gramáticas, validaciones, eventos, gamificación, editores e integraciones. Salida: deja el servicio listo para generar resúmenes de disponibilidad. */
    /** Descripción: Inicializa una instancia de HydraCapabilityReadinessService con sus dependencias y datos de entrada. Entradas: metamodels: dato de entrada necesario para la operación; grammars: dato de entrada necesario para la operación; grammarProjection: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación; events: dato de entrada necesario para la operación; eventFlows: dato de entrada necesario para la operación; gamification: dato de entrada necesario para la operación; editors: dato de entrada necesario para la operación; integrations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraCapabilityReadinessService(CanonicalMetamodelApplicationService metamodels,
            GrammarApplicationService grammars, GrammarClassDiagramProjectionService grammarProjection,
            ValidationModelApplicationService validations, EventModelApplicationService events,
            EventFlowApplicationService eventFlows, GamificationApplicationService gamification,
            EditorDefinitionApplicationService editors, IntegrationProfileApplicationService integrations){
        this.metamodels = metamodels; this.grammars = grammars; this.grammarProjection = grammarProjection;
        this.validations = validations; this.events = events; this.eventFlows = eventFlows;
        this.gamification = gamification; this.editors = editors; this.integrations = integrations;
    }

    /** Descripción: Inicializa una variante focal del servicio para contextos de metamodelo sin dependencias de gramática. Entradas: servicios necesarios para calcular las capas de metamodelo. Salida: deja el servicio listo para pruebas y usos focales de metamodelo. */
    /** Descripción: Inicializa una instancia de HydraCapabilityReadinessService con sus dependencias y datos de entrada. Entradas: metamodels: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación; events: dato de entrada necesario para la operación; eventFlows: dato de entrada necesario para la operación; gamification: dato de entrada necesario para la operación; editors: dato de entrada necesario para la operación; integrations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraCapabilityReadinessService(CanonicalMetamodelApplicationService metamodels,
            ValidationModelApplicationService validations, EventModelApplicationService events,
            EventFlowApplicationService eventFlows, GamificationApplicationService gamification,
            EditorDefinitionApplicationService editors, IntegrationProfileApplicationService integrations){
        this(metamodels, null, null, validations, events, eventFlows, gamification, editors, integrations);
    }

    /** Descripción: Mantiene la entrada compatible que resume un contexto de metamodelo. Entradas: metamodelId: identificador del metamodelo. Salida: devuelve su disponibilidad agregada. */
    /** Descripción: Ejecuta la operación «summarize» correspondiente a la responsabilidad de HydraCapabilityReadinessService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraCapabilityReadiness summarize(String metamodelId){ return summarizeMetamodel(metamodelId); }

    /** Descripción: Calcula el inventario de capacidades para un metamodelo persistido. Entradas: metamodelId: identificador del metamodelo. Salida: devuelve las capas configuradas, métricas y próximos pasos. */
    /** Descripción: Ejecuta la operación «summarizeMetamodel» correspondiente a la responsabilidad de HydraCapabilityReadinessService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraCapabilityReadiness summarizeMetamodel(String metamodelId){
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        return summarize("METAMODEL", metamodel.id(), metamodel.name(), metamodel.id(), metamodel, "Metamodel structure");
    }

    /** Descripción: Calcula el inventario de capacidades para una gramática y su metamodelo inferido o asociado. Entradas: grammarId: identificador de la gramática. Salida: devuelve las capas configuradas, métricas y próximos pasos. */
    /** Descripción: Ejecuta la operación «summarizeGrammar» correspondiente a la responsabilidad de HydraCapabilityReadinessService. Entradas: grammarId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public HydraCapabilityReadiness summarizeGrammar(String grammarId){
        if(grammars == null || grammarProjection == null) throw new IllegalStateException("Grammar readiness is not available in this service configuration");
        GrammarDefinitionModel grammar = grammars.find(grammarId).grammar();
        GrammarClassDiagramProjection projection = grammarProjection.project(grammar, (com.example.editor.grammar.core.GrammarAnalysisResult)null);
        MetaModel inferred = projection.inferredMetamodel();
        String semanticContextId = grammar.metamodelId().isBlank()
                ? "grammar." + grammar.id()
                : grammar.metamodelId();
        return summarize("GRAMMAR", grammar.id(), grammar.name(), semanticContextId, inferred, "Grammar structure");
    }

    /** Descripción: Agrega todas las capas portables de un contexto semántico común. Entradas: tipo, identificadores, nombre, metamodelo y etiqueta estructural del contexto. Salida: devuelve un resumen normalizado con métricas, avisos y recomendaciones. */
    /** Descripción: Ejecuta la operación «summarize» correspondiente a la responsabilidad de HydraCapabilityReadinessService. Entradas: contextType: dato de entrada necesario para la operación; contextId: identificador que delimita el recurso objetivo; contextName: dato de entrada necesario para la operación; artifactContextId: identificador que delimita el recurso objetivo; metamodel: dato de entrada necesario para la operación; structureLabel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraCapabilityReadiness summarize(String contextType, String contextId, String contextName,
            String artifactContextId, MetaModel metamodel, String structureLabel){
        ValidationModelSummary validation = validations.findAll().stream()
                .filter(item -> item.metamodelId().equals(artifactContextId)).findFirst().orElse(null);
        EventModelSummary event = events.list().stream()
                .filter(item -> item.metamodelId().equals(artifactContextId)).findFirst().orElse(null);
        EventFlowSummary flow = eventFlows.list().stream()
                .filter(item -> item.metamodelId().equals(artifactContextId)).findFirst().orElse(null);
        List<String> warnings = new ArrayList<>();
        GamificationCatalogResult gamificationCatalog;
        try {
            gamificationCatalog = gamification.listWithDiagnostics();
            if(gamificationCatalog == null) gamificationCatalog = GamificationCatalogResult.empty("Gamification catalog returned no result");
        } catch(RuntimeException exception) {
            gamificationCatalog = GamificationCatalogResult.empty("Gamification readiness could not be inspected: " + safeMessage(exception));
        }
        warnings.addAll(gamificationCatalog.diagnostics());
        GamificationSummary game = gamificationCatalog.summaries().stream()
                .filter(item -> item.metamodelId().equals(artifactContextId)).findFirst().orElse(null);
        List<EditorDefinitionSummary> editorDefinitions = editors.list(artifactContextId);
        List<IntegrationProfileSummary> profiles = integrations.list(artifactContextId);

        List<HydraCapabilityLayerSummary> layers = new ArrayList<>();
        int classifierCount = metamodel.classes().size() + metamodel.enums().size() + metamodel.dataTypes().size();
        layers.add(layer(contextType.equals("GRAMMAR") ? "GRAMMAR" : "METAMODEL", structureLabel, contextType.equals("METAMODEL") || classifierCount > 0, Math.max(classifierCount, contextType.equals("METAMODEL") ? 1 : 0),
                metrics("classes", metamodel.classes().size(), "relations", metamodel.relations().size()),
                contextType.equals("GRAMMAR") ? "GRAMMARS" : "METAMODEL"));
        layers.add(layer("VALIDATIONS", "Validations and quick fixes", validation != null && validation.ruleCount() > 0,
                validation == null ? 0 : 1, metrics("rules", validation == null ? 0 : validation.ruleCount(),
                        "ruleSets", validation == null ? 0 : validation.ruleSetCount()), "VALIDATIONS"));
        layers.add(layer("EVENTS", "Events and bindings", event != null && (event.eventTypeCount() > 0 || event.bindingCount() > 0),
                event == null ? 0 : 1, metrics("eventTypes", event == null ? 0 : event.eventTypeCount(),
                        "bindings", event == null ? 0 : event.bindingCount()), "EVENTS"));
        layers.add(layer("EVENT_FLOWS", "Event flows", flow != null && flow.flowCount() > 0,
                flow == null ? 0 : 1, metrics("flows", flow == null ? 0 : flow.flowCount(),
                        "nodes", flow == null ? 0 : flow.nodeCount()), "EVENT_FLOWS"));
        layers.add(layer("GAMIFICATION", "Gamification", game != null && (game.ruleCount() > 0 || game.levelCount() > 0 || game.achievementCount() > 0),
                game == null ? 0 : 1, metrics("rules", game == null ? 0 : game.ruleCount(),
                        "levels", game == null ? 0 : game.levelCount(), "achievements", game == null ? 0 : game.achievementCount()), "GAMIFICATION"));
        layers.add(layer("EDITORS", "Model editors and representations", !editorDefinitions.isEmpty(), editorDefinitions.size(),
                metrics("representations", editorDefinitions.stream().mapToInt(EditorDefinitionSummary::representationCount).sum(),
                        "tools", editorDefinitions.stream().mapToInt(EditorDefinitionSummary::toolCount).sum(),
                        "forms", editorDefinitions.stream().mapToInt(EditorDefinitionSummary::formCount).sum()), "EDITORS"));
        layers.add(layer("INTEGRATIONS", "Technology integrations", !profiles.isEmpty(), profiles.size(),
                metrics("profiles", profiles.size(), "features", profiles.stream().mapToInt(IntegrationProfileSummary::featureCount).sum(),
                        "mappings", profiles.stream().mapToInt(IntegrationProfileSummary::mappingCount).sum()), "INTEGRATIONS"));

        int configured = (int) layers.stream().filter(HydraCapabilityLayerSummary::configured).count();
        int score = Math.round((configured * 100.0f) / layers.size());
        List<String> next = layers.stream().filter(item -> !item.configured())
                .map(item -> "Configure " + item.displayName().toLowerCase()).limit(3).toList();
        if(next.isEmpty()){next = List.of("Validate native targets", "Review round-trip synchronization", "Package an independent editor");}
        return new HydraCapabilityReadiness(contextId, contextName, contextType, artifactContextId, score, configured, layers.size(), layers, next, warnings);
    }

    /** Descripción: Construye el resumen de una capa individual. Entradas: identificador, nombre, estado, número de artefactos, métricas y módulo recomendado. Salida: devuelve la capa normalizada. */
    /** Descripción: Ejecuta la operación «layer» correspondiente a la responsabilidad de HydraCapabilityReadinessService. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; configured: configuración que gobierna el comportamiento; count: dato de entrada necesario para la operación; metrics: dato de entrada necesario para la operación; module: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraCapabilityLayerSummary layer(String id, String name, boolean configured, int count,
            Map<String, Integer> metrics, String module){
        return new HydraCapabilityLayerSummary(id, name, configured, count, metrics, module);
    }

    /** Descripción: Obtiene un mensaje seguro para un fallo de inspección sin propagar valores nulos. Entradas: exception: error capturado. Salida: devuelve un texto utilizable en diagnósticos. */
    /** Descripción: Ejecuta la operación «safeMessage» correspondiente a la responsabilidad de HydraCapabilityReadinessService. Entradas: exception: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String safeMessage(Throwable exception) {
        if(exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) return "Unknown error";
        return exception.getMessage();
    }

    /** Descripción: Construye un mapa ordenado de métricas a partir de pares nombre-valor. Entradas: values: secuencia alterna de claves y enteros. Salida: devuelve las métricas normalizadas. */
    /** Descripción: Ejecuta la operación «metrics» correspondiente a la responsabilidad de HydraCapabilityReadinessService. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, Integer> metrics(Object... values){
        Map<String, Integer> result = new LinkedHashMap<>();
        for(int index = 0; index < values.length; index += 2){result.put((String) values[index], (Integer) values[index + 1]);}
        return result;
    }
}
