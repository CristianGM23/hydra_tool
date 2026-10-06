package com.example.editor.integration;

import org.springframework.stereotype.Component;
import com.example.editor.definition.persistence.EditorDefinitionRepository;
import com.example.editor.event.persistence.EventBindingModelRepository;
import com.example.editor.event.flow.persistence.EventFlowModelRepository;
import com.example.editor.gamification.persistence.GamificationDefinitionRepository;
import com.example.editor.gamification.persistence.GamificationBindingRepository;
import com.example.editor.event.persistence.EventDefinitionModelRepository;
import com.example.editor.metamodel.behavior.persistence.OperationBehaviorModelRepository;
import com.example.editor.validation.action.persistence.ActionModelRepository;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.validation.persistence.ValidationModelRepository;
import com.example.editor.grammar.persistence.GrammarRepository;
import com.example.editor.textual.persistence.TextualEditorDefinitionRepository;
import com.example.editor.textual.persistence.TextualSyntaxStyleRepository;

/** Resuelve los documentos que ya existen en el proyecto actual. */
@Component
public class CurrentProjectIntegrationArtifactResolver implements IntegrationArtifactResolver {
    private final ExpressionModelRepository expressions;
    private final ValidationModelRepository validations;
    private final ActionModelRepository actions;
    private final OperationBehaviorModelRepository operationBehaviors;
    private final EventDefinitionModelRepository eventDefinitions;
    private final EventBindingModelRepository eventBindings;
    private final EditorDefinitionRepository editors;
    private final EventFlowModelRepository eventFlows;
    private final GamificationDefinitionRepository gamificationDefinitions;
    private final GamificationBindingRepository gamificationBindings;
    private final GrammarRepository grammars;
    private final TextualEditorDefinitionRepository textualEditors;
    private final TextualSyntaxStyleRepository textualStyles;

