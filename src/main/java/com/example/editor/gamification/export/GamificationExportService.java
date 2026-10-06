package com.example.editor.gamification.export;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import com.example.editor.gamification.AchievementDefinition;
import com.example.editor.gamification.ChallengeDefinition;
import com.example.editor.gamification.GamificationApplicationService;
import com.example.editor.gamification.GamificationLedgerEntry;
import com.example.editor.gamification.GamificationLoadResult;
import com.example.editor.gamification.GamificationRule;
import com.example.editor.gamification.LevelDefinition;
import com.example.editor.gamification.RewardDefinition;
import com.example.editor.gamification.runtime.GamificationRuntimeService;
import com.example.editor.gamification.runtime.GamificationRuntimeSnapshot;
import tools.jackson.databind.ObjectMapper;

/** Proyecta reglas, objetivos, progresión y ledger sin acoplar el núcleo a Draw.io. */
@Service
public class GamificationExportService {
    private final GamificationApplicationService applicationService;
    private final GamificationRuntimeService runtimeService;
    private final ObjectMapper mapper;
    /** Descripción: Inicializa una instancia de GamificationExportService con sus dependencias y datos de entrada. Entradas: applicationService: servicio colaborador utilizado por la operación; runtimeService: servicio colaborador utilizado por la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GamificationExportService(GamificationApplicationService applicationService,
            GamificationRuntimeService runtimeService, ObjectMapper mapper) {
        this.applicationService = applicationService;
        this.runtimeService = runtimeService;
        this.mapper = mapper;
    }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de Edge. Entradas: metamodelId: identificador que delimita el recurso objetivo; format: dato de entrada necesario para la operación; view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExportedGamificationArtifact export(String metamodelId, String format, String view) {
        GamificationLoadResult model = applicationService.find(metamodelId);
        String normalizedFormat = normalize(format, "json");
        String normalizedView = normalize(view, "rules");
        GamificationRuntimeSnapshot runtime = needsRuntime(normalizedView) ? runtimeService.state(metamodelId) : null;
        return switch(normalizedFormat) {
            case "drawio" -> new ExportedGamificationArtifact("application/vnd.jgraph.mxfile", file(metamodelId, normalizedView, "drawio"), drawio(model, runtime, normalizedView));
            case "markdown", "md" -> new ExportedGamificationArtifact("text/markdown;charset=UTF-8", file(metamodelId, normalizedView, "md"), markdown(model, runtime, normalizedView));
            default -> new ExportedGamificationArtifact("application/json", file(metamodelId, normalizedView, "json"), json(model, runtime, normalizedView));
        };
    }
    /** Descripción: Ejecuta la operación «json» correspondiente a la responsabilidad de Edge. Entradas: model: dato de entrada necesario para la operación; runtime: dato de entrada necesario para la operación; view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String json(GamificationLoadResult model, GamificationRuntimeSnapshot runtime, String view) {
        try {
            Object value = switch(view) {
                case "definitions", "progression" -> model.definitions();
                case "ledger" -> runtime == null ? List.of() : runtime.ledger();
                case "state" -> runtime == null ? java.util.Map.of() : runtime.state();
                case "diagnostics" -> model.checkReport();
                default -> model.bindings();
            };
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch(Exception exception) { throw new IllegalStateException("Could not export gamification", exception); }
    }
    /** Descripción: Ejecuta la operación «markdown» correspondiente a la responsabilidad de Edge. Entradas: model: dato de entrada necesario para la operación; runtime: dato de entrada necesario para la operación; view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String markdown(GamificationLoadResult model, GamificationRuntimeSnapshot runtime, String view) {
        StringBuilder out = new StringBuilder("# HYDRA gamification\n\n");
        if(view.equals("ledger") && runtime != null) {
            for(GamificationLedgerEntry entry : runtime.ledger().entries()) out.append("- `").append(entry.status()).append("` ")
                    .append(entry.actorId()).append(": ").append(entry.pointsDelta()).append(" ").append(entry.pointsTypeId())
                    .append(" from `").append(entry.sourceEventId()).append("`\n");
        } else if(view.equals("progression") || view.equals("definitions")) {
            for(LevelDefinition level : model.definitions().levels()) out.append("- Level **").append(level.name()).append("** at ").append(level.minimumPoints()).append(" `").append(level.pointTypeId()).append("`\n");
            for(AchievementDefinition achievement : model.definitions().achievements()) out.append("- Achievement **").append(achievement.name()).append("**\n");
            for(ChallengeDefinition challenge : model.definitions().challenges()) out.append("- Challenge **").append(challenge.name()).append("** target ").append(challenge.targetCount()).append("\n");
        } else {
            for(GamificationRule rule : model.bindings().rules()) out.append("- `").append(rule.eventTypeId()).append("` → **").append(rule.name()).append("**: ").append(rule.points()).append(" `").append(rule.pointsTypeId()).append("` [").append(rule.repeatPolicy()).append("]\n");
        }
        return out.toString();
    }
    /** Descripción: Ejecuta la operación «drawio» correspondiente a la responsabilidad de Edge. Entradas: model: dato de entrada necesario para la operación; runtime: dato de entrada necesario para la operación; view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String drawio(GamificationLoadResult model, GamificationRuntimeSnapshot runtime, String view) {
        List<Cell> cells = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        if(view.equals("ledger") && runtime != null) {
            int index = 0;
            for(GamificationLedgerEntry entry : runtime.ledger().entries()) cells.add(new Cell(entry.id(), entry.status() + "\n" + entry.actorId() + "\n" + entry.pointsDelta() + " " + entry.pointsTypeId(), 40 + (index % 4) * 240, 40 + (index / 4) * 130, "rounded=1;whiteSpace=wrap;html=1;"));
        } else if(view.equals("progression") || view.equals("definitions")) {
            int index = 0;
            for(LevelDefinition level : model.definitions().levels()) { cells.add(new Cell(level.id(), "LEVEL\n" + level.name() + "\n" + level.minimumPoints() + " " + level.pointTypeId(), 40 + index++ * 220, 40, "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;")); }
            for(AchievementDefinition achievement : model.definitions().achievements()) cells.add(new Cell(achievement.id(), "ACHIEVEMENT\n" + achievement.name(), 40 + (index++ % 5) * 220, 190, "rounded=1;whiteSpace=wrap;html=1;fillColor=#e1d5e7;"));
            for(ChallengeDefinition challenge : model.definitions().challenges()) cells.add(new Cell(challenge.id(), "CHALLENGE\n" + challenge.name(), 40 + (index++ % 5) * 220, 330, "rounded=1;whiteSpace=wrap;html=1;fillColor=#fff2cc;"));
            for(RewardDefinition reward : model.definitions().rewards()) cells.add(new Cell(reward.id(), "REWARD\n" + reward.name(), 40 + (index++ % 5) * 220, 470, "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;"));
        } else {
            int index = 0;
            for(GamificationRule rule : model.bindings().rules()) {
                String source = "event-" + index;
                cells.add(new Cell(source, rule.eventTypeId(), 40, 40 + index * 120, "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;"));
                cells.add(new Cell(rule.id(), rule.name() + "\n" + rule.points() + " " + rule.pointsTypeId(), 330, 40 + index * 120, "rounded=1;whiteSpace=wrap;html=1;fillColor=#e1d5e7;"));
                edges.add(new Edge("edge-" + rule.id(), rule.repeatPolicy().name(), source, rule.id()));
                index++;
            }
        }
        StringBuilder xml = new StringBuilder("<mxfile host=\"HYDRA\"><diagram name=\"").append(xml(view)).append("\"><mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>");
        for(Cell cell : cells) xml.append("<mxCell id=\"").append(xml(cell.id)).append("\" value=\"").append(xml(cell.label)).append("\" style=\"").append(cell.style).append("\" vertex=\"1\" parent=\"1\"><mxGeometry x=\"").append(cell.x).append("\" y=\"").append(cell.y).append("\" width=\"190\" height=\"80\" as=\"geometry\"/></mxCell>");
        for(Edge edge : edges) xml.append("<mxCell id=\"").append(xml(edge.id)).append("\" value=\"").append(xml(edge.label)).append("\" edge=\"1\" parent=\"1\" source=\"").append(xml(edge.source)).append("\" target=\"").append(xml(edge.target)).append("\"><mxGeometry relative=\"1\" as=\"geometry\"/></mxCell>");
        return xml.append("</root></mxGraphModel></diagram></mxfile>").toString();
    }
    /** Descripción: Ejecuta la operación «needsRuntime» correspondiente a la responsabilidad de Edge. Entradas: view: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean needsRuntime(String view) { return view.equals("ledger") || view.equals("state"); }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de Edge. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String normalize(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(); }
    /** Descripción: Ejecuta la operación «file» correspondiente a la responsabilidad de Edge. Entradas: metamodelId: identificador que delimita el recurso objetivo; view: dato de entrada necesario para la operación; extension: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String file(String metamodelId, String view, String extension) { return metamodelId + ".gamification-" + view + "." + extension; }
    /** Descripción: Ejecuta la operación «xml» correspondiente a la responsabilidad de Edge. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String xml(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private record Cell(String id, String label, int x, int y, String style) { }
    private record Edge(String id, String label, String source, String target) { }
}
