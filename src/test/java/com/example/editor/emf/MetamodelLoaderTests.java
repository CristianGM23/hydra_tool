package com.example.editor.emf;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba la carga, interpretación y registro de metamodelos Ecore.
 */
class MetamodelLoaderTests {

    private final EmfResourceSetFactory resourceSetFactory = new EmfResourceSetFactory();
    private final MetamodelLoader metamodelLoader = new MetamodelLoader(resourceSetFactory);

    @TempDir
    Path temporaryDirectory;

    /**
     * Descripción:
     * Comprueba que un archivo Ecore válido se carga sin errores.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando el recurso queda cargado.
     *
     * @throws URISyntaxException cuando no puede resolverse el recurso de prueba.
     */
    @Test
    void loadsValidMetamodel() throws URISyntaxException {
        LoadedMetamodel loadedMetamodel = metamodelLoader.load(getResourcePath("/metamodels/sample.ecore"));

        assertThat(loadedMetamodel.getResource().isLoaded()).isTrue();
        assertThat(loadedMetamodel.getResource().getErrors()).isEmpty();
        assertThat(loadedMetamodel.getRootPackages()).hasSize(1);
    }

    /**
     * Descripción:
     * Comprueba que se descubren las clases y la herencia del metamodelo.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando las clases tienen la estructura esperada.
     *
     * @throws URISyntaxException cuando no puede resolverse el recurso de prueba.
     */
    @Test
    void discoversClassesAndInheritance() throws URISyntaxException {
        LoadedMetamodel loadedMetamodel = metamodelLoader.load(getResourcePath("/metamodels/sample.ecore"));
        EPackage rootPackage = loadedMetamodel.getRootPackages().get(0);
        EClass namedElement = (EClass) rootPackage.getEClassifier("NamedElement");
        EClass model = (EClass) rootPackage.getEClassifier("Model");
        EClass node = (EClass) rootPackage.getEClassifier("Node");

        assertThat(namedElement).isNotNull();
        assertThat(model).isNotNull();
        assertThat(node).isNotNull();
        assertThat(namedElement.isAbstract()).isTrue();
        assertThat(node.getEAllSuperTypes()).contains(namedElement);
    }

    /**
     * Descripción:
     * Comprueba que el paquete raíz queda registrado mediante su nsURI.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando el registro devuelve el mismo paquete.
     *
     * @throws URISyntaxException cuando no puede resolverse el recurso de prueba.
     */
    @Test
    void registersRootPackageByNsUri() throws URISyntaxException {
        LoadedMetamodel loadedMetamodel = metamodelLoader.load(getResourcePath("/metamodels/sample.ecore"));
        EPackage rootPackage = loadedMetamodel.getRootPackages().get(0);
        Object registeredPackage = loadedMetamodel.getResourceSet().getPackageRegistry().get(rootPackage.getNsURI());

        assertThat(registeredPackage).isSameAs(rootPackage);
    }

    /**
     * Descripción:
     * Comprueba que los subpaquetes también quedan registrados.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando el subpaquete se resuelve por nsURI.
     *
     * @throws URISyntaxException cuando no puede resolverse el recurso de prueba.
     */
    @Test
    void registersSubpackagesByNsUri() throws URISyntaxException {
        LoadedMetamodel loadedMetamodel = metamodelLoader.load(getResourcePath("/metamodels/sample.ecore"));
        EPackage rootPackage = loadedMetamodel.getRootPackages().get(0);
        EPackage subpackage = rootPackage.getESubpackages().get(0);
        Object registeredPackage = loadedMetamodel.getResourceSet().getPackageRegistry().get(subpackage.getNsURI());

        assertThat(registeredPackage).isSameAs(subpackage);
    }

    /**
     * Descripción:
     * Comprueba que se rechaza una ruta que no existe.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando se lanza MetamodelLoadingException.
     */
    @Test
    void rejectsMissingMetamodelFile() {
        Path missingFile = temporaryDirectory.resolve("missing.ecore");

        assertThatThrownBy(() -> metamodelLoader.load(missingFile))
            .isInstanceOf(MetamodelLoadingException.class)
            .hasMessageContaining("Metamodel file does not exist");
    }

    /**
     * Descripción:
     * Comprueba que se rechaza un archivo Ecore con XML incorrecto.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando se lanza MetamodelLoadingException.
     *
     * @throws URISyntaxException cuando no puede resolverse el recurso de prueba.
     */
    @Test
    void rejectsMalformedMetamodel() throws URISyntaxException {
        Path malformedFile = getResourcePath("/metamodels/malformed.ecore");

        assertThatThrownBy(() -> metamodelLoader.load(malformedFile))
            .isInstanceOf(MetamodelLoadingException.class)
            .hasMessageContaining("Could not load metamodel file");
    }

    /**
     * Descripción:
     * Convierte un recurso del classpath en una ruta del sistema.
     *
     * Entrada:
     * @param resourceName es la ruta del recurso dentro de src/test/resources.
     *
     * Salida:
     * @return la ruta del archivo de prueba.
     *
     * @throws URISyntaxException cuando la URL no puede convertirse en URI.
     */
    private Path getResourcePath(String resourceName) throws URISyntaxException {
        java.net.URL resourceUrl = Objects.requireNonNull(getClass().getResource(resourceName), "Test resource was not found");
        return Path.of(resourceUrl.toURI());
    }
}