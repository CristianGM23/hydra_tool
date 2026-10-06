package com.example.editor.metamodel.adapter.ecore;

import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba la proyección bidireccional de MetaOperation y MetaParameter. */
class EcoreOperationRoundTripTests {

    private DefaultEcoreMetamodelAdapter adapter;

    @BeforeEach
    void setUp() {
        EcoreAdapterProperties properties = new EcoreAdapterProperties();
        adapter = new DefaultEcoreMetamodelAdapter(properties, new EcorePrimitiveTypeRegistry(properties), new NeutralMetamodelValidator());
    }

    @Test
    void exportsOperationsParametersReturnMultiplicityAndBehavior() {
        EPackage ePackage = adapter.toEPackage(model()).value();
        EClass service = (EClass) ePackage.getEClassifier("Service");
        EOperation find = service.getEOperations().stream().filter(item -> item.getName().equals("find")).findFirst().orElseThrow();
        EOperation reset = service.getEOperations().stream().filter(item -> item.getName().equals("reset")).findFirst().orElseThrow();

        assertThat(find.getEType().getName()).isEqualTo("Result");
        assertThat(find.getLowerBound()).isZero();
        assertThat(find.getUpperBound()).isEqualTo(-1);
        assertThat(find.getEParameters()).singleElement().satisfies(parameter -> {
            assertThat(parameter.getName()).isEqualTo("query");
            assertThat(parameter.getEType().getName()).isEqualTo("EString");
        });
        assertThat(EcoreNeutralMetadataSupport.readMetadata(find, EcoreNeutralMetadataSupport.OPERATION_BEHAVIOR_KIND_KEY)).isEqualTo("QUERY");
        assertThat(reset.getEType()).isNull();
    }

    @Test
    void importsOperationsOnClassesAndInterfacesWithoutLosingAttributes() {
        MetaModel restored = adapter.fromEPackage(adapter.toEPackage(model()).value(), "operations.ecore").value();
        MetaClass contract = restored.classes().stream().filter(item -> item.name().equals("Contract")).findFirst().orElseThrow();
        MetaClass service = restored.classes().stream().filter(item -> item.name().equals("Service")).findFirst().orElseThrow();

        assertThat(contract.kind()).isEqualTo(MetaClassKind.INTERFACE);
        assertThat(contract.attributes()).extracting(MetaAttribute::name).containsExactly("identifier");
        assertThat(contract.operations()).extracting(MetaOperation::name).containsExactly("displayName");
        assertThat(service.operations()).extracting(MetaOperation::name).containsExactly("find", "reset");
        assertThat(service.operations().getFirst().parameters()).extracting(MetaParameter::name).containsExactly("query");
        assertThat(service.operations().getFirst().behaviorKind()).isEqualTo(MetaOperationBehaviorKind.QUERY);
        assertThat(service.operations().getFirst().exceptionTypes()).containsExactly(MetaTypeReference.external("java.io.IOException"));
    }

    private MetaModel model() {
        MetaAttribute identifier = new MetaAttribute("attribute:Contract:identifier", "identifier", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaOperation displayName = new MetaOperation("operation:Contract:displayName", "displayName", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, List.of(), List.of(), MetaOperationBehaviorKind.QUERY, Map.of());
        MetaClass contract = new MetaClass("class:Contract", "Contract", MetaClassKind.INTERFACE, List.of(identifier), List.of(displayName), Map.of());
        MetaClass result = new MetaClass("class:Result", "Result", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaParameter query = new MetaParameter("parameter:Service:find:query", "query", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, Map.of());
        MetaOperation find = new MetaOperation("operation:Service:find", "find", MetaTypeReference.classType("class:Result"), Multiplicity.many(), true, true, List.of(query), List.of(MetaTypeReference.external("java.io.IOException")), MetaOperationBehaviorKind.QUERY, Map.of());
        MetaOperation reset = new MetaOperation("operation:Service:reset", "reset", null, null, true, true, List.of(), List.of(), MetaOperationBehaviorKind.COMMAND, Map.of());
        MetaClass service = new MetaClass("class:Service", "Service", MetaClassKind.CLASS, List.of(), List.of(find, reset), Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "operations", "operations", List.of(contract, result, service), List.of(), List.of(), List.of(), Map.of());
    }
}
