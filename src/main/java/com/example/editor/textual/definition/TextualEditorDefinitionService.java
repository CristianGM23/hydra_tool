package com.example.editor.textual.definition;

import java.util.*;
import org.springframework.stereotype.Service;
import com.example.editor.grammar.application.*;
import com.example.editor.grammar.provider.*;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.textual.persistence.TextualEditorDefinitionRepository;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import com.example.editor.validation.validation.ValidationModelApplicationService;
import com.example.editor.validation.action.ActionModelApplicationService;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.flow.EventFlowApplicationService;
import com.example.editor.gamification.GamificationApplicationService;

@Service
public class TextualEditorDefinitionService {
    private final TextualEditorDefinitionRepository repository;
    private final GrammarApplicationService grammars;
    private final GrammarCompilationService compilations;
    private final GrammarProviderRegistry providers;
    private final ValidationModelApplicationService validations;
    private final ActionModelApplicationService actions;
    private final EventModelApplicationService events;
    private final EventFlowApplicationService eventFlows;
    private final GamificationApplicationService gamification;
    /** Inicializa el servicio y el catálogo de capas compartidas que pueden asociarse de forma automática al editor textual. */
    /** Descripción: Inicializa una instancia de TextualEditorDefinitionService con sus dependencias y datos de entrada. Entradas: r: dato de entrada necesario para la operación; g: dato de entrada necesario para la operación; c: dato de entrada necesario para la operación; p: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; events: dato de entrada necesario para la operación; eventFlows: dato de entrada necesario para la operación; gamification: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TextualEditorDefinitionService(TextualEditorDefinitionRepository r,GrammarApplicationService g,GrammarCompilationService c,GrammarProviderRegistry p,
            ValidationModelApplicationService validations,ActionModelApplicationService actions,EventModelApplicationService events,
            EventFlowApplicationService eventFlows,GamificationApplicationService gamification){
        repository=r;grammars=g;compilations=c;providers=p;this.validations=validations;this.actions=actions;this.events=events;this.eventFlows=eventFlows;this.gamification=gamification;
    }
    /** Descripción: Crea o prepara el resultado de «create» dentro de TextualEditorDefinitionService. Entradas: m: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<TextualEditorDefinitionModel>create(TextualEditorDefinitionModel m){validate(m);return repository.create(m);}
    /** Descripción: Actualiza el estado gestionado por TextualEditorDefinitionService mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; m: dato de entrada necesario para la operación; e: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<TextualEditorDefinitionModel>replace(String id,TextualEditorDefinitionModel m,String e){if(m==null||!id.equals(m.id()))throw new IllegalArgumentException("Path textual editor id and document id must match");validate(m);return repository.replace(id,m,e);}
    /** Descripción: Obtiene la información correspondiente a «find» dentro de TextualEditorDefinitionService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<TextualEditorDefinitionModel>find(String id){return repository.find(id).orElseThrow(()->new NoSuchElementException("Textual editor definition not found: "+id));}
    /** Descripción: Obtiene la información correspondiente a «list» dentro de TextualEditorDefinitionService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<StoredCanonicalDocument<TextualEditorDefinitionModel>>list(){return repository.listIds().stream().map(repository::find).flatMap(Optional::stream).toList();}
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; e: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String id,String e){find(id);repository.delete(id,e);}
    /** Descripción: Resuelve el snapshot efectivo del editor textual y sigue el snapshot activo salvo fijación explícita. Entradas: m: definición textual cuya política de snapshot debe aplicarse. Salida: devuelve el snapshot exitoso que debe gobernar el runtime textual. */ public GrammarCompilationSnapshot resolveSnapshot(TextualEditorDefinitionModel m){
        if(m==null||m.grammarId().isBlank())throw new IllegalArgumentException("Textual editor grammarId is required");
        if(followsActiveSnapshot(m))return compilations.active(m.grammarId()).orElseThrow(()->new IllegalStateException("Textual editor requires an active successful grammar snapshot"));
        if(!m.compilationSnapshotId().isBlank()){GrammarCompilationSnapshot s=compilations.find(m.compilationSnapshotId());if(!s.grammarId().equals(m.grammarId())||!s.successful())throw new IllegalArgumentException("Textual editor requires a successful snapshot of its grammar");return s;}
        return compilations.active(m.grammarId()).orElseThrow(()->new IllegalStateException("Textual editor requires an active successful grammar snapshot"));
    }

