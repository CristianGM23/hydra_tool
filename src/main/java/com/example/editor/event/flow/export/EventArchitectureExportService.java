package com.example.editor.event.flow.export;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import com.example.editor.event.EventBinding;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelLoadResult;
import com.example.editor.event.EventTypeDefinition;
import com.example.editor.event.flow.EventFlowApplicationService;
import com.example.editor.event.flow.EventFlowLoadResult;
import com.example.editor.event.flow.FlowDefinition;
import com.example.editor.event.flow.FlowDiagnostic;
import com.example.editor.event.flow.FlowNode;
import com.example.editor.event.flow.FlowTransition;
import tools.jackson.databind.ObjectMapper;

/** Proyecciones documentales del catálogo, bindings, flujos y diagnósticos. */
@Service
public class EventArchitectureExportService {
    private final EventModelApplicationService eventService;
    private final EventFlowApplicationService flowService;
    private final ObjectMapper mapper;
    /** Descripción: Inicializa una instancia de EventArchitectureExportService con sus dependencias y datos de entrada. Entradas: eventService: servicio colaborador utilizado por la operación; flowService: servicio colaborador utilizado por la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public EventArchitectureExportService(EventModelApplicationService eventService, EventFlowApplicationService flowService, ObjectMapper mapper) {
        this.eventService = eventService;
        this.flowService = flowService;
        this.mapper = mapper;
    }
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de Edge. Entradas: metamodelId: identificador que delimita el recurso objetivo; format: dato de entrada necesario para la operación; view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExportedEventArchitecture export(String metamodelId, String format, String view) {
        EventModelLoadResult events = eventService.find(metamodelId);
        EventFlowLoadResult flows = flowService.find(metamodelId);
        String normalizedFormat = format == null ? "json" : format.trim().toLowerCase();
        String normalizedView = view == null || view.isBlank() ? "flow" : view.trim().toLowerCase();
        return switch(normalizedFormat) {
            case "drawio" -> new ExportedEventArchitecture("application/vnd.jgraph.mxfile", file(metamodelId, normalizedView, "drawio"), drawio(events, flows, normalizedView));
            case "plantuml", "puml" -> new ExportedEventArchitecture("text/plain;charset=UTF-8", file(metamodelId, normalizedView, "puml"), plantUml(events, flows, normalizedView));
            case "markdown", "md" -> new ExportedEventArchitecture("text/markdown;charset=UTF-8", file(metamodelId, normalizedView, "md"), markdown(events, flows, normalizedView));
            default -> new ExportedEventArchitecture("application/json", file(metamodelId, normalizedView, "json"), json(events, flows, normalizedView));
        };
    }
    /** Descripción: Ejecuta la operación «json» correspondiente a la responsabilidad de Edge. Entradas: events: dato de entrada necesario para la operación; flows: dato de entrada necesario para la operación; view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String json(EventModelLoadResult events, EventFlowLoadResult flows, String view) {
        try {
            Object value = switch(view) {
                case "catalog" -> events.definitions();
                case "bindings" -> events.bindings();
                case "diagnostics" -> flows.checkReport();
                default -> flows.eventFlowModel();
            };
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch(Exception exception) { throw new IllegalStateException("Could not export event architecture", exception); }
    }
    /** Descripción: Ejecuta la operación «markdown» correspondiente a la responsabilidad de Edge. Entradas: events: dato de entrada necesario para la operación; flows: dato de entrada necesario para la operación; view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String markdown(EventModelLoadResult events, EventFlowLoadResult flows, String view) {
        StringBuilder out = new StringBuilder("# HYDRA event architecture\n\n");
        if(view.equals("catalog")) {
            for(EventTypeDefinition type : events.definitions().eventTypes()) out.append("## ").append(type.name()).append("\n\n- ID: `").append(type.id()).append("`\n- Category: ").append(type.category()).append("\n- Fields: ").append(type.fields().size()).append("\n\n");
        } else if(view.equals("bindings")) {
            for(EventBinding binding : events.bindings().bindings()) out.append("- `").append(binding.sourceEventTypeId()).append("` → `").append(binding.targetEventTypeId()).append("` (**").append(binding.name()).append("**)\n");
        } else if(view.equals("diagnostics")) {
            for(FlowDiagnostic diagnostic : flows.checkReport().diagnostics()) out.append("- **").append(diagnostic.severity()).append("** `").append(diagnostic.code()).append("`: ").append(diagnostic.message()).append("\n");
        } else {
            for(FlowDefinition flow : flows.eventFlowModel().flows()) {
                out.append("## ").append(flow.name()).append("\n\n");
                for(FlowNode node : flow.nodes()) out.append("- `").append(node.kind()).append("` **").append(node.name()).append("** (`").append(node.id()).append("`)\n");
                out.append("\n");
            }
        }
        return out.toString();
    }
    /** Descripción: Ejecuta la operación «plantUml» correspondiente a la responsabilidad de Edge. Entradas: events: dato de entrada necesario para la operación; flows: dato de entrada necesario para la operación; view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String plantUml(EventModelLoadResult events, EventFlowLoadResult flows, String view) {
        StringBuilder out = new StringBuilder("@startuml\n");
        if(view.equals("catalog")) {
            for(EventTypeDefinition type : events.definitions().eventTypes()) out.append("class \"").append(escape(type.name())).append("\" as ").append(alias(type.id())).append(" <<event>>\n");
        } else if(view.equals("bindings")) {
            for(EventBinding binding : events.bindings().bindings()) out.append(alias(binding.sourceEventTypeId())).append(" --> ").append(alias(binding.targetEventTypeId())).append(" : ").append(escape(binding.name())).append("\n");
        } else {
            for(FlowDefinition flow : flows.eventFlowModel().flows()) {
                out.append("partition \"").append(escape(flow.name())).append("\" {\n");
                for(FlowNode node : flow.nodes()) out.append("  state \"").append(escape(node.name())).append("\" as ").append(alias(node.id())).append("\n");
                for(FlowTransition transition : flow.transitions()) out.append("  ").append(alias(transition.sourceNodeId())).append(" --> ").append(alias(transition.targetNodeId())).append(" : ").append(transition.kind()).append("\n");
                out.append("}\n");
            }
        }
        return out.append("@enduml\n").toString();
    }
    /** Descripción: Ejecuta la operación «drawio» correspondiente a la responsabilidad de Edge. Entradas: events: dato de entrada necesario para la operación; flows: dato de entrada necesario para la operación; view: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String drawio(EventModelLoadResult events, EventFlowLoadResult flows, String view) {
        List<Cell> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        if(view.equals("catalog")) {
            int index = 0;
            for(EventTypeDefinition type : events.definitions().eventTypes()) nodes.add(new Cell(type.id(), type.name() + "\\n" + type.id(), 40 + (index % 4) * 240, 40 + (index / 4) * 130, "rounded=1;whiteSpace=wrap;html=1;"));
        } else if(view.equals("bindings")) {
            int index = 0;
            java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
            events.bindings().bindings().forEach(binding -> { ids.add(binding.sourceEventTypeId()); ids.add(binding.targetEventTypeId()); });
            for(String id : ids) { nodes.add(new Cell(id, id, 40 + (index % 4) * 260, 40 + (index / 4) * 130, "rounded=1;whiteSpace=wrap;html=1;")); index++; }
            for(EventBinding binding : events.bindings().bindings()) edges.add(new Edge(binding.id(), binding.name(), binding.sourceEventTypeId(), binding.targetEventTypeId()));
        } else if(view.equals("diagnostics")) {
            int index = 0;
            for(FlowDiagnostic diagnostic : flows.checkReport().diagnostics()) nodes.add(new Cell("diagnostic-" + index++, diagnostic.severity() + "\\n" + diagnostic.code() + "\\n" + diagnostic.message(), 40, 40 + index * 110, "rounded=1;whiteSpace=wrap;html=1;"));
        } else {
            int offset = 0;
            for(FlowDefinition flow : flows.eventFlowModel().flows()) {
                int index = 0;
                for(FlowNode node : flow.nodes()) nodes.add(new Cell(node.id(), node.kind() + "\\n" + node.name(), 40 + (index % 5) * 220, 40 + offset + (index / 5) * 130, "rounded=1;whiteSpace=wrap;html=1;"));
                for(FlowTransition transition : flow.transitions()) edges.add(new Edge(transition.id(), transition.kind().name(), transition.sourceNodeId(), transition.targetNodeId()));
                offset += Math.max(180, ((flow.nodes().size() + 4) / 5) * 130 + 60);
            }
        }
        StringBuilder xml = new StringBuilder("<mxfile host=\"HYDRA\"><diagram name=\"").append(xml(view)).append("\"><mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>");
        for(Cell node : nodes) xml.append("<mxCell id=\"").append(xml(node.id)).append("\" value=\"").append(xml(node.label)).append("\" style=\"").append(node.style).append("\" vertex=\"1\" parent=\"1\"><mxGeometry x=\"").append(node.x).append("\" y=\"").append(node.y).append("\" width=\"190\" height=\"80\" as=\"geometry\"/></mxCell>");
        for(Edge edge : edges) xml.append("<mxCell id=\"").append(xml(edge.id)).append("\" value=\"").append(xml(edge.label)).append("\" edge=\"1\" parent=\"1\" source=\"").append(xml(edge.source)).append("\" target=\"").append(xml(edge.target)).append("\"><mxGeometry relative=\"1\" as=\"geometry\"/></mxCell>");
        return xml.append("</root></mxGraphModel></diagram></mxfile>").toString();
    }
    /** Descripción: Ejecuta la operación «file» correspondiente a la responsabilidad de Edge. Entradas: metamodelId: identificador que delimita el recurso objetivo; view: dato de entrada necesario para la operación; extension: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String file(String metamodelId, String view, String extension) { return metamodelId + ".event-" + view + "." + extension; }
    /** Descripción: Ejecuta la operación «alias» correspondiente a la responsabilidad de Edge. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String alias(String value) { return "n_" + value.replaceAll("[^A-Za-z0-9_]", "_"); }
    /** Descripción: Ejecuta la operación «escape» correspondiente a la responsabilidad de Edge. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escape(String value) { return value == null ? "" : value.replace("\"", "'").replace("\n", " "); }
    /** Descripción: Ejecuta la operación «xml» correspondiente a la responsabilidad de Edge. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String xml(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private record Cell(String id, String label, int x, int y, String style) { }
    private record Edge(String id, String label, String source, String target) { }
}