    /** Descripción: Inicializa una instancia de CurrentProjectIntegrationArtifactResolver con sus dependencias y datos de entrada. Entradas: expressions: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; operationBehaviors: dato de entrada necesario para la operación; eventDefinitions: dato de entrada necesario para la operación; eventBindings: dato de entrada necesario para la operación; editors: dato de entrada necesario para la operación; eventFlows: dato de entrada necesario para la operación; gamificationDefinitions: dato de entrada necesario para la operación; gamificationBindings: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CurrentProjectIntegrationArtifactResolver(ExpressionModelRepository expressions, ValidationModelRepository validations,
            ActionModelRepository actions, OperationBehaviorModelRepository operationBehaviors,
            EventDefinitionModelRepository eventDefinitions, EventBindingModelRepository eventBindings,
            EditorDefinitionRepository editors, EventFlowModelRepository eventFlows,
            GamificationDefinitionRepository gamificationDefinitions, GamificationBindingRepository gamificationBindings,
            GrammarRepository grammars, TextualEditorDefinitionRepository textualEditors, TextualSyntaxStyleRepository textualStyles) {
        this.expressions = expressions; this.validations = validations; this.actions = actions;
        this.operationBehaviors = operationBehaviors; this.eventDefinitions = eventDefinitions;
        this.eventBindings = eventBindings; this.editors = editors; this.eventFlows = eventFlows;
        this.gamificationDefinitions = gamificationDefinitions; this.gamificationBindings = gamificationBindings;
        this.grammars = grammars; this.textualEditors = textualEditors; this.textualStyles = textualStyles;
    }

    @Override
    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de CurrentProjectIntegrationArtifactResolver. Entradas: metamodelId: identificador que delimita el recurso objetivo; artifact: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public IntegrationArtifactResolution resolve(String metamodelId, IntegrationArtifactSelection artifact) {
        String semanticScopeId=metamodelId;
        return switch(artifact.kind()) {
            case GRAMMAR -> grammars.findById(artifact.documentId()).map(value -> resolved(artifact, "Grammar definition found"))
                    .orElseGet(() -> notFound(artifact, "Grammar definition was not found"));
            case TEXTUAL_EDITOR_DEFINITION -> textualEditors.find(artifact.documentId()).map(value -> resolved(artifact, "Textual editor definition found"))
                    .orElseGet(() -> notFound(artifact, "Textual editor definition was not found"));
            case TEXTUAL_SYNTAX_STYLE -> textualStyles.find(artifact.documentId()).map(value -> resolved(artifact, "Textual syntax style found"))
                    .orElseGet(() -> notFound(artifact, "Textual syntax style was not found"));
            case METAMODEL -> !semanticScopeId.startsWith("grammar.") && artifact.documentId().equals(semanticScopeId)
                    ? resolved(artifact, "Canonical metamodel is the owning document")
                    : incompatible(artifact, "Metamodel artifact is not the semantic root of this profile");
            case EXPRESSIONS -> expressions.findById(artifact.documentId())
                    .filter(value -> value.expressionModel().metamodelId().equals(metamodelId))
                    .map(value -> resolved(artifact, "Expression model found"))
                    .orElseGet(() -> notFound(artifact, "Expression model was not found for this metamodel"));
            case VALIDATIONS -> validations.findById(artifact.documentId())
                    .filter(value -> value.document().metamodelId().equals(metamodelId))
                    .map(value -> resolved(artifact, "Validation model found"))
                    .orElseGet(() -> notFound(artifact, "Validation model was not found for this metamodel"));
            case ACTIONS -> actions.findById(artifact.documentId())
                    .filter(value -> value.document().metamodelId().equals(metamodelId))
                    .map(value -> resolved(artifact, "Action model found"))
                    .orElseGet(() -> notFound(artifact, "Action model was not found for this metamodel"));
            case OPERATION_BEHAVIORS -> operationBehaviors.findById(artifact.documentId())
                    .filter(value -> value.document().metamodelId().equals(metamodelId))
                    .map(value -> resolved(artifact, "Operation behavior model found"))
                    .orElseGet(() -> notFound(artifact, "Operation behavior model was not found for this metamodel"));
            case EVENT_DEFINITIONS -> eventDefinitions.findByMetamodelId(metamodelId)
                    .filter(value -> value.document().id().equals(artifact.documentId()))
                    .map(value -> resolved(artifact, "Event definition model found"))
                    .orElseGet(() -> notFound(artifact, "Event definition model was not found for this metamodel"));
            case EVENT_BINDINGS -> eventBindings.findByMetamodelId(metamodelId)
                    .filter(value -> value.document().id().equals(artifact.documentId()))
                    .map(value -> resolved(artifact, "Event binding model found"))
                    .orElseGet(() -> notFound(artifact, "Event binding model was not found for this metamodel"));
            case EDITOR_DEFINITION -> editors.findById(artifact.documentId())
                    .filter(value -> value.document().metamodelId().equals(metamodelId))
                    .map(value -> resolved(artifact, "Editor definition found"))
                    .orElseGet(() -> notFound(artifact, "Editor definition was not found for this metamodel"));
            case CODE_GENERATION -> editors.findById(artifact.documentId())
                    .filter(value -> value.document().metamodelId().equals(metamodelId))
                    .filter(value -> value.document().codeGeneration().enabled())
                    .map(value -> resolved(artifact, "Code generation layer found in editor definition"))
                    .orElseGet(() -> notFound(artifact, "Code generation layer was not found or is disabled for this metamodel editor"));
            case EVENT_FLOWS -> eventFlows.findByMetamodelId(metamodelId)
                    .filter(value -> value.document().id().equals(artifact.documentId()))
                    .map(value -> resolved(artifact, "Event flow model found"))
                    .orElseGet(() -> notFound(artifact, "Event flow model was not found for this metamodel"));
            case GAMIFICATION_DEFINITIONS -> gamificationDefinitions.find(metamodelId)
                    .filter(value -> value.document().id().equals(artifact.documentId()))
                    .map(value -> resolved(artifact, "Gamification definition model found"))
                    .orElseGet(() -> notFound(artifact, "Gamification definition model was not found for this metamodel"));
            case GAMIFICATION_BINDINGS -> gamificationBindings.find(metamodelId)
                    .filter(value -> value.document().id().equals(artifact.documentId()))
                    .map(value -> resolved(artifact, "Gamification binding model found"))
                    .orElseGet(() -> notFound(artifact, "Gamification binding model was not found for this metamodel"));
            case NOTATION -> new IntegrationArtifactResolution(artifact, IntegrationResolutionStatus.DEFERRED,
                    "Notation translation remains target-specific and is preserved for a later adapter extension");
        };
    }

    /** Descripción: Obtiene la información correspondiente a «resolved» dentro de CurrentProjectIntegrationArtifactResolver. Entradas: artifact: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationArtifactResolution resolved(IntegrationArtifactSelection artifact, String message) { return new IntegrationArtifactResolution(artifact, IntegrationResolutionStatus.RESOLVED, message); }
    /** Descripción: Ejecuta la operación «notFound» correspondiente a la responsabilidad de CurrentProjectIntegrationArtifactResolver. Entradas: artifact: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationArtifactResolution notFound(IntegrationArtifactSelection artifact, String message) { return new IntegrationArtifactResolution(artifact, IntegrationResolutionStatus.NOT_FOUND, message); }
    /** Descripción: Ejecuta la operación «incompatible» correspondiente a la responsabilidad de CurrentProjectIntegrationArtifactResolver. Entradas: artifact: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private IntegrationArtifactResolution incompatible(IntegrationArtifactSelection artifact, String message) { return new IntegrationArtifactResolution(artifact, IntegrationResolutionStatus.INCOMPATIBLE, message); }
}
