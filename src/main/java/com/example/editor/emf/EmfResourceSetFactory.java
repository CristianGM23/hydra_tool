package com.example.editor.emf;

import java.util.Map;

import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.springframework.stereotype.Component;

/**
 * Crea instancias de ResourceSet preparadas para trabajar con Ecore y XMI.
 *
 * Cada operación recibe un ResourceSet nuevo. Esto evita que distintos
 * usuarios o modelos compartan accidentalmente recursos modificables.
 */
@Component
public class EmfResourceSetFactory {

    /**
     * Descripción:
     * Crea un ResourceSet y registra las fábricas necesarias para Ecore y XMI.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return un ResourceSet nuevo preparado para cargar archivos Ecore y XMI.
     */
    /** Descripción: Crea o prepara el resultado de «createResourceSet» dentro de EmfResourceSetFactory. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public ResourceSet createResourceSet() {
        ResourceSet resourceSet = new ResourceSetImpl();
        Map<String, Object> factories = resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap();

        factories.put("ecore", new EcoreResourceFactoryImpl());
        factories.put("xmi", new XMIResourceFactoryImpl());

        return resourceSet;
    }
}