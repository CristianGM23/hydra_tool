package com.example.editor.emf;

import java.util.List;
import java.util.Objects;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * Agrupa el resultado de cargar un metamodelo.
 *
 * Conserva el ResourceSet, el recurso Ecore y todos los paquetes raíz
 * encontrados dentro del archivo.
 */
public final class LoadedMetamodel {

    private final ResourceSet resourceSet;
    private final Resource resource;
    private final List<EPackage> rootPackages;

    /**
     * Descripción:
     * Crea un resultado inmutable con la información del metamodelo cargado.
     *
     * Entrada:
     * @param resourceSet contiene los recursos y registros de EMF.
     * @param resource representa el archivo Ecore cargado.
     * @param rootPackages contiene los paquetes raíz encontrados.
     *
     * Salida:
     * Crea una instancia de LoadedMetamodel.
     */
    /** Descripción: Inicializa una instancia de LoadedMetamodel con sus dependencias y datos de entrada. Entradas: resourceSet: dato de entrada necesario para la operación; resource: dato de entrada necesario para la operación; rootPackages: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public LoadedMetamodel(ResourceSet resourceSet, Resource resource, List<EPackage> rootPackages) {
        this.resourceSet = Objects.requireNonNull(resourceSet, "ResourceSet must not be null");
        this.resource = Objects.requireNonNull(resource, "Resource must not be null");
        this.rootPackages = List.copyOf(rootPackages);
    }

    /**
     * Descripción:
     * Devuelve el ResourceSet utilizado durante la carga.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return el ResourceSet del metamodelo.
     */
    /** Descripción: Obtiene la información correspondiente a «getResourceSet» dentro de LoadedMetamodel. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public ResourceSet getResourceSet() {
        return resourceSet;
    }

    /**
     * Descripción:
     * Devuelve el recurso que representa el archivo Ecore.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return el recurso Ecore cargado.
     */
    /** Descripción: Obtiene la información correspondiente a «getResource» dentro de LoadedMetamodel. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Resource getResource() {
        return resource;
    }

    /**
     * Descripción:
     * Devuelve los paquetes raíz encontrados en el archivo.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return una lista inmutable de paquetes Ecore.
     */
    /** Descripción: Obtiene la información correspondiente a «getRootPackages» dentro de LoadedMetamodel. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<EPackage> getRootPackages() {
        return rootPackages;
    }
}