    /** Descripción: Ejecuta la operación «optionalSnapshot» correspondiente a la responsabilidad de TextualEditorDefinitionService. Entradas: m: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Optional<GrammarCompilationSnapshot> optionalSnapshot(TextualEditorDefinitionModel m){
        if(m==null||m.grammarId().isBlank())return Optional.empty();
        if(followsActiveSnapshot(m)||m.compilationSnapshotId().isBlank())return compilations.active(m.grammarId());
        try{GrammarCompilationSnapshot snapshot=compilations.find(m.compilationSnapshotId());return snapshot.grammarId().equals(m.grammarId())&&snapshot.successful()?Optional.of(snapshot):Optional.empty();}
        catch(NoSuchElementException exception){return Optional.empty();}
    }

    /** Descripción: Determina la política de actualización de gramática; FOLLOW_ACTIVE es el comportamiento por defecto y PINNED requiere declaración explícita. Entradas: model: definición textual que contiene la política solicitada. Salida: devuelve true cuando debe seguirse el snapshot activo de la gramática. */ private static boolean followsActiveSnapshot(TextualEditorDefinitionModel model){return !"PINNED".equalsIgnoreCase(model.settings().getOrDefault("hydra.grammarSnapshotPolicy","FOLLOW_ACTIVE"));}

    /** Resolve AUTO and prove every requested provider-backed feature against the actual pinned snapshot/runtime. */
    /** Descripción: Ejecuta la operación «negotiate» correspondiente a la responsabilidad de TextualEditorDefinitionService. Entradas: m: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TextualRuntimeNegotiation negotiate(TextualEditorDefinitionModel m){
        if(m==null||m.grammarId().isBlank())throw new IllegalArgumentException("Textual editor grammarId is required for runtime negotiation");
        var grammar=grammars.find(m.grammarId()).grammar();
        GrammarCompilationSnapshot snapshot=resolveSnapshot(m);
        GrammarProvider provider=providers.require(grammar.technology());
        var caps=provider.descriptor().capabilityDescriptor();
        TextualRuntimeMode effective=effectiveMode(m.runtimeMode(),snapshot,provider);
        List<TextualEditorFeature> supported=new ArrayList<>(),unavailable=new ArrayList<>();
        for(TextualEditorFeature feature:m.features())(supports(caps,effective,feature)?supported:unavailable).add(feature);
        List<String> extensions=m.fileExtensions().isEmpty()?snapshot.runtimeDescriptor().fileExtensions():m.fileExtensions();
        String entryRule=m.entryRule().isBlank()?snapshot.runtimeDescriptor().entryRule():m.entryRule();
        Map<String,String> evidence=new LinkedHashMap<>();
        evidence.put("runtimeMode",effective.name());evidence.put("providerTechnology",grammar.technology().name());
        evidence.put("liveCapabilities",String.join(",",caps.liveFeatureNames()));
        evidence.put("generatedHostCapabilities",String.join(",",caps.generatedHostFeatureNames()));
        evidence.put("languageServerAvailable",Boolean.toString(!snapshot.runtimeDescriptor().languageServerCommand().isBlank()));
        evidence.put("browserWorkerAvailable",Boolean.toString(!snapshot.runtimeDescriptor().browserWorkerEntry().isBlank()));
        evidence.put("snapshotSuccessful",Boolean.toString(snapshot.successful()));
        evidence.put("grammarSnapshotPolicy",followsActiveSnapshot(m)?"FOLLOW_ACTIVE":"PINNED");
        evidence.put("effectiveSnapshotId",snapshot.id());
        evidence.put("effectiveSourceHash",snapshot.sourceHash());
        return new TextualRuntimeNegotiation(m.runtimeMode(),effective,snapshot.id(),provider.descriptor().id(),extensions,entryRule,supported,unavailable,evidence);
    }

    /** Devuelve únicamente capas pertenecientes al contrato semántico real de la gramática compilada. */
    /** Descripción: Ejecuta la operación «sharedLayerOptions» correspondiente a la responsabilidad de TextualEditorDefinitionService. Entradas: m: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public TextualSharedLayerOptions sharedLayerOptions(TextualEditorDefinitionModel m){
        Optional<GrammarCompilationSnapshot> available=optionalSnapshot(m);
        if(available.isEmpty())return new TextualSharedLayerOptions("",List.of(),List.of(),List.of(),List.of(),List.of());
        GrammarCompilationSnapshot snapshot=available.get();
        String contract=snapshot.semanticContractId();
        if(contract.isBlank())return new TextualSharedLayerOptions("",List.of(),List.of(),List.of(),List.of(),List.of());
        List<String> validationIds=validations.findAll().stream().filter(item->contract.equals(item.metamodelId())).map(item->item.id()).toList();
        List<String> actionIds=actions.findAll().stream().filter(item->contract.equals(item.metamodelId())).map(item->item.id()).toList();
        List<String> eventIds=events.list().stream().filter(item->contract.equals(item.metamodelId())).map(item->item.definitionModelId()).toList();
        List<String> flowIds=eventFlows.list().stream().filter(item->contract.equals(item.metamodelId())).map(item->item.id()).toList();
        List<String> gameIds=gamification.list().stream().filter(item->contract.equals(item.metamodelId())).map(item->item.definitionModelId()).toList();
        return new TextualSharedLayerOptions(contract,validationIds,actionIds,eventIds,flowIds,gameIds);
    }

    /** Validates a textual-editor definition without forcing compilation merely to persist a draft. Runtime-dependent checks run as soon as a successful snapshot exists. */ /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: m: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validate(TextualEditorDefinitionModel m){
        if(m==null||m.id().isBlank()||m.name().isBlank()||m.grammarId().isBlank())throw new IllegalArgumentException("Textual editor id, name and grammarId are required");
        grammars.find(m.grammarId());
        Optional<GrammarCompilationSnapshot> snapshot=optionalSnapshot(m);
        if(snapshot.isEmpty()){
            if(!m.compilationSnapshotId().isBlank())throw new IllegalArgumentException("The selected grammar compilation is not a successful snapshot of this grammar");
            if(hasSharedLayers(m))throw new IllegalArgumentException("Compile the grammar successfully before associating runtime validation, events, flows or gamification");
            return;
        }
        TextualRuntimeNegotiation negotiation=negotiate(m);
        if(!negotiation.unavailableFeatures().isEmpty())throw new IllegalArgumentException("Textual editor requests capabilities unavailable in the negotiated runtime "+negotiation.effectiveMode()+": "+negotiation.unavailableFeatures());
        if(negotiation.entryRule().isBlank())throw new IllegalArgumentException("Textual editor requires an entry rule from the definition or compiled grammar snapshot");
        if(negotiation.fileExtensions().isEmpty())throw new IllegalArgumentException("Textual editor requires at least one file extension from the definition or compiled grammar snapshot");
        validateSharedLayers(m,sharedLayerOptions(m));
    }

