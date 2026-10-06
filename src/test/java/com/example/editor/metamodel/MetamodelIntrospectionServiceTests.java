package com.example.editor.metamodel;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.example.editor.emf.EmfResourceSetFactory;
import com.example.editor.emf.LoadedMetamodel;
import com.example.editor.emf.MetamodelLoader;
import com.example.editor.metamodel.MetamodelDescriptor.AttributeDescriptor;
import com.example.editor.metamodel.MetamodelDescriptor.ClassDescriptor;
import com.example.editor.metamodel.MetamodelDescriptor.DataTypeDescriptor;
import com.example.editor.metamodel.MetamodelDescriptor.EnumDescriptor;
import com.example.editor.metamodel.MetamodelDescriptor.PackageDescriptor;
import com.example.editor.metamodel.MetamodelDescriptor.ReferenceDescriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba que un metamodelo Ecore se convierte correctamente en una
 * descripción neutral que podrá utilizar el futuro editor gráfico.
 */
class MetamodelIntrospectionServiceTests {

    private static final String NS_URI = "https://example.com/introspection";

    private final EmfResourceSetFactory resourceSetFactory = new EmfResourceSetFactory();
    private final MetamodelLoader metamodelLoader = new MetamodelLoader(resourceSetFactory);
    private final MetamodelIntrospectionService introspectionService = new MetamodelIntrospectionService();

    /**
     * Descripción:
     * Comprueba el URI de origen y el número de paquetes raíz.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando existe un paquete raíz.
     */
    @Test
    void describesSourceAndRootPackage() throws URISyntaxException {
        MetamodelDescriptor descriptor = describeMetamodel();

        assertThat(descriptor.sourceUri()).endsWith("introspection.ecore");
        assertThat(descriptor.packages()).hasSize(1);
        assertThat(descriptor.packages().get(0).name()).isEqualTo("introspection");
    }

    /**
     * Descripción:
     * Comprueba que los subpaquetes se describen recursivamente.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando se encuentra el paquete nested.
     */
    @Test
    void describesNestedPackages() throws URISyntaxException {
        PackageDescriptor rootPackage = getRootPackage();

        assertThat(rootPackage.subpackages()).hasSize(1);
        assertThat(rootPackage.subpackages().get(0).name()).isEqualTo("nested");
        assertThat(rootPackage.subpackages().get(0).nsUri()).isEqualTo(NS_URI + "/nested");
    }

    /**
     * Descripción:
     * Comprueba las propiedades generales de una EClass.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando NamedElement es abstracta.
     */
    @Test
    void describesClassMetadata() throws URISyntaxException {
        ClassDescriptor namedElement = findClass(getRootPackage(), "NamedElement");

        assertThat(namedElement.id()).isEqualTo(NS_URI + "#//NamedElement");
        assertThat(namedElement.abstractType()).isTrue();
        assertThat(namedElement.interfaceType()).isFalse();
        assertThat(namedElement.instantiable()).isFalse();
    }

    /**
     * Descripción:
     * Comprueba que se describen los supertipos directos.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando Node hereda de NamedElement.
     */
    @Test
    void describesDirectSuperTypes() throws URISyntaxException {
        ClassDescriptor node = findClass(getRootPackage(), "Node");

        assertThat(node.instantiable()).isTrue();
        assertThat(node.superTypeIds()).containsExactly(NS_URI + "#//NamedElement");
    }

    /**
     * Descripción:
     * Comprueba que los atributos heredados aparecen en la clase hija.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando id aparece como heredado.
     */
    @Test
    void describesInheritedAttributes() throws URISyntaxException {
        ClassDescriptor node = findClass(getRootPackage(), "Node");
        AttributeDescriptor idAttribute = findAttribute(node, "id");

        assertThat(idAttribute.inherited()).isTrue();
        assertThat(idAttribute.declaringClassId()).isEqualTo(NS_URI + "#//NamedElement");
        assertThat(idAttribute.idAttribute()).isTrue();
        assertThat(idAttribute.required()).isTrue();
    }

    /**
     * Descripción:
     * Comprueba el tipo, cardinalidad y valor predeterminado de un atributo.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando priority se describe correctamente.
     */
    @Test
    void describesScalarAttribute() throws URISyntaxException {
        ClassDescriptor node = findClass(getRootPackage(), "Node");
        AttributeDescriptor priority = findAttribute(node, "priority");

        assertThat(priority.typeName()).isEqualTo("EInt");
        assertThat(priority.lowerBound()).isZero();
        assertThat(priority.upperBound()).isEqualTo(1);
        assertThat(priority.many()).isFalse();
        assertThat(priority.defaultValue()).isEqualTo("1");
    }

    /**
     * Descripción:
     * Comprueba que los atributos multivaluados se identifican correctamente.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando tags tiene cardinalidad ilimitada.
     */
    @Test
    void describesManyValuedAttribute() throws URISyntaxException {
        ClassDescriptor node = findClass(getRootPackage(), "Node");
        AttributeDescriptor tags = findAttribute(node, "tags");

        assertThat(tags.many()).isTrue();
        assertThat(tags.upperBound()).isEqualTo(-1);
        assertThat(tags.typeName()).isEqualTo("EString");
    }

