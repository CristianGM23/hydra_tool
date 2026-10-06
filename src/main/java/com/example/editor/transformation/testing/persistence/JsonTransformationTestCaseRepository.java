package com.example.editor.transformation.testing.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.editor.persistence.CanonicalDocumentStore;
import com.example.editor.persistence.CanonicalDocumentStoreFactory;
import com.example.editor.persistence.CanonicalDocumentStoreSpec;
import com.example.editor.transformation.TransformationProperties;
import com.example.editor.transformation.testing.TransformationTestCase;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonTransformationTestCaseRepository implements TransformationTestCaseRepository {
    private final CanonicalDocumentStore<TransformationTestCase> store;

    /** Descripción: Inicializa el repositorio JSON de casos de prueba. Entradas: properties: configuración; mapper: serializador; stores: fábrica de almacenes. Salida: deja el repositorio preparado. */
    /** Descripción: Inicializa una instancia de JsonTransformationTestCaseRepository con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento; stores: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public JsonTransformationTestCaseRepository(TransformationProperties properties, ObjectMapper mapper, CanonicalDocumentStoreFactory stores) {
        store = stores.create(new CanonicalDocumentStoreSpec<>("transformation.test-case", properties.getTestDirectory(),
                properties.getTestFileSuffix(), properties.isUseEtag(), mapper, TransformationTestCase.class, TransformationTestCase::id));
    }

    /** Descripción: Crea un caso de prueba. Entradas: testCase: caso a persistir. Salida: devuelve el documento almacenado. */ public StoredCanonicalDocument<TransformationTestCase> create(TransformationTestCase testCase) { return store.create(testCase); }
    /** Descripción: Reemplaza un caso de prueba. Entradas: id: identificador; testCase: caso; etag: versión esperada. Salida: devuelve el documento almacenado. */ public StoredCanonicalDocument<TransformationTestCase> replace(String id, TransformationTestCase testCase, String etag) { return store.replace(id, testCase, etag); }
    /** Descripción: Busca un caso de prueba. Entradas: id: identificador. Salida: devuelve el documento cuando existe. */ public Optional<StoredCanonicalDocument<TransformationTestCase>> find(String id) { return store.find(id); }
    /** Descripción: Enumera identificadores persistidos. Entradas: no requiere parámetros. Salida: devuelve los identificadores. */ public List<String> listIds() { return store.listIds(); }
    /** Descripción: Elimina un caso de prueba. Entradas: id: identificador; etag: versión esperada. Salida: no devuelve valor. */ public void delete(String id, String etag) { store.delete(id, etag); }
}
