package com.example.editor.grammar.persistence;
import java.util.*;
import org.springframework.stereotype.Repository;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.runtime.GrammarActivationState;
import com.example.editor.persistence.*;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.ObjectMapper;
@Repository
public class JsonGrammarActivationRepository implements GrammarActivationRepository {
    private final CanonicalDocumentStore<GrammarActivationState> store;
    /** Descripción: Inicializa una instancia de JsonGrammarActivationRepository con sus dependencias y datos de entrada. Entradas: p: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento; stores: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonGrammarActivationRepository(GrammarProperties p,ObjectMapper mapper,CanonicalDocumentStoreFactory stores){store=stores.create(new CanonicalDocumentStoreSpec<>("grammar.activation",p.getActivationDirectory(),p.getActivationFileSuffix(),false,mapper,GrammarActivationState.class,GrammarActivationState::grammarId));}
    /** Descripción: Actualiza el estado gestionado por JsonGrammarActivationRepository mediante la operación «save». Entradas: s: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<GrammarActivationState> save(GrammarActivationState s){return store.exists(s.grammarId())?store.replace(s.grammarId(),s,""):store.create(s);} /** Descripción: Obtiene la información correspondiente a «find» dentro de JsonGrammarActivationRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredCanonicalDocument<GrammarActivationState>> find(String id){return store.find(id);}
}
