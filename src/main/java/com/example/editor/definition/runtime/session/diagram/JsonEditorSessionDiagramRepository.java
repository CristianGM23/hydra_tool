package com.example.editor.definition.runtime.session.diagram;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import com.example.editor.definition.runtime.session.EditorSessionProperties;
import com.example.editor.persistence.CanonicalDocumentStore;
import com.example.editor.persistence.CanonicalDocumentStoreFactory;
import com.example.editor.persistence.CanonicalDocumentStoreSpec;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.ObjectMapper;
@Repository
public class JsonEditorSessionDiagramRepository implements EditorSessionDiagramRepository {
    private final CanonicalDocumentStore<EditorSessionDiagramDocument> store;
    @Autowired
    /** Descripción: Inicializa una instancia de JsonEditorSessionDiagramRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento; stores: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonEditorSessionDiagramRepository(EditorSessionProperties properties, ObjectMapper mapper, CanonicalDocumentStoreFactory stores) {
        store = stores.create(new CanonicalDocumentStoreSpec<>("editor.session.diagram", properties.getDirectory(), properties.getDiagramFileSuffix(), properties.isUseEtag(), mapper, EditorSessionDiagramDocument.class, EditorSessionDiagramDocument::id));
    }
    /** Descripción: Inicializa una instancia de JsonEditorSessionDiagramRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonEditorSessionDiagramRepository(EditorSessionProperties properties, ObjectMapper mapper) {
        this(properties, mapper, new LocalCanonicalDocumentStoreFactory());
    }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de JsonEditorSessionDiagramRepository. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredCanonicalDocument<EditorSessionDiagramDocument>> find(String sessionId) { return store.find(sessionId); }
    /** Descripción: Actualiza el estado gestionado por JsonEditorSessionDiagramRepository mediante la operación «save». Entradas: document: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<EditorSessionDiagramDocument> save(EditorSessionDiagramDocument document, String etag) {
        return store.exists(document.id()) ? store.replace(document.id(), document, etag) : store.create(document);
    }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: sessionId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteIfExists(String sessionId) { store.deleteIfExists(sessionId); }
}
