package com.example.editor.metamodel.export.project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.example.editor.definition.CodeGenerationDefinition;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.persistence.EditorDefinitionRepository;
import com.example.editor.event.flow.persistence.EventFlowModelRepository;
import com.example.editor.event.persistence.EventBindingModelRepository;
import com.example.editor.event.persistence.EventDefinitionModelRepository;
import com.example.editor.gamification.persistence.GamificationBindingRepository;
import com.example.editor.gamification.persistence.GamificationDefinitionRepository;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.projection.CanonicalArtifactContent;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.behavior.persistence.OperationBehaviorModelRepository;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.validation.action.persistence.ActionModelRepository;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.validation.persistence.ValidationModelRepository;
import tools.jackson.databind.json.JsonMapper;

/** Reúne el metamodelo y las capas exactas seleccionadas para una exportación completa. */
@Component
public class MetamodelProjectArtifactAssembler {
    private final CanonicalMetamodelApplicationService metamodels;
    private final ExpressionModelRepository expressions;
    private final ValidationModelRepository validations;
    private final ActionModelRepository actions;
    private final OperationBehaviorModelRepository operations;
    private final EventDefinitionModelRepository events;
    private final EventBindingModelRepository eventBindings;
    private final EventFlowModelRepository eventFlows;
    private final GamificationDefinitionRepository gamification;
    private final GamificationBindingRepository gamificationBindings;
    private final EditorDefinitionRepository editors;
    private final DefaultDerivedEditorDefinitionFactory derivedEditor;
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de MetamodelProjectArtifactAssembler con sus dependencias y datos de entrada. Entradas: metamodels: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; operations: dato de entrada necesario para la operación; events: dato de entrada necesario para la operación; eventBindings: dato de entrada necesario para la operación; eventFlows: dato de entrada necesario para la operación; gamification: dato de entrada necesario para la operación; gamificationBindings: dato de entrada necesario para la operación; editors: dato de entrada necesario para la operación; derivedEditor: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelProjectArtifactAssembler(CanonicalMetamodelApplicationService metamodels, ExpressionModelRepository expressions,
            ValidationModelRepository validations, ActionModelRepository actions, OperationBehaviorModelRepository operations,
            EventDefinitionModelRepository events, EventBindingModelRepository eventBindings, EventFlowModelRepository eventFlows,
            GamificationDefinitionRepository gamification, GamificationBindingRepository gamificationBindings,
            EditorDefinitionRepository editors, DefaultDerivedEditorDefinitionFactory derivedEditor, JsonMapper mapper){
        this.metamodels = metamodels; this.expressions = expressions; this.validations = validations; this.actions = actions;
        this.operations = operations; this.events = events; this.eventBindings = eventBindings; this.eventFlows = eventFlows;
        this.gamification = gamification; this.gamificationBindings = gamificationBindings; this.editors = editors;
        this.derivedEditor = derivedEditor; this.mapper = mapper;
    }

