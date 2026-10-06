package com.example.editor.emf;

import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que la fábrica crea entornos EMF independientes y registra
 * las fábricas necesarias para Ecore y XMI.
 */
class EmfResourceSetFactoryTests {

    private final EmfResourceSetFactory factory = new EmfResourceSetFactory();

    /**
     * Descripción:
     * Comprueba que la fábrica devuelve un ResourceSet.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando el resultado no es nulo.
     */
    @Test
    void createsResourceSet() {
        ResourceSet resourceSet = factory.createResourceSet();
        assertThat(resourceSet).isNotNull();
    }

    /**
     * Descripción:
     * Comprueba que los archivos Ecore tienen una fábrica registrada.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando la fábrica es EcoreResourceFactoryImpl.
     */
    @Test
    void registersEcoreResourceFactory() {
        ResourceSet resourceSet = factory.createResourceSet();
        Object resourceFactory = resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().get("ecore");
        assertThat(resourceFactory).isInstanceOf(EcoreResourceFactoryImpl.class);
    }

    /**
     * Descripción:
     * Comprueba que los archivos XMI tienen una fábrica registrada.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando la fábrica es XMIResourceFactoryImpl.
     */
    @Test
    void registersXmiResourceFactory() {
        ResourceSet resourceSet = factory.createResourceSet();
        Object resourceFactory = resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().get("xmi");
        assertThat(resourceFactory).isInstanceOf(XMIResourceFactoryImpl.class);
    }

    /**
     * Descripción:
     * Comprueba que cada llamada devuelve un ResourceSet independiente.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando las instancias son diferentes.
     */
    @Test
    void createsIndependentResourceSets() {
        ResourceSet firstResourceSet = factory.createResourceSet();
        ResourceSet secondResourceSet = factory.createResourceSet();

        assertThat(firstResourceSet).isNotSameAs(secondResourceSet);
        assertThat(firstResourceSet.getResources()).isNotSameAs(secondResourceSet.getResources());
    }
}