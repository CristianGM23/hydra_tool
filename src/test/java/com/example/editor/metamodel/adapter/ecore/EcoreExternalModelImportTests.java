package com.example.editor.metamodel.adapter.ecore;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.TransformationResult;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Ecore models written outside HYDRA (EMF's own extlibrary example and Ecore.ecore, found by the battery scenario INT-REAL) use two
 * constructs the importer rejected: a reference whose type is an Ecore interface, and overloaded EOperations.
 */
class EcoreExternalModelImportTests {
    private static final EcoreFactory F = EcoreFactory.eINSTANCE;
    private DefaultEcoreMetamodelAdapter adapter;

    @BeforeEach
    void setUp() {
        EcoreAdapterProperties properties = new EcoreAdapterProperties();
        adapter = new DefaultEcoreMetamodelAdapter(properties, new EcorePrimitiveTypeRegistry(properties), new NeutralMetamodelValidator());
    }

    @Test
    void anInterfaceUsedAsAReferenceTypeIsImportedWithTheDeclaredAdaptationAndAWarning() {
        TransformationResult<MetaModel> result = adapter.fromEPackage(library(), "library.ecore");
        Map<String, MetaClass> classes = byName(result.value());

        assertThat(classes.get("Lendable").kind()).isEqualTo(MetaClassKind.ABSTRACT_CLASS);
        assertThat(classes.get("Lendable").annotations()).containsEntry("hydra:semanticAdaptation", "STRUCTURAL_INTERFACE_TO_ABSTRACT_CLASS")
                .containsEntry("ecore:declaredInterface", "true");
        // an interface that extends an adapted one is adapted too, otherwise interface -> class inheritance would be invalid
        assertThat(classes.get("Renewable").kind()).isEqualTo(MetaClassKind.ABSTRACT_CLASS);
        // an interface no structural relation points to keeps its kind
        assertThat(classes.get("Addressable").kind()).isEqualTo(MetaClassKind.INTERFACE);
        // one warning per adapted class, as the Java, Python, GLSP, TypeScript and Go importers do
        assertThat(result.warnings()).filteredOn(w -> w.code().equals("ECORE_STRUCTURAL_INTERFACE_ADAPTED")).extracting(TransformationWarning::path)
                .containsExactly("$.classes[class:Lendable]", "$.classes[class:Renewable]");
        assertThat(kinds(result.value())).containsEntry("CirculatingItem->Item", MetaRelationKind.INHERITANCE)
                .containsEntry("CirculatingItem->Lendable", MetaRelationKind.INHERITANCE).containsEntry("Library->Addressable", MetaRelationKind.IMPLEMENTATION)
                .containsEntry("Borrower.borrowed->Lendable", MetaRelationKind.REFERENCE);
    }

    @Test
    void theEcoreExportRestoresTheDeclaredInterfaces() {
        EPackage exported = adapter.toEPackage(adapter.fromEPackage(library(), "library.ecore").value()).value();
        EClass lendable = (EClass) exported.getEClassifier("Lendable");
        EClass renewable = (EClass) exported.getEClassifier("Renewable");

        assertThat(lendable.isInterface()).isTrue();
        assertThat(lendable.isAbstract()).isTrue();
        assertThat(renewable.isInterface()).isTrue();
        assertThat(((EClass) exported.getEClassifier("Item")).isInterface()).isFalse();
        // a second import of the export is identical to the first (no drift across generations)
        assertThat(adapter.fromEPackage(exported, "library.ecore").value()).isEqualTo(adapter.fromEPackage(library(), "library.ecore").value());
    }

    @Test
    void overloadedOperationsGetDistinctDeterministicIdsThatSurviveTheRoundTrip() {
        MetaModel imported = adapter.fromEPackage(library(), "library.ecore").value();
        List<MetaOperation> find = byName(imported).get("Library").operations();

        assertThat(find).extracting(MetaOperation::name).containsExactly("find", "find", "count");
        assertThat(find).extracting(MetaOperation::id).doesNotHaveDuplicates()
                .containsExactly("operation:class:Library:find", "operation:class:Library:find:2", "operation:class:Library:count");
        assertThat(find.get(1).parameters()).extracting(p -> p.id()).containsExactly("parameter:operation:class:Library:find:2:title");
        MetaModel again = adapter.fromEPackage(adapter.toEPackage(imported).value(), "library.ecore").value();
        assertThat(byName(again).get("Library").operations()).extracting(MetaOperation::id).isEqualTo(find.stream().map(MetaOperation::id).toList());
    }

    @Test
    void theResolveProxiesFlagOfAnImportedContainmentSurvivesTheExportWhileNativeContainmentsKeepTheDefault() {
        EPackage exported = adapter.toEPackage(adapter.fromEPackage(library(), "library.ecore").value()).value();
        EClass library = (EClass) exported.getEClassifier("Library");

        // EMF's default for every reference is resolveProxies=true; branches keeps it, stock was declared false
        assertThat(((EReference) library.getEStructuralFeature("branches")).isResolveProxies()).isTrue();
        assertThat(((EReference) library.getEStructuralFeature("stock")).isResolveProxies()).isFalse();
        MetaClass root = new MetaClass("class:Root", "Root", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation children = new MetaRelation("reference:Root:children", "children", "class:Root", "class:Root", MetaRelationKind.CONTAINMENT, null,
                com.example.editor.metamodel.core.Multiplicity.many(), "", true, true, Map.of());
        EPackage nativeModel = adapter.toEPackage(new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "native", "native", List.of(root), List.of(), List.of(), List.of(children), Map.of())).value();
        assertThat(((EReference) ((EClass) nativeModel.getEClassifier("Root")).getEStructuralFeature("children")).isResolveProxies()).isFalse();
    }

    /** A reduced extlibrary: Lendable is an interface used as a reference type; Addressable is an interface only implemented. */
    private static EPackage library() {
        EPackage p = F.createEPackage();
        p.setName("library");
        p.setNsURI("urn:library");
        p.setNsPrefix("library");
        EClass addressable = eClass(p, "Addressable", true, true);
        EClass lendable = eClass(p, "Lendable", true, true);
        EClass renewable = eClass(p, "Renewable", true, true);
        renewable.getESuperTypes().add(lendable);
        EClass item = eClass(p, "Item", true, false);
        EClass circulating = eClass(p, "CirculatingItem", true, false);
        circulating.getESuperTypes().addAll(List.of(item, lendable));
        EClass borrower = eClass(p, "Borrower", false, false);
        EReference borrowed = F.createEReference();
        borrowed.setName("borrowed");
        borrowed.setEType(lendable);
        borrowed.setUpperBound(-1);
        borrower.getEStructuralFeatures().add(borrowed);
        EClass library = eClass(p, "Library", false, false);
        library.getESuperTypes().add(addressable);
        EReference branches = F.createEReference();
        branches.setName("branches");
        branches.setEType(library);
        branches.setUpperBound(-1);
        branches.setContainment(true);
        library.getEStructuralFeatures().add(branches);
        EReference stock = F.createEReference();
        stock.setName("stock");
        stock.setEType(item);
        stock.setUpperBound(-1);
        stock.setContainment(true);
        stock.setResolveProxies(false);
        library.getEStructuralFeatures().add(stock);
        library.getEOperations().add(operation("find", "index", EcorePackage.Literals.EINT));
        library.getEOperations().add(operation("find", "title", EcorePackage.Literals.ESTRING));
        library.getEOperations().add(operation("count", null, null));
        return p;
    }

    private static EClass eClass(EPackage p, String name, boolean isAbstract, boolean isInterface) {
        EClass c = F.createEClass();
        c.setName(name);
        c.setAbstract(isAbstract);
        c.setInterface(isInterface);
        p.getEClassifiers().add(c);
        return c;
    }

    private static EOperation operation(String name, String parameter, org.eclipse.emf.ecore.EClassifier type) {
        EOperation o = F.createEOperation();
        o.setName(name);
        if(parameter != null) {
            EParameter e = F.createEParameter();
            e.setName(parameter);
            e.setEType(type);
            o.getEParameters().add(e);
        }
        return o;
    }

    private static Map<String, MetaClass> byName(MetaModel model) {
        return model.classes().stream().collect(Collectors.toMap(MetaClass::name, Function.identity()));
    }

    private static Map<String, MetaRelationKind> kinds(MetaModel model) {
        Map<String, String> names = model.classes().stream().collect(Collectors.toMap(MetaClass::id, MetaClass::name));
        return model.relations().stream().collect(Collectors.toMap((MetaRelation r) -> names.get(r.sourceTypeId()) + (r.name().isBlank() ? "" : "." + r.name()) + "->" + names.get(r.targetTypeId()),
                MetaRelation::kind));
    }
}
