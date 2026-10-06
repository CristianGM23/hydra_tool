package com.example.editor.integration.compiler;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.example.editor.definition.persistence.EditorDefinitionRepository;
import com.example.editor.event.flow.persistence.EventFlowModelRepository;
import com.example.editor.event.persistence.EventBindingModelRepository;
import com.example.editor.event.persistence.EventDefinitionModelRepository;
import com.example.editor.gamification.persistence.GamificationBindingRepository;
import com.example.editor.gamification.persistence.GamificationDefinitionRepository;
import com.example.editor.integration.IntegrationArtifactSelection;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.projection.CanonicalArtifactContent;
import com.example.editor.grammar.persistence.GrammarRepository;
import com.example.editor.textual.persistence.TextualEditorDefinitionRepository;
import com.example.editor.textual.persistence.TextualSyntaxStyleRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.behavior.persistence.OperationBehaviorModelRepository;
import com.example.editor.validation.action.persistence.ActionModelRepository;
import com.example.editor.validation.expression.persistence.ExpressionModelRepository;
import com.example.editor.validation.validation.persistence.ValidationModelRepository;
import tools.jackson.databind.json.JsonMapper;

/** Carga documentos del proyecto actual y los entrega como contenido neutral serializado. */
@Component
public class CurrentProjectCanonicalArtifactContentLoader implements CanonicalIntegrationArtifactContentLoader {
    private final CanonicalMetamodelApplicationService metamodels;
    private final ExpressionModelRepository expressions;
    private final ValidationModelRepository validations;
    private final ActionModelRepository actions;
    private final OperationBehaviorModelRepository operationBehaviors;
    private final EventDefinitionModelRepository eventDefinitions;
    private final EventBindingModelRepository eventBindings;
    private final EventFlowModelRepository eventFlows;
    private final GamificationDefinitionRepository gamificationDefinitions;
    private final GamificationBindingRepository gamificationBindings;
    private final EditorDefinitionRepository editors;
    private final GrammarRepository grammars;
    private final TextualEditorDefinitionRepository textualEditors;
    private final TextualSyntaxStyleRepository textualStyles;
    private final JsonMapper mapper;

    /** Descripción: Inicializa una instancia de CurrentProjectCanonicalArtifactContentLoader con sus dependencias y datos de entrada. Entradas: metamodels: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; operationBehaviors: dato de entrada necesario para la operación; eventDefinitions: dato de entrada necesario para la operación; eventBindings: dato de entrada necesario para la operación; eventFlows: dato de entrada necesario para la operación; gamificationDefinitions: dato de entrada necesario para la operación; gamificationBindings: dato de entrada necesario para la operación; editors: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public CurrentProjectCanonicalArtifactContentLoader(CanonicalMetamodelApplicationService metamodels,
            ExpressionModelRepository expressions, ValidationModelRepository validations, ActionModelRepository actions,
            OperationBehaviorModelRepository operationBehaviors, EventDefinitionModelRepository eventDefinitions,
            EventBindingModelRepository eventBindings, EventFlowModelRepository eventFlows,
            GamificationDefinitionRepository gamificationDefinitions, GamificationBindingRepository gamificationBindings,
            EditorDefinitionRepository editors, GrammarRepository grammars, TextualEditorDefinitionRepository textualEditors,
            TextualSyntaxStyleRepository textualStyles, JsonMapper mapper) {
        this.metamodels = metamodels; this.expressions = expressions; this.validations = validations; this.actions = actions;
        this.operationBehaviors = operationBehaviors; this.eventDefinitions = eventDefinitions; this.eventBindings = eventBindings;
        this.eventFlows = eventFlows; this.gamificationDefinitions = gamificationDefinitions;
        this.gamificationBindings = gamificationBindings; this.editors = editors; this.grammars = grammars;
        this.textualEditors = textualEditors; this.textualStyles = textualStyles; this.mapper = mapper;
    }

    /** Descripción: Obtiene la información correspondiente a «load» dentro de CurrentProjectCanonicalArtifactContentLoader. Entradas: profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ @Override public List<CanonicalArtifactContent> load(IntegrationProfileModel profile){
        List<CanonicalArtifactContent> result = new ArrayList<>();
        for(IntegrationArtifactSelection selection : profile.artifacts()){
            Object document = loadDocument(profile, selection);
            if(document == null){
                if(selection.required()){throw new TechnologyCompilationException("Required canonical artifact was not found: " + selection.kind() + " / " + selection.documentId());}
                continue;
            }
            try{
                result.add(new CanonicalArtifactContent(selection.kind(), selection.documentId(), "application/json", mapper.writeValueAsBytes(document)));
            }catch(RuntimeException exception){
                throw new TechnologyCompilationException("Could not serialize canonical artifact: " + selection.kind(), exception);
            }
        }
        return List.copyOf(result);
    }

    /** Descripción: Obtiene la información correspondiente a «loadDocument» dentro de CurrentProjectCanonicalArtifactContentLoader. Entradas: profile: archivo o contenido que debe procesarse; selection: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object loadDocument(IntegrationProfileModel profile, IntegrationArtifactSelection selection){
        String metamodelId = profile.metamodelId();
        String semanticScopeId = profile.semanticScopeId();
        return switch(selection.kind()){
            case GRAMMAR -> grammars.findById(selection.documentId()).map(value -> value.document()).orElse(null);
            case TEXTUAL_EDITOR_DEFINITION -> textualEditors.find(selection.documentId()).map(value -> value.document()).orElse(null);
            case TEXTUAL_SYNTAX_STYLE -> textualStyles.find(selection.documentId()).map(value -> value.document()).orElse(null);
            case METAMODEL -> metamodelId.isBlank() ? null : metamodels.findById(metamodelId).metamodel();
            case EXPRESSIONS -> expressions.findById(selection.documentId()).map(value -> value.expressionModel()).orElse(null);
            case VALIDATIONS -> validations.findById(selection.documentId()).map(value -> value.document()).orElse(null);
            case ACTIONS -> actions.findById(selection.documentId()).map(value -> value.document()).orElse(null);
            case OPERATION_BEHAVIORS -> operationBehaviors.findById(selection.documentId()).map(value -> value.document()).orElse(null);
            case EVENT_DEFINITIONS -> eventDefinitions.findByMetamodelId(semanticScopeId).filter(value -> value.document().id().equals(selection.documentId())).map(value -> value.document()).orElse(null);
            case EVENT_BINDINGS -> eventBindings.findByMetamodelId(semanticScopeId).filter(value -> value.document().id().equals(selection.documentId())).map(value -> value.document()).orElse(null);
            case EVENT_FLOWS -> eventFlows.findByMetamodelId(semanticScopeId).filter(value -> value.document().id().equals(selection.documentId())).map(value -> value.document()).orElse(null);
            case GAMIFICATION_DEFINITIONS -> gamificationDefinitions.find(semanticScopeId).filter(value -> value.document().id().equals(selection.documentId())).map(value -> value.document()).orElse(null);
            case GAMIFICATION_BINDINGS -> gamificationBindings.find(semanticScopeId).filter(value -> value.document().id().equals(selection.documentId())).map(value -> value.document()).orElse(null);
            case EDITOR_DEFINITION -> editors.findById(selection.documentId()).map(value -> value.document()).orElse(null);
            case CODE_GENERATION -> editors.findById(selection.documentId()).map(value -> value.document().codeGeneration()).orElse(null);
            case NOTATION -> null;
        };
    }
}
