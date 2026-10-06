package com.example.editor.emf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.springframework.stereotype.Service;

/**
 * Carga archivos Ecore utilizando el runtime dinámico de EMF.
 *
 * La clase comprueba el archivo, obtiene sus EPackage y registra cada paquete
 * mediante su nsURI para que los modelos XMI puedan resolver sus tipos.
 */
@Service
public class MetamodelLoader {

    private final EmfResourceSetFactory resourceSetFactory;

    /**
     * Descripción:
     * Crea el cargador utilizando la fábrica de ResourceSet.
     *
     * Entrada:
     * @param resourceSetFactory crea entornos EMF independientes.
     *
     * Salida:
     * Crea una instancia de MetamodelLoader.
     */
    /** Descripción: Inicializa una instancia de MetamodelLoader con sus dependencias y datos de entrada. Entradas: resourceSetFactory: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public MetamodelLoader(EmfResourceSetFactory resourceSetFactory) {
        this.resourceSetFactory = resourceSetFactory;
    }

    /**
     * Descripción:
     * Carga un archivo Ecore, comprueba sus paquetes y registra sus nsURI.
     *
     * Entrada:
     * @param metamodelPath es la ruta del archivo Ecore.
     *
     * Salida:
     * @return un resultado con el ResourceSet, el recurso y sus paquetes raíz.
     *
     * @throws MetamodelLoadingException cuando el archivo no puede cargarse.
     */
    /** Descripción: Obtiene la información correspondiente a «load» dentro de MetamodelLoader. Entradas: metamodelPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public LoadedMetamodel load(Path metamodelPath) {
        validatePath(metamodelPath);

        ResourceSet resourceSet = resourceSetFactory.createResourceSet();
        URI resourceUri = URI.createFileURI(metamodelPath.toAbsolutePath().normalize().toString());
        Resource resource = resourceSet.createResource(resourceUri);

        if (resource == null) {
            throw new MetamodelLoadingException("No resource factory is available for the metamodel file: " + metamodelPath.toAbsolutePath());
        }

        try {
            resource.load(Collections.emptyMap());
        } catch (IOException exception) {
            throw new MetamodelLoadingException("Could not load metamodel file: " + metamodelPath.toAbsolutePath(), exception);
        }

        if (!resource.getErrors().isEmpty()) {
            throw new MetamodelLoadingException("Metamodel resource contains loading errors: " + metamodelPath.toAbsolutePath());
        }

        List<EPackage> rootPackages = findRootPackages(resource);

        if (rootPackages.isEmpty()) {
            throw new MetamodelLoadingException("Metamodel file does not contain any root EPackage: " + metamodelPath.toAbsolutePath());
        }

        Set<String> registeredNsUris = new HashSet<>();

        for (EPackage rootPackage : rootPackages) {
            registerPackageRecursively(resourceSet, rootPackage, registeredNsUris);
        }

        return new LoadedMetamodel(resourceSet, resource, rootPackages);
    }

    /**
     * Descripción:
     * Comprueba que la ruta existe, es un archivo y tiene extensión ecore.
     *
     * Entrada:
     * @param metamodelPath es la ruta que se debe comprobar.
     *
     * Salida:
     * No devuelve ningún valor.
     *
     * @throws MetamodelLoadingException cuando la ruta no es válida.
     */
    /** Descripción: Valida las condiciones necesarias para «validatePath» y rechaza entradas no válidas. Entradas: metamodelPath: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validatePath(Path metamodelPath) {
        if (metamodelPath == null) {
            throw new MetamodelLoadingException("Metamodel path must not be null");
        }

        if (!Files.exists(metamodelPath)) {
            throw new MetamodelLoadingException("Metamodel file does not exist: " + metamodelPath.toAbsolutePath());
        }

        if (!Files.isRegularFile(metamodelPath)) {
            throw new MetamodelLoadingException("Metamodel path is not a regular file: " + metamodelPath.toAbsolutePath());
        }

        String fileName = metamodelPath.getFileName().toString();

        if (!fileName.toLowerCase().endsWith(".ecore")) {
            throw new MetamodelLoadingException("Metamodel file must use the .ecore extension: " + metamodelPath.toAbsolutePath());
        }
    }

    /**
     * Descripción:
     * Busca todos los EPackage situados en la raíz del recurso.
     *
     * Entrada:
     * @param resource contiene los objetos cargados desde el archivo Ecore.
     *
     * Salida:
     * @return una lista con los paquetes raíz encontrados.
     */
    /** Descripción: Obtiene la información correspondiente a «findRootPackages» dentro de MetamodelLoader. Entradas: resource: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<EPackage> findRootPackages(Resource resource) {
        List<EPackage> rootPackages = new ArrayList<>();

        for (EObject content : resource.getContents()) {
            if (content instanceof EPackage ePackage) {
                rootPackages.add(ePackage);
            }
        }

        return rootPackages;
    }

    /**
     * Descripción:
     * Valida y registra un paquete y todos sus subpaquetes.
     *
     * Entrada:
     * @param resourceSet contiene el registro de paquetes.
     * @param ePackage es el paquete que se debe registrar.
     * @param registeredNsUris contiene los nsURI registrados durante esta carga.
     *
     * Salida:
     * No devuelve ningún valor.
     *
     * @throws MetamodelLoadingException cuando el paquete no es válido.
     */
    /** Descripción: Ejecuta la operación «registerPackageRecursively» correspondiente a la responsabilidad de MetamodelLoader. Entradas: resourceSet: dato de entrada necesario para la operación; ePackage: dato de entrada necesario para la operación; registeredNsUris: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void registerPackageRecursively(ResourceSet resourceSet, EPackage ePackage, Set<String> registeredNsUris) {
        validatePackage(ePackage);

        String nsUri = ePackage.getNsURI();

        if (registeredNsUris.contains(nsUri)) {
            throw new MetamodelLoadingException("Duplicate EPackage nsURI found in metamodel: " + nsUri);
        }

        registeredNsUris.add(nsUri);
        resourceSet.getPackageRegistry().put(nsUri, ePackage);

        // Los subpaquetes también necesitan registro para resolver sus tipos desde XMI.
        for (EPackage subpackage : ePackage.getESubpackages()) {
            registerPackageRecursively(resourceSet, subpackage, registeredNsUris);
        }
    }

    /**
     * Descripción:
     * Comprueba que un paquete tenga nombre, nsURI y prefijo.
     *
     * Entrada:
     * @param ePackage es el paquete que se debe validar.
     *
     * Salida:
     * No devuelve ningún valor.
     *
     * @throws MetamodelLoadingException cuando falta información obligatoria.
     */
    /** Descripción: Valida las condiciones necesarias para «validatePackage» y rechaza entradas no válidas. Entradas: ePackage: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validatePackage(EPackage ePackage) {
        if (isBlank(ePackage.getName())) {
            throw new MetamodelLoadingException("EPackage name must not be empty");
        }

        if (isBlank(ePackage.getNsURI())) {
            throw new MetamodelLoadingException("EPackage nsURI must not be empty: " + ePackage.getName());
        }

        if (isBlank(ePackage.getNsPrefix())) {
            throw new MetamodelLoadingException("EPackage nsPrefix must not be empty: " + ePackage.getName());
        }
    }

    /**
     * Descripción:
     * Comprueba si un texto es nulo, está vacío o contiene solo espacios.
     *
     * Entrada:
     * @param value es el texto que se debe comprobar.
     *
     * Salida:
     * @return true cuando el texto no contiene información y false en caso contrario.
     */
    /** Descripción: Evalúa la condición «isBlank» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isBlank(String value) {
        if (value == null) {
            return true;
        } else {
            return value.isBlank();
        }
    }
}