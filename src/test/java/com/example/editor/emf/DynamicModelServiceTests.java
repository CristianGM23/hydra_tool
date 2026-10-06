package com.example.editor.emf;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba la creación, modificación, conexión y persistencia de modelos EMF.
 */
class DynamicModelServiceTests {

    private static final String SAMPLE_NS_URI = "https://example.com/sample";

    private final EmfResourceSetFactory resourceSetFactory = new EmfResourceSetFactory();
    private final MetamodelLoader metamodelLoader = new MetamodelLoader(resourceSetFactory);
    private final DynamicModelService modelService = new DynamicModelService();
    private final XmiModelRepository modelRepository = new XmiModelRepository();

    @TempDir
    Path temporaryDirectory;

    /**
     * Descripción:
     * Comprueba que se puede crear una instancia dinámica de Node.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando el EObject pertenece a Node.
     */
    @Test
    void createsConcreteEObject() throws URISyntaxException {
        LoadedMetamodel metamodel = loadMetamodel();
        EObject node = modelService.createObject(metamodel, SAMPLE_NS_URI, "Node");

        assertThat(node).isNotNull();
        assertThat(node.eClass().getName()).isEqualTo("Node");
    }

    /**
     * Descripción:
     * Comprueba que una clase desconocida es rechazada.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando se lanza ModelOperationException.
     */
    @Test
    void rejectsUnknownEClass() throws URISyntaxException {
        LoadedMetamodel metamodel = loadMetamodel();

        assertThatThrownBy(() -> modelService.createObject(metamodel, SAMPLE_NS_URI, "Unknown"))
            .isInstanceOf(ModelOperationException.class)
            .hasMessageContaining("EClass was not found");
    }

    /**
     * Descripción:
     * Comprueba que una EClass abstracta no puede instanciarse.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando se rechaza NamedElement.
     */
    @Test
    void rejectsAbstractEClass() throws URISyntaxException {
        LoadedMetamodel metamodel = loadMetamodel();

        assertThatThrownBy(() -> modelService.createObject(metamodel, SAMPLE_NS_URI, "NamedElement"))
            .isInstanceOf(ModelOperationException.class)
            .hasMessageContaining("Abstract EClass cannot be instantiated");
    }

    /**
     * Descripción:
     * Comprueba que se puede asignar un atributo de texto.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando name contiene el valor esperado.
     */
    @Test
    void setsStringAttribute() throws URISyntaxException {
        LoadedMetamodel metamodel = loadMetamodel();
        EObject node = modelService.createObject(metamodel, SAMPLE_NS_URI, "Node");

        modelService.setAttribute(node, "name", "Node A");

        EAttribute nameAttribute = (EAttribute) node.eClass().getEStructuralFeature("name");
        assertThat(node.eGet(nameAttribute)).isEqualTo("Node A");
    }

    /**
     * Descripción:
     * Comprueba que un valor textual se convierte correctamente a boolean.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando active contiene true.
     */
    @Test
    void convertsAndSetsBooleanAttribute() throws URISyntaxException {
        LoadedMetamodel metamodel = loadMetamodel();
        EObject node = modelService.createObject(metamodel, SAMPLE_NS_URI, "Node");

        modelService.setAttribute(node, "active", "true");

        EAttribute activeAttribute = (EAttribute) node.eClass().getEStructuralFeature("active");
        assertThat(node.eGet(activeAttribute)).isEqualTo(Boolean.TRUE);
    }

    /**
     * Descripción:
     * Comprueba que un EObject puede añadirse a una referencia containment.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando Node queda contenido en Model.
     */
    @Test
    void addsEObjectToContainmentReference() throws URISyntaxException {
        LoadedMetamodel metamodel = loadMetamodel();
        EObject model = modelService.createObject(metamodel, SAMPLE_NS_URI, "Model");
        EObject node = modelService.createObject(metamodel, SAMPLE_NS_URI, "Node");

        modelService.addContained(model, "elements", node);

        assertThat(node.eContainer()).isSameAs(model);
        assertThat(node.eContainingFeature().getName()).isEqualTo("elements");
    }

    /**
     * Descripción:
     * Comprueba que una referencia normal no puede utilizarse como containment.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando target es rechazado.
     */
    @Test
    void rejectsNonContainmentReferenceForContainmentOperation() throws URISyntaxException {
        LoadedMetamodel metamodel = loadMetamodel();
        EObject source = modelService.createObject(metamodel, SAMPLE_NS_URI, "Node");
        EObject target = modelService.createObject(metamodel, SAMPLE_NS_URI, "Node");

        assertThatThrownBy(() -> modelService.addContained(source, "target", target))
            .isInstanceOf(ModelOperationException.class)
            .hasMessageContaining("EReference is not a containment reference");
    }