    /** Descripción: Evalúa la condición «hasSharedLayers» sin modificar el estado externo. Entradas: m: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean hasSharedLayers(TextualEditorDefinitionModel m){
        return !m.validationModelIds().isEmpty()||!m.actionModelIds().isEmpty()||!m.eventDefinitionIds().isEmpty()||!m.eventFlowIds().isEmpty()||!m.gamificationDefinitionIds().isEmpty();
    }

    /** Rechaza asociaciones inexistentes o pertenecientes a otro contrato semántico. */
    /** Descripción: Valida las condiciones necesarias para «validateSharedLayers» y rechaza entradas no válidas. Entradas: m: dato de entrada necesario para la operación; available: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void validateSharedLayers(TextualEditorDefinitionModel m,TextualSharedLayerOptions available){
        requireSubset("validationModelIds",m.validationModelIds(),available.validationModelIds());
        requireSubset("actionModelIds",m.actionModelIds(),available.actionModelIds());
        requireSubset("eventDefinitionIds",m.eventDefinitionIds(),available.eventDefinitionIds());
        requireSubset("eventFlowIds",m.eventFlowIds(),available.eventFlowIds());
        requireSubset("gamificationDefinitionIds",m.gamificationDefinitionIds(),available.gamificationDefinitionIds());
    }
    /** Descripción: Valida las condiciones necesarias para «requireSubset» y rechaza entradas no válidas. Entradas: field: dato de entrada necesario para la operación; selected: dato de entrada necesario para la operación; available: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void requireSubset(String field,List<String> selected,List<String> available){
        LinkedHashSet<String> invalid=new LinkedHashSet<>(selected);invalid.removeAll(available);
        if(!invalid.isEmpty())throw new IllegalArgumentException("Textual editor "+field+" contains layers outside the compiled semantic contract: "+invalid);
    }

    /** Descripción: Ejecuta la operación «effectiveMode» correspondiente a la responsabilidad de TextualEditorDefinitionService. Entradas: requested: solicitud con los datos necesarios para ejecutar la operación; snapshot: dato de entrada necesario para la operación; provider: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static TextualRuntimeMode effectiveMode(TextualRuntimeMode requested,GrammarCompilationSnapshot snapshot,GrammarProvider provider){
        TextualRuntimeMode mode=requested==null?TextualRuntimeMode.AUTO:requested;
        var runtime=snapshot.runtimeDescriptor();var caps=provider.descriptor().capabilityDescriptor();
        if(mode==TextualRuntimeMode.AUTO){
            if(!runtime.browserWorkerEntry().isBlank()&&caps.supportsGeneratedHost(GrammarProviderCapability.BROWSER_WORKER))return TextualRuntimeMode.BROWSER_WORKER;
            if(!runtime.languageServerCommand().isBlank()&&caps.supportsGeneratedHost(GrammarProviderCapability.LANGUAGE_SERVER))return TextualRuntimeMode.LANGUAGE_SERVER;
            if(caps.supportsLive(GrammarProviderCapability.TEXTUAL_RUNTIME))return TextualRuntimeMode.PARSER_BRIDGE;
            throw new IllegalArgumentException("The active grammar snapshot exposes no usable textual runtime");
        }
        if(mode==TextualRuntimeMode.BROWSER_WORKER&&(!caps.supportsGeneratedHost(GrammarProviderCapability.BROWSER_WORKER)||runtime.browserWorkerEntry().isBlank()))throw new IllegalArgumentException("BROWSER_WORKER is not available for the active grammar snapshot");
        if(mode==TextualRuntimeMode.LANGUAGE_SERVER&&(!caps.supportsGeneratedHost(GrammarProviderCapability.LANGUAGE_SERVER)||runtime.languageServerCommand().isBlank()))throw new IllegalArgumentException("LANGUAGE_SERVER is not available for the active grammar snapshot");
        if(mode==TextualRuntimeMode.PARSER_BRIDGE&&!caps.supportsLive(GrammarProviderCapability.TEXTUAL_RUNTIME))throw new IllegalArgumentException("PARSER_BRIDGE is not available for the active grammar snapshot");
        return mode;
    }

    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: caps: dato de entrada necesario para la operación; mode: dato de entrada necesario para la operación; feature: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean supports(GrammarProviderCapabilities caps,TextualRuntimeMode mode,TextualEditorFeature feature){
        if(feature==TextualEditorFeature.DIAGNOSTICS||feature==TextualEditorFeature.QUICK_FIXES||feature==TextualEditorFeature.OUTLINE)return true; // provided by HYDRA canonical runtime/source-map layers
        GrammarProviderCapability capability=switch(feature){
            case COMPLETION->GrammarProviderCapability.COMPLETION;case HOVER->GrammarProviderCapability.HOVER;case GO_TO_DEFINITION->GrammarProviderCapability.DEFINITION;
            case FIND_REFERENCES->GrammarProviderCapability.REFERENCES;case RENAME->GrammarProviderCapability.RENAME;case FORMATTING->GrammarProviderCapability.FORMATTING;
            case FOLDING->GrammarProviderCapability.FOLDING;case SEMANTIC_TOKENS->GrammarProviderCapability.SEMANTIC_TOKENS;
            case OUTLINE->GrammarProviderCapability.DOCUMENT_SYMBOLS;case DIAGNOSTICS->GrammarProviderCapability.ANALYZE;case QUICK_FIXES->GrammarProviderCapability.CODE_ACTIONS;};
        if(caps.supportsLive(capability))return true;
        return (mode==TextualRuntimeMode.LANGUAGE_SERVER||mode==TextualRuntimeMode.BROWSER_WORKER)&&caps.supportsGeneratedHost(capability);
    }
}
