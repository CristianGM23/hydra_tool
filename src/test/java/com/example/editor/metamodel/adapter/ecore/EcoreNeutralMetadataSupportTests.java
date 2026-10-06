package com.example.editor.metamodel.adapter.ecore;

import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EcoreFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba la lectura segura de los mapas EMap de EMF.
 */
class EcoreNeutralMetadataSupportTests {

    /**
     * Descripción:
     * Guarda y recupera anotaciones sin depender de la inferencia genérica de EMap.
     */
    @Test
    void readsEMapDetailsAsStandardMap() {
        EClass eClass = EcoreFactory.eINSTANCE.createEClass();
        EcoreNeutralMetadataSupport.writeAnnotations(eClass, Map.of("group", "people", "icon", "person"));

        Map<String, String> result = EcoreNeutralMetadataSupport.readAnnotations(eClass);

        assertThat(result).containsEntry("group", "people").containsEntry("icon", "person");
        assertThatThrownByUnsupportedOperation(result);
    }

    private void assertThatThrownByUnsupportedOperation(Map<String, String> result) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> result.put("new", "value")).isInstanceOf(UnsupportedOperationException.class);
    }
}
