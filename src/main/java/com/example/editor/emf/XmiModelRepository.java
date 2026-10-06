package com.example.editor.emf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.springframework.stereotype.Repository;

/**
 * Guarda y carga modelos dinámicos utilizando el formato XMI.
 *
 * El repositorio utiliza el ResourceSet del metamodelo cargado para que los
 * namespaces y tipos Ecore puedan resolverse correctamente.
 */
@Repository
public class XmiModelRepository {

    /**
     * Descripción:
     * Guarda un EObject raíz dentro de un archivo XMI.
     *
     * Entrada:
     * @param loadedMetamodel contiene el ResourceSet y los paquetes registrados.
     * @param rootObject es el objeto raíz del modelo.
     * @param destination es la ruta del archivo XMI de salida.
     *
     * Salida:
     * No devuelve ningún valor.
     *
     * @throws ModelPersistenceException cuando el modelo no puede guardarse.
     */
    /** Descripción: Actualiza el estado gestionado por XmiModelRepository mediante la operación «save». Entradas: loadedMetamodel: dato de entrada necesario para la operación; rootObject: dato de entrada necesario para la operación; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void save(LoadedMetamodel loadedMetamodel, EObject rootObject, Path destination) {
        validateCommonParameters(loadedMetamodel, rootObject, destination);
        validateXmiExtension(destination);

        Path absolutePath = destination.toAbsolutePath().normalize();
        Path parentDirectory = absolutePath.getParent();

        try {
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }

            ResourceSet resourceSet = loadedMetamodel.getResourceSet();
            URI resourceUri = URI.createFileURI(absolutePath.toString());
            Resource resource = resourceSet.createResource(resourceUri);

            if (resource == null) {
                throw new ModelPersistenceException("No resource factory is available for XMI file: " + absolutePath);
            }

            if (rootObject.eResource() != null) {
                rootObject.eResource().getContents().remove(rootObject);
            }

            resource.getContents().add(rootObject);

            Map<Object, Object> options = new HashMap<>();
            options.put(XMLResource.OPTION_ENCODING, "UTF-8");
            options.put(XMLResource.OPTION_FORMATTED, Boolean.TRUE);
            resource.save(options);
        } catch (IOException exception) {
            throw new ModelPersistenceException("Could not save XMI model: " + absolutePath, exception);
        }
    }

    /**
     * Descripción:
     * Carga el primer EObject raíz contenido en un archivo XMI.
     *
     * Entrada:
     * @param loadedMetamodel contiene el ResourceSet y los paquetes registrados.
     * @param source es la ruta del archivo XMI.
     *
     * Salida:
     * @return el EObject raíz cargado.
     *
     * @throws ModelPersistenceException cuando el archivo no puede cargarse.
     */
    /** Descripción: Obtiene la información correspondiente a «load» dentro de XmiModelRepository. Entradas: loadedMetamodel: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EObject load(LoadedMetamodel loadedMetamodel, Path source) {
        if (loadedMetamodel == null) {
            throw new ModelPersistenceException("Loaded metamodel must not be null");
        }

        if (source == null) {
            throw new ModelPersistenceException("XMI source path must not be null");
        }

        validateXmiExtension(source);

        Path absolutePath = source.toAbsolutePath().normalize();

        if (!Files.isRegularFile(absolutePath)) {
            throw new ModelPersistenceException("XMI model file does not exist: " + absolutePath);
        }

        ResourceSet resourceSet = loadedMetamodel.getResourceSet();
        URI resourceUri = URI.createFileURI(absolutePath.toString());
        Resource resource = resourceSet.createResource(resourceUri);

        if (resource == null) {
            throw new ModelPersistenceException("No resource factory is available for XMI file: " + absolutePath);
        }

        try {
            resource.load(Collections.emptyMap());
        } catch (IOException exception) {
            throw new ModelPersistenceException("Could not load XMI model: " + absolutePath, exception);
        }

        if (!resource.getErrors().isEmpty()) {
            throw new ModelPersistenceException("XMI model contains loading errors: " + absolutePath);
        }

        if (resource.getContents().isEmpty()) {
            throw new ModelPersistenceException("XMI model does not contain a root EObject: " + absolutePath);
        }

        // Esta operación intenta resolver todas las referencias externas y proxies.
        EcoreUtil.resolveAll(resourceSet);

        return resource.getContents().get(0);
    }

    /**
     * Descripción:
     * Comprueba los parámetros comunes utilizados durante el guardado.
     *
     * Entrada:
     * @param loadedMetamodel contiene el entorno EMF.
     * @param rootObject es el objeto raíz.
     * @param destination es la ruta de salida.
     *
     * Salida:
     * No devuelve ningún valor.
     *
     * @throws ModelPersistenceException cuando algún parámetro no es válido.
     */
    /** Descripción: Valida las condiciones necesarias para «validateCommonParameters» y rechaza entradas no válidas. Entradas: loadedMetamodel: dato de entrada necesario para la operación; rootObject: dato de entrada necesario para la operación; destination: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateCommonParameters(LoadedMetamodel loadedMetamodel, EObject rootObject, Path destination) {
        if (loadedMetamodel == null) {
            throw new ModelPersistenceException("Loaded metamodel must not be null");
        }

        if (rootObject == null) {
            throw new ModelPersistenceException("Root EObject must not be null");
        }

        if (destination == null) {
            throw new ModelPersistenceException("XMI destination path must not be null");
        }
    }

    /**
     * Descripción:
     * Comprueba que la ruta utiliza la extensión xmi.
     *
     * Entrada:
     * @param path es la ruta que se debe comprobar.
     *
     * Salida:
     * No devuelve ningún valor.
     *
     * @throws ModelPersistenceException cuando la extensión no es xmi.
     */
    /** Descripción: Valida las condiciones necesarias para «validateXmiExtension» y rechaza entradas no válidas. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateXmiExtension(Path path) {
        String fileName = path.getFileName().toString();

        if (!fileName.toLowerCase().endsWith(".xmi")) {
            throw new ModelPersistenceException("Model file must use the .xmi extension: " + path.toAbsolutePath());
        }
    }
}