    /** Atajo actual: incluye todas las capas compatibles y selecciona el editor cuando no es ambiguo. */
    /** Descripción: Ejecuta la operación «assemble» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: metamodelId: identificador que delimita el recurso objetivo; includeLayers: dato de entrada necesario para la operación; requireEditor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<CanonicalArtifactContent> assemble(String metamodelId, boolean includeLayers, boolean requireEditor){
        return assemble(metamodelId, new MetamodelProjectExportOptions(includeLayers, MetamodelProjectArtifactSelectionMode.ALL_COMPATIBLE, List.of(), "", java.util.Map.of()), requireEditor);
    }

    /** Descripción: Ejecuta la operación «assemble» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: metamodelId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento; requireEditor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<CanonicalArtifactContent> assemble(String metamodelId, MetamodelProjectExportOptions options, boolean requireEditor){
        MetamodelProjectExportOptions effective = options == null ? MetamodelProjectExportOptions.defaults() : options;
        MetaModel metamodel = metamodels.findById(metamodelId).metamodel();
        List<CanonicalArtifactContent> result = new ArrayList<>();
        add(result, IntegrationArtifactKind.METAMODEL, metamodel.id(), metamodel);

        Set<ArtifactKey> requested = effective.includeExistingLayers() ? selectedKeys(metamodelId, effective) : Set.of();
        if(effective.includeExistingLayers()){
            addExpressionLayers(result, metamodelId, effective.selectionMode(), requested);
            addValidationLayers(result, metamodelId, effective.selectionMode(), requested);
            addActionLayers(result, metamodelId, effective.selectionMode(), requested);
            addOperationLayers(result, metamodelId, effective.selectionMode(), requested);
            addEventLayers(result, metamodelId, effective.selectionMode(), requested);
            addGamificationLayers(result, metamodelId, effective.selectionMode(), requested);
        }
        addEditors(result, metamodel, effective, requireEditor);
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «catalog» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelProjectArtifactCatalog catalog(String metamodelId){
        metamodels.findById(metamodelId);
        List<MetamodelProjectArtifactDescriptor> result = new ArrayList<>();
        expressions.findAll().stream().filter(item -> metamodelId.equals(item.metamodelId())).sorted(Comparator.comparing(item -> item.id()))
                .forEach(item -> result.add(descriptor(IntegrationArtifactKind.EXPRESSIONS, item.id(), "Expressions · " + item.id(), item.etag(), false)));
        validations.findAll().stream().filter(item -> metamodelId.equals(item.metamodelId())).sorted(Comparator.comparing(item -> item.id()))
                .forEach(item -> result.add(descriptor(IntegrationArtifactKind.VALIDATIONS, item.id(), "Validations · " + item.id(), item.etag(), false)));
        actions.findAll().stream().filter(item -> metamodelId.equals(item.metamodelId())).sorted(Comparator.comparing(item -> item.id()))
                .forEach(item -> result.add(descriptor(IntegrationArtifactKind.ACTIONS, item.id(), "Quick fixes/actions · " + item.id(), item.etag(), false)));
        operations.findAll().stream().filter(item -> metamodelId.equals(item.metamodelId())).sorted(Comparator.comparing(item -> item.metamodelId()))
                .forEach(item -> result.add(descriptor(IntegrationArtifactKind.OPERATION_BEHAVIORS, item.metamodelId(), "Operation behaviors", item.etag(), false)));
        events.findByMetamodelId(metamodelId).ifPresent(value -> result.add(descriptor(IntegrationArtifactKind.EVENT_DEFINITIONS, value.document().id(), "Event definitions", value.etag(), false)));
        eventBindings.findByMetamodelId(metamodelId).ifPresent(value -> result.add(descriptor(IntegrationArtifactKind.EVENT_BINDINGS, value.document().id(), "Event bindings", value.etag(), false)));
        eventFlows.findByMetamodelId(metamodelId).ifPresent(value -> result.add(descriptor(IntegrationArtifactKind.EVENT_FLOWS, value.document().id(), "EventFlow", value.etag(), false)));
        gamification.find(metamodelId).ifPresent(value -> result.add(descriptor(IntegrationArtifactKind.GAMIFICATION_DEFINITIONS, value.document().id(), "Gamification definitions", value.etag(), false)));
        gamificationBindings.find(metamodelId).ifPresent(value -> result.add(descriptor(IntegrationArtifactKind.GAMIFICATION_BINDINGS, value.document().id(), "Gamification bindings", value.etag(), false)));
        storedEditors(metamodelId).forEach(value -> {
            result.add(descriptor(IntegrationArtifactKind.EDITOR_DEFINITION, value.document().id(), "Editor · " + value.document().name(), value.etag(), true));
            if(effectiveCodeGeneration(value.document()).enabled()) result.add(descriptor(IntegrationArtifactKind.CODE_GENERATION, value.document().id(), "Code generation · " + value.document().name(), value.etag(), false));
        });
        result.sort(Comparator.comparing((MetamodelProjectArtifactDescriptor item) -> item.kind().name()).thenComparing(MetamodelProjectArtifactDescriptor::documentId));
        return new MetamodelProjectArtifactCatalog(metamodelId, result);
    }

    /** Descripción: Ejecuta la operación «selectedKeys» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: metamodelId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private Set<ArtifactKey> selectedKeys(String metamodelId, MetamodelProjectExportOptions options){
        if(options.selectionMode() != MetamodelProjectArtifactSelectionMode.EXPLICIT){return Set.of();}
        Set<ArtifactKey> available = catalog(metamodelId).artifacts().stream().map(item -> new ArtifactKey(item.kind(), item.documentId())).collect(java.util.stream.Collectors.toSet());
        Set<ArtifactKey> selected = new HashSet<>();
        for(MetamodelProjectArtifactRef ref : options.selectedArtifacts()){
            ArtifactKey key = new ArtifactKey(ref.kind(), ref.documentId());
            if(ref.kind() == IntegrationArtifactKind.METAMODEL){throw invalidSelection("The metamodel is always included and must not be selected as a layer.");}
            if(!available.contains(key)){throw invalidSelection("Selected project artifact does not belong to metamodel " + metamodelId + ": " + ref.kind() + "/" + ref.documentId());}
            selected.add(key);
        }
        return Set.copyOf(selected);
    }

    /** Descripción: Ejecuta la operación «addExpressionLayers» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: result: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; mode: dato de entrada necesario para la operación; requested: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addExpressionLayers(List<CanonicalArtifactContent> result, String metamodelId, MetamodelProjectArtifactSelectionMode mode, Set<ArtifactKey> requested){
        expressions.findAll().stream().filter(item -> metamodelId.equals(item.metamodelId())).sorted(Comparator.comparing(item -> item.id()))
                .filter(item -> wanted(mode, requested, IntegrationArtifactKind.EXPRESSIONS, item.id()))
                .map(item -> expressions.findById(item.id()).orElseThrow()).forEach(value -> add(result, IntegrationArtifactKind.EXPRESSIONS, value.expressionModel().id(), value.expressionModel()));
    }
    /** Descripción: Ejecuta la operación «addValidationLayers» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: result: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; mode: dato de entrada necesario para la operación; requested: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addValidationLayers(List<CanonicalArtifactContent> result, String metamodelId, MetamodelProjectArtifactSelectionMode mode, Set<ArtifactKey> requested){
        validations.findAll().stream().filter(item -> metamodelId.equals(item.metamodelId())).sorted(Comparator.comparing(item -> item.id()))
                .filter(item -> wanted(mode, requested, IntegrationArtifactKind.VALIDATIONS, item.id()))
                .map(item -> validations.findById(item.id()).orElseThrow()).forEach(value -> add(result, IntegrationArtifactKind.VALIDATIONS, value.document().id(), value.document()));
    }
    /** Descripción: Ejecuta la operación «addActionLayers» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: result: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; mode: dato de entrada necesario para la operación; requested: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addActionLayers(List<CanonicalArtifactContent> result, String metamodelId, MetamodelProjectArtifactSelectionMode mode, Set<ArtifactKey> requested){
        actions.findAll().stream().filter(item -> metamodelId.equals(item.metamodelId())).sorted(Comparator.comparing(item -> item.id()))
                .filter(item -> wanted(mode, requested, IntegrationArtifactKind.ACTIONS, item.id()))
                .map(item -> actions.findById(item.id()).orElseThrow()).forEach(value -> add(result, IntegrationArtifactKind.ACTIONS, value.document().id(), value.document()));
    }
    /** Descripción: Ejecuta la operación «addOperationLayers» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: result: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; mode: dato de entrada necesario para la operación; requested: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addOperationLayers(List<CanonicalArtifactContent> result, String metamodelId, MetamodelProjectArtifactSelectionMode mode, Set<ArtifactKey> requested){
        operations.findAll().stream().filter(item -> metamodelId.equals(item.metamodelId())).sorted(Comparator.comparing(item -> item.metamodelId()))
                .filter(item -> wanted(mode, requested, IntegrationArtifactKind.OPERATION_BEHAVIORS, item.metamodelId()))
                .map(item -> operations.findById(item.metamodelId()).orElseThrow()).forEach(value -> add(result, IntegrationArtifactKind.OPERATION_BEHAVIORS, value.document().metamodelId(), value.document()));
    }
    /** Descripción: Ejecuta la operación «addEventLayers» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: result: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; mode: dato de entrada necesario para la operación; requested: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addEventLayers(List<CanonicalArtifactContent> result, String metamodelId, MetamodelProjectArtifactSelectionMode mode, Set<ArtifactKey> requested){
        events.findByMetamodelId(metamodelId).filter(value -> wanted(mode, requested, IntegrationArtifactKind.EVENT_DEFINITIONS, value.document().id()))
                .ifPresent(value -> add(result, IntegrationArtifactKind.EVENT_DEFINITIONS, value.document().id(), value.document()));
        eventBindings.findByMetamodelId(metamodelId).filter(value -> wanted(mode, requested, IntegrationArtifactKind.EVENT_BINDINGS, value.document().id()))
                .ifPresent(value -> add(result, IntegrationArtifactKind.EVENT_BINDINGS, value.document().id(), value.document()));
        eventFlows.findByMetamodelId(metamodelId).filter(value -> wanted(mode, requested, IntegrationArtifactKind.EVENT_FLOWS, value.document().id()))
                .ifPresent(value -> add(result, IntegrationArtifactKind.EVENT_FLOWS, value.document().id(), value.document()));
    }
    /** Descripción: Ejecuta la operación «addGamificationLayers» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: result: dato de entrada necesario para la operación; metamodelId: identificador que delimita el recurso objetivo; mode: dato de entrada necesario para la operación; requested: solicitud con los datos necesarios para ejecutar la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addGamificationLayers(List<CanonicalArtifactContent> result, String metamodelId, MetamodelProjectArtifactSelectionMode mode, Set<ArtifactKey> requested){
        gamification.find(metamodelId).filter(value -> wanted(mode, requested, IntegrationArtifactKind.GAMIFICATION_DEFINITIONS, value.document().id()))
                .ifPresent(value -> add(result, IntegrationArtifactKind.GAMIFICATION_DEFINITIONS, value.document().id(), value.document()));
        gamificationBindings.find(metamodelId).filter(value -> wanted(mode, requested, IntegrationArtifactKind.GAMIFICATION_BINDINGS, value.document().id()))
                .ifPresent(value -> add(result, IntegrationArtifactKind.GAMIFICATION_BINDINGS, value.document().id(), value.document()));
    }

    /** Descripción: Ejecuta la operación «addEditors» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: result: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento; requireEditor: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addEditors(List<CanonicalArtifactContent> result, MetaModel metamodel, MetamodelProjectExportOptions options,
            boolean requireEditor){
        List<com.example.editor.validation.persistence.StoredCanonicalDocument<EditorDefinitionModel>> stored = storedEditors(metamodel.id());
        String explicitEditorId = options.editorId();
        if(!explicitEditorId.isBlank()){
            var selected = stored.stream().filter(value -> value.document().id().equals(explicitEditorId)).findFirst()
                    .orElseThrow(() -> invalidSelection("Editor definition does not belong to metamodel " + metamodel.id() + ": " + explicitEditorId));
            addEditorArtifacts(result, selected.document());
            return;
        }
        if(requireEditor){
            if(stored.isEmpty()){
                EditorDefinitionModel generated = derivedEditor.create(metamodel); addEditorArtifacts(result, generated); return;
            }
            if(stored.size() > 1){throw invalidSelection("Multiple editor definitions exist for metamodel " + metamodel.id() + ". Select editorId explicitly before generating this target.");}
            EditorDefinitionModel selected = stored.get(0).document(); addEditorArtifacts(result, selected); return;
        }
        if(!options.includeExistingLayers() || !requireEditor){return;}
    }

    /** Descripción: Añade la definición del editor y, cuando está habilitada, su capa canónica de generación de código. Entradas: resultado y editor. Salida: artefactos exportables coherentes. */
    /** Descripción: Ejecuta la operación «addEditorArtifacts» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: result: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addEditorArtifacts(List<CanonicalArtifactContent> result, EditorDefinitionModel editor){
        add(result, IntegrationArtifactKind.EDITOR_DEFINITION, editor.id(), editor);
        CodeGenerationDefinition codeGeneration = effectiveCodeGeneration(editor);
        if(codeGeneration.enabled()) add(result, IntegrationArtifactKind.CODE_GENERATION, editor.id(), codeGeneration);
    }

    /** Descripción: Ejecuta la operación «storedEditors» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private List<com.example.editor.validation.persistence.StoredCanonicalDocument<EditorDefinitionModel>> storedEditors(String metamodelId){
        return editors.listIds().stream().sorted().map(editors::findById).flatMap(java.util.Optional::stream)
                .filter(value -> metamodelId.equals(value.document().metamodelId())).toList();
    }
    /** Descripción: Ejecuta la operación «wanted» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: mode: dato de entrada necesario para la operación; requested: solicitud con los datos necesarios para ejecutar la operación; kind: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean wanted(MetamodelProjectArtifactSelectionMode mode, Set<ArtifactKey> requested, IntegrationArtifactKind kind, String id){
        return mode == MetamodelProjectArtifactSelectionMode.ALL_COMPATIBLE || requested.contains(new ArtifactKey(kind, id));
    }
    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: kind: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; label: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelProjectArtifactDescriptor descriptor(IntegrationArtifactKind kind, String id, String label, String etag, boolean editor){return new MetamodelProjectArtifactDescriptor(kind, id, label, etag, editor);}
    /** Descripción: Ejecuta la operación «invalidSelection» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelDesignException invalidSelection(String message){return new MetamodelDesignException(Reason.INVALID_REQUEST, message);}
    /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: result: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void add(List<CanonicalArtifactContent> result, IntegrationArtifactKind kind, String id, Object document){result.add(new CanonicalArtifactContent(kind, id, "application/json", mapper.writeValueAsBytes(document)));}
    private record ArtifactKey(IntegrationArtifactKind kind, String id) { }
    /** Descripción: Normaliza la capa de generación para documentos históricos y dobles de prueba que no la exponen todavía. Entradas: editor. Salida: configuración efectiva nunca nula. */
    /** Descripción: Ejecuta la operación «effectiveCodeGeneration» correspondiente a la responsabilidad de MetamodelProjectArtifactAssembler. Entradas: editor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static CodeGenerationDefinition effectiveCodeGeneration(EditorDefinitionModel editor) {
        if(editor == null || editor.codeGeneration() == null) return CodeGenerationDefinition.disabled();
        return editor.codeGeneration();
    }

}
