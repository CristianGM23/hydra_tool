package com.example.editor.metamodel.design;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.EcoreFactory;

/**
 * Conserva identificadores estables del diseñador dentro de anotaciones Ecore.
 */
public final class EcoreDesignIdSupport {

    private static final String SOURCE = "https://example.com/editor/design";
    private static final String ID_KEY = "id";

    /** Descripción: Inicializa una instancia de EcoreDesignIdSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private EcoreDesignIdSupport() {
    }

    /**
     * Descripción:
     * Guarda un identificador estable en un elemento Ecore.
     *
     * Entrada:
     * @param element contiene el elemento Ecore.
     * @param id contiene el identificador.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Actualiza el estado gestionado por EcoreDesignIdSupport mediante la operación «writeId». Entradas: element: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public static void writeId(EModelElement element, String id) {
        EAnnotation annotation = element.getEAnnotation(SOURCE);

        if (annotation == null) {
            annotation = EcoreFactory.eINSTANCE.createEAnnotation();
            annotation.setSource(SOURCE);
            element.getEAnnotations().add(annotation);
        }

        annotation.getDetails().put(ID_KEY, id);
    }

    /**
     * Descripción:
     * Lee un identificador estable o devuelve el valor alternativo.
     *
     * Entrada:
     * @param element contiene el elemento Ecore.
     * @param fallback contiene el identificador alternativo.
     *
     * Salida:
     * @return el identificador disponible.
     */
    /** Descripción: Obtiene la información correspondiente a «readId» dentro de EcoreDesignIdSupport. Entradas: element: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String readId(EModelElement element, String fallback) {
        EAnnotation annotation = element.getEAnnotation(SOURCE);

        if (annotation == null) {
            return fallback;
        }

        String id = annotation.getDetails().get(ID_KEY);

        if (id == null || id.isBlank()) {
            return fallback;
        }

        return id;
    }
}
