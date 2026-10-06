package com.example.editor.transformation.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.example.editor.persistence.CanonicalDocumentStore;
import com.example.editor.persistence.CanonicalDocumentStoreFactory;
import com.example.editor.persistence.CanonicalDocumentStoreSpec;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.transformation.TransformationProperties;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonTransformationRepository implements TransformationRepository {
    private final CanonicalDocumentStore<TransformationDefinitionDocument> store;

    @Autowired
    /** Descripción: Inicializa una instancia de JsonTransformationRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento; stores: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonTransformationRepository(TransformationProperties properties, ObjectMapper mapper, CanonicalDocumentStoreFactory stores) {
        store = stores.create(new CanonicalDocumentStoreSpec<>(
                "transformation.definition",
                properties.getDirectory(),
                properties.getFileSuffix(),
                properties.isUseEtag(),
                mapper,
                TransformationDefinitionDocument.class,
                TransformationDefinitionDocument::id));
    }

    /** Descripción: Inicializa una instancia de JsonTransformationRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public JsonTransformationRepository(TransformationProperties properties, ObjectMapper mapper) {
        this(properties, mapper, new LocalCanonicalDocumentStoreFactory());
    }

    /** Descripción: Crea o prepara el resultado de «create» dentro de JsonTransformationRepository. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<TransformationDefinitionDocument> create(TransformationDefinitionDocument document) {
        return store.create(document);
    }

    /** Descripción: Actualiza el estado gestionado por JsonTransformationRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; document: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StoredCanonicalDocument<TransformationDefinitionDocument> replace(String id, TransformationDefinitionDocument document, String etag) {
        return store.replace(id, document, etag);
    }

    /** Descripción: Obtiene la información correspondiente a «find» dentro de JsonTransformationRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<StoredCanonicalDocument<TransformationDefinitionDocument>> find(String id) {
        return store.find(id);
    }

    /** Descripción: Obtiene la información correspondiente a «listIds» dentro de JsonTransformationRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> listIds() {
        return store.listIds();
    }

    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void delete(String id, String etag) {
        store.delete(id, etag);
    }
}