    /**
     * Descripción:
     * Comprueba que una referencia normal conecta dos EObjects sin cambiar su containment.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando source.target apunta a target.
     */
    @Test
    void setsNonContainmentReference() throws URISyntaxException {
        LoadedMetamodel metamodel = loadMetamodel();
        EObject source = modelService.createObject(metamodel, SAMPLE_NS_URI, "Node");
        EObject target = modelService.createObject(metamodel, SAMPLE_NS_URI, "Node");

        modelService.setReference(source, "target", target);

        EReference targetReference = (EReference) source.eClass().getEStructuralFeature("target");
        assertThat(source.eGet(targetReference)).isSameAs(target);
        assertThat(target.eContainer()).isNull();
    }

    /**
     * Descripción:
     * Comprueba que una referencia rechaza un tipo de destino incompatible.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando Model no puede asignarse a Node.target.
     */
    @Test
    void rejectsIncompatibleReferenceTarget() throws URISyntaxException {
        LoadedMetamodel metamodel = loadMetamodel();
        EObject node = modelService.createObject(metamodel, SAMPLE_NS_URI, "Node");
        EObject model = modelService.createObject(metamodel, SAMPLE_NS_URI, "Model");

        assertThatThrownBy(() -> modelService.setReference(node, "target", model))
            .isInstanceOf(ModelOperationException.class)
            .hasMessageContaining("EObject type is not compatible with EReference");
    }

    /**
     * Descripción:
     * Comprueba el guardado y la carga completa de un modelo XMI.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando atributos, containment y referencias se conservan.
     */
    @Test
    void preservesModelDuringXmiRoundTrip() throws URISyntaxException {
        LoadedMetamodel firstMetamodel = loadMetamodel();
        EObject model = modelService.createObject(firstMetamodel, SAMPLE_NS_URI, "Model");
        EObject firstNode = modelService.createObject(firstMetamodel, SAMPLE_NS_URI, "Node");
        EObject secondNode = modelService.createObject(firstMetamodel, SAMPLE_NS_URI, "Node");

        modelService.setAttribute(firstNode, "name", "Node A");
        modelService.setAttribute(firstNode, "active", "true");
        modelService.setAttribute(secondNode, "name", "Node B");
        modelService.addContained(model, "elements", firstNode);
        modelService.addContained(model, "elements", secondNode);
        modelService.setReference(firstNode, "target", secondNode);

        Path xmiFile = temporaryDirectory.resolve("model.xmi");
        modelRepository.save(firstMetamodel, model, xmiFile);

        LoadedMetamodel secondMetamodel = loadMetamodel();
        EObject loadedModel = modelRepository.load(secondMetamodel, xmiFile);
        EReference elementsReference = (EReference) loadedModel.eClass().getEStructuralFeature("elements");

        @SuppressWarnings("unchecked")
        EList<EObject> loadedElements = (EList<EObject>) loadedModel.eGet(elementsReference);

        EObject loadedFirstNode = loadedElements.get(0);
        EObject loadedSecondNode = loadedElements.get(1);
        EAttribute nameAttribute = (EAttribute) loadedFirstNode.eClass().getEStructuralFeature("name");
        EAttribute activeAttribute = (EAttribute) loadedFirstNode.eClass().getEStructuralFeature("active");
        EReference targetReference = (EReference) loadedFirstNode.eClass().getEStructuralFeature("target");

        assertThat(loadedModel.eClass().getName()).isEqualTo("Model");
        assertThat(loadedElements).hasSize(2);
        assertThat(loadedFirstNode.eGet(nameAttribute)).isEqualTo("Node A");
        assertThat(loadedFirstNode.eGet(activeAttribute)).isEqualTo(Boolean.TRUE);
        assertThat(loadedFirstNode.eGet(targetReference)).isSameAs(loadedSecondNode);
        assertThat(loadedSecondNode.eIsProxy()).isFalse();
    }

    /**
     * Descripción:
     * Carga el metamodelo utilizado en cada test.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return el metamodelo sample.ecore cargado.
     */
    private LoadedMetamodel loadMetamodel() throws URISyntaxException {
        java.net.URL resourceUrl = Objects.requireNonNull(getClass().getResource("/metamodels/sample.ecore"), "Test resource was not found");
        return metamodelLoader.load(Path.of(resourceUrl.toURI()));
    }
}