    /**
     * Descripción:
     * Comprueba una referencia containment y otra referencia normal.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando ambas referencias se diferencian.
     */
    @Test
    void describesContainmentAndNormalReferences() throws URISyntaxException {
        PackageDescriptor rootPackage = getRootPackage();
        ReferenceDescriptor elements = findReference(findClass(rootPackage, "Model"), "elements");
        ReferenceDescriptor target = findReference(findClass(rootPackage, "Node"), "target");

        assertThat(elements.containment()).isTrue();
        assertThat(elements.many()).isTrue();
        assertThat(elements.targetClassName()).isEqualTo("NamedElement");

        assertThat(target.containment()).isFalse();
        assertThat(target.many()).isFalse();
        assertThat(target.targetClassName()).isEqualTo("Node");
    }

    /**
     * Descripción:
     * Comprueba las enumeraciones y los tipos de datos personalizados.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando NodeState y Code están presentes.
     */
    @Test
    void describesEnumsAndCustomDataTypes() throws URISyntaxException {
        PackageDescriptor rootPackage = getRootPackage();
        EnumDescriptor nodeState = findEnum(rootPackage, "NodeState");
        DataTypeDescriptor code = findDataType(rootPackage, "Code");

        assertThat(nodeState.literals()).extracting(literal -> literal.name()).containsExactly("IDLE", "ACTIVE", "ERROR");
        assertThat(nodeState.literals()).extracting(literal -> literal.value()).containsExactly(0, 1, 2);
        assertThat(code.instanceClassName()).isEqualTo("java.lang.String");
        assertThat(code.serializable()).isTrue();
    }

    /**
     * Descripción:
     * Comprueba que no se acepta un metamodelo nulo.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando se lanza la excepción esperada.
     */
    @Test
    void rejectsNullLoadedMetamodel() {
        assertThatThrownBy(() -> introspectionService.describe(null))
            .isInstanceOf(MetamodelIntrospectionException.class)
            .hasMessageContaining("Loaded metamodel must not be null");
    }

    /**
     * Descripción:
     * Carga e interpreta el metamodelo de prueba.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return la descripción completa del metamodelo.
     */
    private MetamodelDescriptor describeMetamodel() throws URISyntaxException {
        java.net.URL resourceUrl = Objects.requireNonNull(getClass().getResource("/metamodels/introspection.ecore"), "Test resource was not found");
        LoadedMetamodel loadedMetamodel = metamodelLoader.load(Path.of(resourceUrl.toURI()));
        return introspectionService.describe(loadedMetamodel);
    }

    /**
     * Descripción:
     * Obtiene el paquete raíz del metamodelo de prueba.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * @return el primer paquete raíz.
     */
    private PackageDescriptor getRootPackage() throws URISyntaxException {
        return describeMetamodel().packages().get(0);
    }

    /**
     * Descripción:
     * Busca una clase por su nombre.
     *
     * Entrada:
     * @param packageDescriptor contiene las clases.
     * @param className es el nombre buscado.
     *
     * Salida:
     * @return la clase encontrada.
     */
    private ClassDescriptor findClass(PackageDescriptor packageDescriptor, String className) {
        return packageDescriptor.classes().stream()
            .filter(classDescriptor -> classDescriptor.name().equals(className))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Class descriptor was not found: " + className));
    }

    /**
     * Descripción:
     * Busca un atributo por su nombre.
     *
     * Entrada:
     * @param classDescriptor contiene los atributos.
     * @param attributeName es el nombre buscado.
     *
     * Salida:
     * @return el atributo encontrado.
     */
    private AttributeDescriptor findAttribute(ClassDescriptor classDescriptor, String attributeName) {
        return classDescriptor.attributes().stream()
            .filter(attribute -> attribute.name().equals(attributeName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Attribute descriptor was not found: " + attributeName));
    }

    /**
     * Descripción:
     * Busca una referencia por su nombre.
     *
     * Entrada:
     * @param classDescriptor contiene las referencias.
     * @param referenceName es el nombre buscado.
     *
     * Salida:
     * @return la referencia encontrada.
     */
    private ReferenceDescriptor findReference(ClassDescriptor classDescriptor, String referenceName) {
        return classDescriptor.references().stream()
            .filter(reference -> reference.name().equals(referenceName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Reference descriptor was not found: " + referenceName));
    }

    /**
     * Descripción:
     * Busca una enumeración por su nombre.
     *
     * Entrada:
     * @param packageDescriptor contiene las enumeraciones.
     * @param enumName es el nombre buscado.
     *
     * Salida:
     * @return la enumeración encontrada.
     */
    private EnumDescriptor findEnum(PackageDescriptor packageDescriptor, String enumName) {
        return packageDescriptor.enums().stream()
            .filter(enumDescriptor -> enumDescriptor.name().equals(enumName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Enum descriptor was not found: " + enumName));
    }

    /**
     * Descripción:
     * Busca un tipo de dato personalizado por su nombre.
     *
     * Entrada:
     * @param packageDescriptor contiene los tipos de datos.
     * @param dataTypeName es el nombre buscado.
     *
     * Salida:
     * @return el tipo de dato encontrado.
     */
    private DataTypeDescriptor findDataType(PackageDescriptor packageDescriptor, String dataTypeName) {
        return packageDescriptor.dataTypes().stream()
            .filter(dataType -> dataType.name().equals(dataTypeName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Data type descriptor was not found: " + dataTypeName));
    }
}