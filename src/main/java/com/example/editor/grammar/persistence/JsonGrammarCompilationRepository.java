package com.example.editor.grammar.persistence;
import java.util.*;
import org.springframework.stereotype.Repository;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.persistence.*;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.ObjectMapper;
@Repository
public class JsonGrammarCompilationRepository implements GrammarCompilationRepository {
    private final CanonicalDocumentStore<GrammarCompilationSnapshot> store;
    /** Descripción: Inicializa una instancia de JsonGrammarCompilationRepository con sus dependencias y datos de entrada. Entradas: p: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento; stores: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonGrammarCompilationRepository(GrammarProperties p,ObjectMapper mapper,CanonicalDocumentStoreFactory stores){store=stores.create(new CanonicalDocumentStoreSpec<>("grammar.compilation",p.getCompilationDirectory(),p.getCompilationFileSuffix(),true,mapper,GrammarCompilationSnapshot.class,GrammarCompilationSnapshot::id));}
    /** Descripción: Crea o prepara el resultado de «create» dentro de JsonGrammarCompilationRepository. Entradas: s: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<GrammarCompilationSnapshot> create(GrammarCompilationSnapshot s){return store.create(s);} /** Descripción: Obtiene la información correspondiente a «findById» dentro de JsonGrammarCompilationRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredCanonicalDocument<GrammarCompilationSnapshot>> findById(String id){return store.find(id);} /** Descripción: Obtiene la información correspondiente a «listIds» dentro de JsonGrammarCompilationRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> listIds(){return store.listIds();}
}
