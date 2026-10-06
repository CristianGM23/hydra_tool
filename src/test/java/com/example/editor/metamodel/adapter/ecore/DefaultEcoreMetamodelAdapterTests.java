package com.example.editor.metamodel.adapter.ecore;

import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.target.EcoreDataTypeConfiguration;
import com.example.editor.metamodel.target.EcoreProjectionConfiguration;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba la conversión bidireccional neutral y Ecore.
 */
class DefaultEcoreMetamodelAdapterTests {

    private EcoreAdapterProperties properties;
    private DefaultEcoreMetamodelAdapter adapter;

    /**
     * Descripción:
     * Prepara un adaptador aislado.
     */
    @BeforeEach
    void setUp() {
        properties = new EcoreAdapterProperties();
        EcorePrimitiveTypeRegistry registry = new EcorePrimitiveTypeRegistry(properties);
        adapter = new DefaultEcoreMetamodelAdapter(properties, registry, new NeutralMetamodelValidator());
    }

    /**
     * Descripción:
     * Comprueba paquete, clases y anotaciones.
     */
    @Test
    void createsPackageAndClasses() {
        EPackage ePackage = adapter.toEPackage(createModel()).value();
        EClass person = (EClass) ePackage.getEClassifier("Person");

        assertThat(ePackage.getName()).isEqualTo("company");
        assertThat(person).isNotNull();
        assertThat(person.isAbstract()).isFalse();
        assertThat(EcoreNeutralMetadataSupport.readAnnotations(person)).containsEntry("ui.group", "people");
    }

    /**
     * Descripción:
     * Conserva enumeraciones, valores e identificadores.
     */
    @Test
    void preservesEnumLiteralValues() {
        EPackage ePackage = adapter.toEPackage(createModel()).value();
        EEnum status = (EEnum) ePackage.getEClassifier("Status");

        assertThat(status.getELiterals()).extracting(item -> item.getValue()).containsExactly(10, 20);
        assertThat(status.getELiterals()).extracting(item -> item.getName()).containsExactly("ACTIVE", "INACTIVE");
    }

    /**
     * Descripción:
     * Conserva tipos de datos personalizados.
     */
    @Test
    void preservesCustomDataTypes() {
        MetaModel model = createModel();
        EcoreProjectionConfiguration defaults = EcoreProjectionConfiguration.defaultsFor(model);
        EcoreProjectionConfiguration configured = new EcoreProjectionConfiguration(
            defaults.enabled(),
            defaults.generationMode(),
            defaults.requiredForRuntime(),
            defaults.fileName(),
            defaults.namespaceUri(),
            defaults.namespacePrefix(),
            defaults.attributeOptions(),
            defaults.relationOptions(),
            Map.of("datatype-money", new EcoreDataTypeConfiguration("com.example.Money", true))
        );
        EPackage ePackage = adapter.toEPackage(model, configured).value();
        EDataType money = (EDataType) ePackage.getEClassifier("Money");

        assertThat(money.getInstanceClassName()).isEqualTo("com.example.Money");
        assertThat(money.isSerializable()).isTrue();
    }

    /**
     * Descripción:
     * Convierte atributos y multiplicidades.
     */
    @Test
    void mapsAttributesAndMultiplicities() {
        EPackage ePackage = adapter.toEPackage(createModel()).value();
        EClass person = (EClass) ePackage.getEClassifier("Person");
        EAttribute name = (EAttribute) person.getEStructuralFeature("name");

        assertThat(name.getEAttributeType().getName()).isEqualTo("EString");
        assertThat(name.getLowerBound()).isEqualTo(1);
        assertThat(name.getUpperBound()).isEqualTo(1);
    }

    /**
     * Descripción:
     * Convierte referencias, containment y opuestos.
     */
    @Test
    void mapsReferencesContainmentAndOpposites() {
        EPackage ePackage = adapter.toEPackage(createModel()).value();
        EClass company = (EClass) ePackage.getEClassifier("Company");
        EClass person = (EClass) ePackage.getEClassifier("Person");
        EReference employees = (EReference) company.getEStructuralFeature("employees");
        EReference employer = (EReference) person.getEStructuralFeature("employer");

        assertThat(employees.isContainment()).isTrue();
        assertThat(employees.getEOpposite()).isSameAs(employer);
        assertThat(employer.getEOpposite()).isSameAs(employees);
    }

    /**
     * Descripción:
     * Conserva agregación mediante metadatos.
     */
    @Test
    void preservesAggregationThroughAnnotations() {
        MetaModel original = createModel();
        MetaModel restored = adapter.fromEPackage(adapter.toEPackage(original).value(), "company.ecore").value();

        assertThat(restored.relations()).anyMatch(item -> item.id().equals("aggregation-tags") && item.kind() == MetaRelationKind.AGGREGATION);
    }

    /**
     * Descripción:
     * Conserva dependencias que Ecore no representa de forma nativa.
     */
    @Test
    void preservesDependencyThroughPackageAnnotations() {
        MetaModel original = createModel();
        MetaModel restored = adapter.fromEPackage(adapter.toEPackage(original).value(), "company.ecore").value();

        assertThat(restored.relations()).anyMatch(item -> item.id().equals("dependency-audit") && item.kind() == MetaRelationKind.DEPENDENCY);
    }

