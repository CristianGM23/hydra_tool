package com.example.editor.metamodel.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.editor.grammar.persistence.GrammarRepository;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjection;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/**
 * Resuelve contextos semánticos no persistidos sobre la representación neutral de HYDRA.
 *
 * Una gramática grammar-first se expone como {@code grammar.<grammarId>} únicamente en memoria.
 * Nunca se escribe un metamodelo canónico artificial ni se cambia la identidad persistida de la gramática.
 */
@Service
public class SemanticContextMetamodelResolver {
    public static final String GRAMMAR_PREFIX = "grammar.";

    private final GrammarRepository grammars;
    private final GrammarClassDiagramProjectionService grammarProjection;

    /** Descripción: Inicializa el resolver de contextos semánticos transitorios. Entradas: grammars: repositorio de gramáticas; grammarProjection: servicio que infiere su estructura neutral. Salida: deja el resolver preparado sin persistir artefactos derivados. */
    /** Descripción: Inicializa una instancia de SemanticContextMetamodelResolver con sus dependencias y datos de entrada. Entradas: grammars: dato de entrada necesario para la operación; grammarProjection: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public SemanticContextMetamodelResolver(GrammarRepository grammars, GrammarClassDiagramProjectionService grammarProjection) {
        this.grammars = grammars;
        this.grammarProjection = grammarProjection;
    }

    /** Descripción: Intenta resolver un identificador semántico transitorio. Entradas: semanticContextId: identificador estable del contexto. Salida: devuelve la proyección neutral y su ETag de origen cuando el contexto es una gramática existente. */
    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de SemanticContextMetamodelResolver. Entradas: semanticContextId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<MetamodelLoadResult> resolve(String semanticContextId) {
        String grammarId = grammarId(semanticContextId);
        if(grammarId.isBlank()) return Optional.empty();
        return grammars.findById(grammarId).map(stored -> project(semanticContextId, stored));
    }

    /** Descripción: Determina si el identificador pertenece al espacio reservado de contextos grammar-first. Entradas: semanticContextId: identificador a comprobar. Salida: devuelve true solo para IDs grammar.<id> con sufijo no vacío. */
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: semanticContextId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean supports(String semanticContextId) {
        return !grammarId(semanticContextId).isBlank();
    }

    /** Descripción: Proyecta una gramática persistida al metamodelo neutral transitorio usado por las capas. Entradas: semanticContextId: identidad estable de capa; stored: gramática persistida con su versión. Salida: devuelve un MetamodelLoadResult cuya estructura se deriva en memoria. */
    /** Descripción: Ejecuta la operación «project» correspondiente a la responsabilidad de SemanticContextMetamodelResolver. Entradas: semanticContextId: identificador que delimita el recurso objetivo; stored: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelLoadResult project(String semanticContextId, StoredCanonicalDocument<com.example.editor.grammar.core.GrammarDefinitionModel> stored) {
        GrammarClassDiagramProjection projection = grammarProjection.project(stored.document(), (com.example.editor.grammar.core.GrammarAnalysisResult)null);
        MetaModel inferred = projection.inferredMetamodel();
        Map<String,String> annotations = new LinkedHashMap<>(inferred.annotations());
        annotations.put("hydra.semanticContext.kind", "GRAMMAR");
        annotations.put("hydra.semanticContext.transient", "true");
        annotations.put("hydra.grammar.id", stored.document().id());
        annotations.put("hydra.grammar.projectionStatus", projection.status().name());
        if(!projection.sourceHash().isBlank()) annotations.put("hydra.grammar.sourceHash", projection.sourceHash());
        if(!projection.projectionHash().isBlank()) annotations.put("hydra.grammar.projectionHash", projection.projectionHash());
        MetaModel semantic = new MetaModel(inferred.schemaVersion(), semanticContextId, stored.document().name(),
                inferred.classes(), inferred.enums(), inferred.dataTypes(), inferred.relations(), annotations);
        return new MetamodelLoadResult(semantic, stored.etag());
    }

    /** Descripción: Extrae el ID de gramática únicamente desde el prefijo de contexto reservado. Entradas: semanticContextId: identificador recibido por una capa. Salida: devuelve el ID de gramática o cadena vacía cuando no corresponde a grammar-first. */
    /** Descripción: Ejecuta la operación «grammarId» correspondiente a la responsabilidad de SemanticContextMetamodelResolver. Entradas: semanticContextId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String grammarId(String semanticContextId) {
        if(semanticContextId == null) return "";
        String value = semanticContextId.trim();
        if(!value.startsWith(GRAMMAR_PREFIX) || value.length() <= GRAMMAR_PREFIX.length()) return "";
        return value.substring(GRAMMAR_PREFIX.length());
    }
}
