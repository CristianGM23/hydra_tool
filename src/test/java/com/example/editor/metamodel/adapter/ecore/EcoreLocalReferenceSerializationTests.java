package com.example.editor.metamodel.adapter.ecore;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que las referencias internas se serializan dentro del mismo recurso.
 */
class EcoreLocalReferenceSerializationTests {

    /**
     * Descripción:
     * Comprueba herencia y referencias opuestas después de cargar los bytes.
     *
     * @throws Exception cuando falla la carga.
     */
    @Test
    void keepsInheritanceAndOppositesAsLocalObjects() throws Exception {
        EcoreAdapterProperties properties = new EcoreAdapterProperties();
        DefaultEcoreMetamodelAdapter adapter = new DefaultEcoreMetamodelAdapter(properties, new EcorePrimitiveTypeRegistry(properties), new NeutralMetamodelValidator());
        MetaClass entity = new MetaClass("class-entity", "Entity", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass person = new MetaClass("class-person", "Person", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass company = new MetaClass("class-company", "Company", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation inheritance = new MetaRelation("inheritance-person", "", "class-person", "class-entity", MetaRelationKind.INHERITANCE, null, null, "", true, true, Map.of());
        MetaRelation employer = new MetaRelation("reference-employer", "employer", "class-person", "class-company", MetaRelationKind.REFERENCE, Multiplicity.many(), Multiplicity.optional(), "reference-employees", true, true, Map.of());
        MetaRelation employees = new MetaRelation("reference-employees", "employees", "class-company", "class-person", MetaRelationKind.CONTAINMENT, Multiplicity.optional(), Multiplicity.many(), "reference-employer", true, true, Map.of());
        MetaModel metamodel = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "company", "company", List.of(entity, person, company), List.of(), List.of(), List.of(inheritance, employer, employees), Map.of());
        byte[] bytes = adapter.toBytes(metamodel).value();
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        Resource resource = resourceSet.createResource(URI.createURI("memory:/company.ecore"));
        resource.load(new ByteArrayInputStream(bytes), Map.of());
        EPackage ePackage = (EPackage) resource.getContents().get(0);
        EClass loadedPerson = (EClass) ePackage.getEClassifier("Person");
        EClass loadedCompany = (EClass) ePackage.getEClassifier("Company");
        EReference loadedEmployer = (EReference) loadedPerson.getEStructuralFeature("employer");
        EReference loadedEmployees = (EReference) loadedCompany.getEStructuralFeature("employees");

        assertThat(loadedPerson.getESuperTypes()).extracting(EClass::getName).containsExactly("Entity");
        assertThat(loadedEmployer.getEOpposite()).isSameAs(loadedEmployees);
        assertThat(loadedEmployees.getEOpposite()).isSameAs(loadedEmployer);
    }
}