    /**
     * Descripción:
     * Conserva herencia e implementación.
     */
    @Test
    void preservesInheritanceAndImplementation() {
        MetaModel restored = adapter.fromEPackage(adapter.toEPackage(createModel()).value(), "company.ecore").value();

        assertThat(restored.relations()).anyMatch(item -> item.id().equals("inheritance-employee") && item.kind() == MetaRelationKind.INHERITANCE);
        assertThat(restored.relations()).anyMatch(item -> item.id().equals("implementation-person") && item.kind() == MetaRelationKind.IMPLEMENTATION);
    }

    /**
     * Descripción:
     * Conserva tipos externos sin crear dependencia estructural.
     */
    @Test
    void preservesExternalAttributeTypes() {
        MetaModel original = createModel();
        MetaModel restored = adapter.fromEPackage(adapter.toEPackage(original).value(), "company.ecore").value();
        MetaAttribute external = restored.classes().stream().filter(item -> item.id().equals("class-person")).findFirst().orElseThrow().attributes().stream().filter(item -> item.id().equals("attribute-external")).findFirst().orElseThrow();

        assertThat(external.type()).isEqualTo(MetaTypeReference.external("com.example.ExternalCode"));
    }

    /**
     * Descripción:
     * Rechaza tipos externos cuando la política es estricta.
     */
    @Test
    void rejectsExternalTypesWithStrictPolicy() {
        properties.setExternalTypePolicy(EcoreExternalTypePolicy.FAIL);
        adapter = new DefaultEcoreMetamodelAdapter(properties, new EcorePrimitiveTypeRegistry(properties), new NeutralMetamodelValidator());

        assertThatThrownBy(() -> adapter.toEPackage(createModel())).isInstanceOf(MetamodelDesignException.class).hasMessageContaining("External neutral attribute type");
    }

    /**
     * Descripción:
     * Genera bytes Ecore válidos.
     */
    @Test
    void serializesEcoreBytes() {
        byte[] content = adapter.toBytes(createModel()).value();
        String xml = new String(content, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(content).isNotEmpty();
        assertThat(xml).contains("ecore:EPackage", "name=\"company\"", "Person");
    }

    private MetaModel createModel() {
        MetaEnumLiteral active = new MetaEnumLiteral("literal-active", "ACTIVE", 10, Map.of());
        MetaEnumLiteral inactive = new MetaEnumLiteral("literal-inactive", "INACTIVE", 20, Map.of());
        MetaEnum status = new MetaEnum("enum-status", "Status", List.of(active, inactive), Map.of());
        MetaDataType money = new MetaDataType("datatype-money", "Money", Map.of());
        MetaAttribute name = new MetaAttribute("attribute-name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaAttribute state = new MetaAttribute("attribute-state", "state", MetaTypeReference.enumType("enum-status"), Multiplicity.optional(), true, true, "ACTIVE", Map.of());
        MetaAttribute balance = new MetaAttribute("attribute-balance", "balance", MetaTypeReference.dataType("datatype-money"), Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute external = new MetaAttribute("attribute-external", "externalCode", MetaTypeReference.external("com.example.ExternalCode"), Multiplicity.optional(), true, true, "", Map.of());
        MetaClass identifiable = new MetaClass("interface-identifiable", "Identifiable", MetaClassKind.INTERFACE, List.of(), List.of(), Map.of());
        MetaClass person = new MetaClass("class-person", "Person", MetaClassKind.CLASS, List.of(name, state, balance, external), List.of(), Map.of("ui.group", "people"));
        MetaClass employee = new MetaClass("class-employee", "Employee", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass company = new MetaClass("class-company", "Company", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass tag = new MetaClass("class-tag", "Tag", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass audit = new MetaClass("class-audit", "Audit", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation employer = new MetaRelation("reference-employer", "employer", "class-person", "class-company", MetaRelationKind.REFERENCE, Multiplicity.many(), Multiplicity.optional(), "reference-employees", true, true, Map.of());
        MetaRelation employees = new MetaRelation("reference-employees", "employees", "class-company", "class-person", MetaRelationKind.CONTAINMENT, Multiplicity.optional(), Multiplicity.many(), "reference-employer", true, true, Map.of());
        MetaRelation aggregation = new MetaRelation("aggregation-tags", "tags", "class-company", "class-tag", MetaRelationKind.AGGREGATION, null, Multiplicity.many(), "", true, true, Map.of("visual", "shared"));
        MetaRelation dependency = new MetaRelation("dependency-audit", "audits", "class-company", "class-audit", MetaRelationKind.DEPENDENCY, null, null, "", true, true, Map.of());
        MetaRelation inheritance = new MetaRelation("inheritance-employee", "", "class-employee", "class-person", MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of());
        MetaRelation implementation = new MetaRelation("implementation-person", "", "class-person", "interface-identifiable", MetaRelationKind.IMPLEMENTATION, null, null, "", true, true, Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "company", "company", List.of(identifiable, person, employee, company, tag, audit), List.of(status), List.of(money), List.of(employer, employees, aggregation, dependency, inheritance, implementation), Map.of("owner", "editor"));
    }
}
