package com.example.editor.definition.runtime.session;

import java.util.List;
import java.util.Map;

import com.example.editor.definition.FormDefinition;
import com.example.editor.definition.RepresentationKind;
import com.example.editor.definition.runtime.ExecutablePaletteGroup;
import com.example.editor.validation.runtime.ModelValidationReport;

/** Vista ejecutable de una sesión, incluyendo representaciones, Problems y herramientas condicionadas. */
public record EditorSessionView(EditorModelSession session, String etag, String representationId,
        String representationName, RepresentationKind representationKind, boolean editable,
        List<EditorRenderedNode> nodes, List<EditorRenderedEdge> edges,
        List<ExecutablePaletteGroup> paletteGroups, Map<String, EditorRuntimeToolState> toolStates,
        FormDefinition selectedForm, List<EditorRepresentationRuntimeView> representations,
        ModelValidationReport validationReport, boolean undoAvailable, boolean redoAvailable) {
    public EditorSessionView {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        paletteGroups = paletteGroups == null ? List.of() : List.copyOf(paletteGroups);
        toolStates = toolStates == null ? Map.of() : Map.copyOf(toolStates);
        representations = representations == null ? List.of() : List.copyOf(representations);
    }

    /** Compatibilidad con vistas anteriores que no exponían estados condicionales. */
    /** Descripción: Inicializa una instancia de EditorSessionView con sus dependencias y datos de entrada. Entradas: session: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación; representationId: identificador que delimita el recurso objetivo; representationName: dato de entrada necesario para la operación; representationKind: dato de entrada necesario para la operación; editable: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación; edges: dato de entrada necesario para la operación; paletteGroups: dato de entrada necesario para la operación; selectedForm: dato de entrada necesario para la operación; representations: dato de entrada necesario para la operación; validationReport: dato de entrada necesario para la operación; undoAvailable: dato de entrada necesario para la operación; redoAvailable: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionView(EditorModelSession session, String etag, String representationId,
            String representationName, RepresentationKind representationKind, boolean editable,
            List<EditorRenderedNode> nodes, List<EditorRenderedEdge> edges,
            List<ExecutablePaletteGroup> paletteGroups, FormDefinition selectedForm,
            List<EditorRepresentationRuntimeView> representations,
            ModelValidationReport validationReport, boolean undoAvailable, boolean redoAvailable) {
        this(session, etag, representationId, representationName, representationKind, editable, nodes,
                edges, paletteGroups, Map.of(), selectedForm, representations, validationReport,
                undoAvailable, redoAvailable);
    }

    /** Descripción: Inicializa una instancia de EditorSessionView con sus dependencias y datos de entrada. Entradas: session: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación; representationName: dato de entrada necesario para la operación; editable: dato de entrada necesario para la operación; nodes: dato de entrada necesario para la operación; edges: dato de entrada necesario para la operación; paletteGroups: dato de entrada necesario para la operación; selectedForm: dato de entrada necesario para la operación; undoAvailable: dato de entrada necesario para la operación; redoAvailable: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorSessionView(EditorModelSession session, String etag, String representationName,
            boolean editable, List<EditorRenderedNode> nodes, List<EditorRenderedEdge> edges,
            List<ExecutablePaletteGroup> paletteGroups, FormDefinition selectedForm,
            boolean undoAvailable, boolean redoAvailable) {
        this(session, etag, session == null ? "" : session.activeRepresentationId(),
                representationName, RepresentationKind.DIAGRAM, editable, nodes, edges, paletteGroups,
                Map.of(), selectedForm, List.of(), session == null ? null : session.latestValidationReport(),
                undoAvailable, redoAvailable);
    }
}
