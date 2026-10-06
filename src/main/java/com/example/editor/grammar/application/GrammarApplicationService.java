package com.example.editor.grammar.application;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.persistence.GrammarRepository;
import com.example.editor.grammar.provider.GrammarProviderDescriptor;
import com.example.editor.grammar.provider.GrammarProviderRegistry;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/** Persistence/application boundary for editable grammars with defensive normalization and provider-driven settings. */
@Service
public class GrammarApplicationService {
    private final GrammarRepository repository;
    private final GrammarProperties properties;
    private final GrammarToolchainProfileService profiles;
    private final GrammarProviderRegistry providers;

    /** Descripción: Inicializa una instancia de GrammarApplicationService con sus dependencias y datos de entrada. Entradas: repository: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; profiles: archivo o contenido que debe procesarse; providers: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarApplicationService(GrammarRepository repository,GrammarProperties properties,
            GrammarToolchainProfileService profiles, GrammarProviderRegistry providers){
        this.repository=repository;this.properties=properties;this.profiles=profiles;this.providers=providers;
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de GrammarApplicationService. Entradas: grammar: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarSaveResponse create(GrammarDefinitionModel grammar){
        validate(grammar);ensureUniqueName(grammar.id(),grammar.name());
        StoredCanonicalDocument<GrammarDefinitionModel> stored=repository.create(grammar);
        return new GrammarSaveResponse(stored.document(),stored.etag());
    }
    /** Descripción: Actualiza el estado gestionado por GrammarApplicationService mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; grammar: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarSaveResponse replace(String id,GrammarDefinitionModel grammar,String etag){
        if(grammar==null||!id.equals(grammar.id()))throw new IllegalArgumentException("Path grammar id and document id must match");
        validate(grammar);ensureUniqueName(id,grammar.name());
        StoredCanonicalDocument<GrammarDefinitionModel> stored=repository.replace(id,grammar,etag);
        return new GrammarSaveResponse(stored.document(),stored.etag());
    }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de GrammarApplicationService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarLoadResponse find(String id){
        StoredCanonicalDocument<GrammarDefinitionModel> stored=repository.findById(id).orElseThrow(()->new NoSuchElementException("Grammar not found: "+id));
        return new GrammarLoadResponse(stored.document(),stored.etag());
    }
    /** Descripción: Obtiene la información correspondiente a «list» dentro de GrammarApplicationService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<GrammarSummary> list(){
        return repository.listIds().stream().map(repository::findById).flatMap(Optional::stream)
                .map(stored->new GrammarSummary(stored.document().id(),stored.document().name(),stored.document().technology(),
                        stored.document().documents().size(),stored.document().toolchainProfileId(),stored.etag()))
                .sorted(Comparator.comparing(GrammarSummary::name,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String id,String etag){find(id);repository.delete(id,etag);}

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: grammar: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void validate(GrammarDefinitionModel grammar){
        if(grammar==null)throw new IllegalArgumentException("Grammar is required");
        if(grammar.id().isBlank()||!grammar.id().matches("[A-Za-z][A-Za-z0-9_.-]{1,127}"))throw new IllegalArgumentException("Grammar id is invalid");
        if(grammar.name().isBlank())throw new IllegalArgumentException("Grammar name is required");
        if(!"1.0".equals(grammar.schemaVersion())) throw new IllegalArgumentException("Unsupported grammar schemaVersion: "+grammar.schemaVersion());
        if(grammar.documents().isEmpty())throw new IllegalArgumentException("At least one grammar document is required");
        if(grammar.documents().size()>properties.getMaximumDocuments())throw new IllegalArgumentException("Too many grammar documents");
        if(grammar.semanticBindings().bindings().size()>properties.getMaximumSemanticBindings())throw new IllegalArgumentException("Grammar semantic binding limit exceeded");
        if(grammar.authoringMode()==GrammarAuthoringMode.METAMODEL_FIRST && grammar.metamodelId().isBlank())throw new IllegalArgumentException("METAMODEL_FIRST requires a metamodelId");
        if(grammar.authoringMode()==GrammarAuthoringMode.LINKED && grammar.metamodelId().isBlank())throw new IllegalArgumentException("LINKED authoring requires a metamodelId");

        Set<String> ids=new HashSet<>(),paths=new HashSet<>();
        boolean entry=false;
        long totalBytes=0;
        for(GrammarSourceDocument document:grammar.documents()){
            if(!ids.add(document.id()))throw new IllegalArgumentException("Duplicate grammar document id: "+document.id());
            if(!paths.add(document.relativePath().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Duplicate grammar document path: "+document.relativePath());
            long bytes=document.content().getBytes(StandardCharsets.UTF_8).length;
            totalBytes+=bytes;
            if(bytes>properties.getMaximumDocumentBytes())throw new IllegalArgumentException("Grammar document exceeds configured size limit: "+document.relativePath());
            if(document.entryPoint()||document.id().equals(grammar.entryDocumentId()))entry=true;
        }
        if(totalBytes>properties.getMaximumGrammarBytes())throw new IllegalArgumentException("Grammar exceeds configured total size limit");
        if(!entry)throw new IllegalArgumentException("The entry grammar document does not exist");
        if(grammar.entryRule().isBlank()) throw new IllegalArgumentException("Grammar entryRule is required");

        GrammarToolchainProfile profile=profiles.require(grammar.toolchainProfileId(),grammar.technology());
        GrammarProviderDescriptor descriptor=providers.require(grammar.technology()).descriptor();
        if(!descriptor.supportedTargets().isEmpty()&&!descriptor.supportedTargets().stream().anyMatch(target->target.equalsIgnoreCase(profile.targetLanguage())))
            throw new IllegalArgumentException("Toolchain target "+profile.targetLanguage()+" is not supported by provider "+descriptor.id());
        if(!descriptor.supportedBuildSystems().isEmpty()&&!descriptor.supportedBuildSystems().stream().anyMatch(build->build.equalsIgnoreCase(profile.buildSystem())))
            throw new IllegalArgumentException("Build system "+profile.buildSystem()+" is not supported by provider "+descriptor.id());
    }

    /** Descripción: Valida las condiciones necesarias para «ensureUniqueName» y rechaza entradas no válidas. Entradas: ownId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void ensureUniqueName(String ownId,String name){
        for(String id:repository.listIds()){
            if(id.equals(ownId))continue;
            repository.findById(id).ifPresent(stored->{if(stored.document().name().equalsIgnoreCase(name))throw new IllegalArgumentException("Grammar name already exists: "+name);});
        }
    }
}
