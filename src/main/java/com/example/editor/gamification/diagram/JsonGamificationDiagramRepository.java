package com.example.editor.gamification.diagram;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import com.example.editor.gamification.GamificationProperties;
import com.example.editor.persistence.CanonicalDocumentStore;
import com.example.editor.persistence.CanonicalDocumentStoreFactory;
import com.example.editor.persistence.CanonicalDocumentStoreSpec;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonGamificationDiagramRepository implements GamificationDiagramRepository {
    private final CanonicalDocumentStore<GamificationDiagramDocument> store;
    @Autowired
    /** Descripción: Inicializa una instancia de JsonGamificationDiagramRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento; stores: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonGamificationDiagramRepository(GamificationProperties properties, ObjectMapper mapper, CanonicalDocumentStoreFactory stores) {
        store = stores.create(new CanonicalDocumentStoreSpec<>("gamification.diagram", properties.getDirectory(), properties.getDiagramFileSuffix(), properties.isUseEtag(), mapper, GamificationDiagramDocument.class, GamificationDiagramDocument::metamodelId));
    }
    /** Descripción: Inicializa una instancia de JsonGamificationDiagramRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonGamificationDiagramRepository(GamificationProperties properties, ObjectMapper mapper) {
        this(properties, mapper, new LocalCanonicalDocumentStoreFactory());
    }
    /** Descripción: Crea o prepara el resultado de «create» dentro de JsonGamificationDiagramRepository. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<GamificationDiagramDocument> create(GamificationDiagramDocument document) { return store.create(document); }
    /** Descripción: Actualiza el estado gestionado por JsonGamificationDiagramRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<GamificationDiagramDocument> replace(String id, GamificationDiagramDocument document, String etag) { return store.replace(id, document, etag); }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de JsonGamificationDiagramRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredCanonicalDocument<GamificationDiagramDocument>> find(String id) { return store.find(id); }
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String id, String etag) { store.delete(id, etag); }
    /** Descripción: Elimina o libera el recurso gestionado por «deleteIfExists» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void deleteIfExists(String id) { store.deleteIfExists(id); }
}
