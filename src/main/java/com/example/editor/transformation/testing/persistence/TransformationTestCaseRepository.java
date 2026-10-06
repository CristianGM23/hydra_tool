package com.example.editor.transformation.testing.persistence;

import java.util.List;
import java.util.Optional;

import com.example.editor.transformation.testing.TransformationTestCase;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

public interface TransformationTestCaseRepository {
    /** Descripción: Crea un caso de prueba persistente. Entradas: testCase: caso de prueba. Salida: devuelve el documento almacenado. */
    /** Descripción: Crea o prepara el resultado de «create» dentro de TransformationTestCaseRepository. Entradas: testCase: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<TransformationTestCase> create(TransformationTestCase testCase);
    /** Descripción: Reemplaza un caso de prueba aplicando control optimista. Entradas: id: identificador; testCase: documento; etag: versión esperada. Salida: devuelve el documento almacenado. */
    /** Descripción: Actualiza el estado gestionado por TransformationTestCaseRepository mediante la operación «replace». Entradas: id: identificador que delimita el recurso objetivo; testCase: dato de entrada necesario para la operación; etag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ StoredCanonicalDocument<TransformationTestCase> replace(String id, TransformationTestCase testCase, String etag);
    /** Descripción: Busca un caso de prueba por identificador. Entradas: id: identificador. Salida: devuelve el documento cuando existe. */
    /** Descripción: Obtiene la información correspondiente a «find» dentro de TransformationTestCaseRepository. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ Optional<StoredCanonicalDocument<TransformationTestCase>> find(String id);
    /** Descripción: Enumera identificadores de casos persistidos. Entradas: no requiere parámetros. Salida: devuelve los identificadores disponibles. */
    /** Descripción: Obtiene la información correspondiente a «listIds» dentro de TransformationTestCaseRepository. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<String> listIds();
    /** Descripción: Elimina un caso de prueba aplicando control optimista. Entradas: id: identificador; etag: versión esperada. Salida: no devuelve valor. */
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: id: identificador que delimita el recurso objetivo; etag: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void delete(String id, String etag);
}
