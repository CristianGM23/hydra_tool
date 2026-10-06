package com.example.editor.metamodel.projection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.adapter.ecore.DefaultEcoreMetamodelAdapter;
import com.example.editor.metamodel.adapter.ecore.EcoreAdapterProperties;
import com.example.editor.metamodel.adapter.ecore.EcorePrimitiveTypeRegistry;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.target.EcoreAttributeConfiguration;
import com.example.editor.metamodel.target.EcoreDataTypeConfiguration;
import com.example.editor.metamodel.target.EcoreGenerationMode;
import com.example.editor.metamodel.target.EcoreProjectionConfiguration;
import com.example.editor.metamodel.target.EcoreRelationConfiguration;
import com.example.editor.metamodel.target.TargetConfigurationDocument;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

import static org.assertj.core.api.Assertions.assertThat;

class EcoreMirrorServiceTests {

    @TempDir
    Path temporaryDirectory;

    private Path metamodelDirectory;
    private EcoreMirrorService service;
    private MetaModel metamodel;

    @BeforeEach
    void setUp() {
        metamodelDirectory = temporaryDirectory.resolve("metamodels");
        StorageProperties storage = new StorageProperties(metamodelDirectory, temporaryDirectory.resolve("models"), temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"), temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        EcoreAdapterProperties properties = new EcoreAdapterProperties();
        var adapter = new DefaultEcoreMetamodelAdapter(properties, new EcorePrimitiveTypeRegistry(properties), new NeutralMetamodelValidator());
        service = new EcoreMirrorService(storage, adapter);
        metamodel = model();
    }

    @Test
    void generatesAnEcoreArtifactWithTheConfiguredFileName() {
        ProjectionResult result = service.generate(metamodel, targets(configuration()));

        assertThat(result.status()).isEqualTo(ProjectionStatus.UP_TO_DATE);
        assertThat(result.artifactPath()).isEqualTo("sample-runtime.ecore");
        assertThat(metamodelDirectory.resolve("sample-runtime.ecore")).isRegularFile();
    }

    @Test
    void appliesTheConfiguredNamespaceValues() {
        service.generate(metamodel, targets(configuration()));

        EPackage ePackage = loadPackage(metamodelDirectory.resolve("sample-runtime.ecore"));
        assertThat(ePackage.getNsURI()).isEqualTo("https://example.org/sample");
        assertThat(ePackage.getNsPrefix()).isEqualTo("sampleRuntime");
    }

    @Test
    void appliesAttributeRuntimeOptionsByStableIdentifier() {
        EcoreProjectionConfiguration configuration = configurationWith(
                Map.of("attribute-name", new EcoreAttributeConfiguration(false, true, true, true, true, true)),
                configuration().relationOptions(),
                configuration().dataTypeOptions());
        service.generate(metamodel, targets(configuration));

        EAttribute attribute = (EAttribute) requiredClass(loadPackage(metamodelDirectory.resolve("sample-runtime.ecore")), "Person").getEStructuralFeature("name");
        assertThat(attribute.isChangeable()).isFalse();
        assertThat(attribute.isTransient()).isTrue();
        assertThat(attribute.isVolatile()).isTrue();
        assertThat(attribute.isDerived()).isTrue();
        assertThat(attribute.isUnsettable()).isTrue();
        assertThat(attribute.isID()).isTrue();
    }

    @Test
    void appliesReferenceRuntimeOptionsByStableIdentifier() {
        EcoreProjectionConfiguration configuration = configurationWith(
                configuration().attributeOptions(),
                Map.of("reference-owner", new EcoreRelationConfiguration(false, false, true, true, true, true)),
                configuration().dataTypeOptions());
        service.generate(metamodel, targets(configuration));

        EReference reference = (EReference) requiredClass(loadPackage(metamodelDirectory.resolve("sample-runtime.ecore")), "Company").getEStructuralFeature("owner");
        assertThat(reference.isChangeable()).isFalse();
        assertThat(reference.isResolveProxies()).isFalse();
        assertThat(reference.isTransient()).isTrue();
        assertThat(reference.isVolatile()).isTrue();
        assertThat(reference.isDerived()).isTrue();
        assertThat(reference.isUnsettable()).isTrue();
    }

    @Test
    void appliesDataTypeRuntimeOptionsByStableIdentifier() {
        EcoreProjectionConfiguration configuration = configurationWith(
                configuration().attributeOptions(),
                configuration().relationOptions(),
                Map.of("datatype-money", new EcoreDataTypeConfiguration("java.math.BigDecimal", false)));
        service.generate(metamodel, targets(configuration));

        EDataType dataType = (EDataType) loadPackage(metamodelDirectory.resolve("sample-runtime.ecore")).getEClassifier("Money");
        assertThat(dataType.getInstanceClassName()).isEqualTo("java.math.BigDecimal");
        assertThat(dataType.isSerializable()).isFalse();
    }

    @Test
    void returnsNotRequestedWhenTheProjectionIsDisabled() {
        EcoreProjectionConfiguration disabled = new EcoreProjectionConfiguration(false, EcoreGenerationMode.DISABLED, true, "sample-runtime.ecore", "urn:sample", "sample", Map.of(), Map.of(), Map.of());

        ProjectionResult result = service.generate(metamodel, targets(disabled));

        assertThat(result.status()).isEqualTo(ProjectionStatus.NOT_REQUESTED);
        assertThat(metamodelDirectory.resolve("sample-runtime.ecore")).doesNotExist();
    }

    @Test
    void rejectsPathTraversalWithoutWritingOutsideTheStorageDirectory() {
        EcoreProjectionConfiguration invalid = new EcoreProjectionConfiguration(true, EcoreGenerationMode.ON_SAVE, true, "../outside.ecore", "urn:sample", "sample", Map.of(), Map.of(), Map.of());

        ProjectionResult result = service.generate(metamodel, targets(invalid));

        assertThat(result.status()).isEqualTo(ProjectionStatus.FAILED);
        assertThat(result.errorMessage()).contains("invalid");
        assertThat(temporaryDirectory.resolve("outside.ecore")).doesNotExist();
    }

    @Test
    void rejectsFilesThatAreNotEcoreArtifacts() {
        EcoreProjectionConfiguration invalid = new EcoreProjectionConfiguration(true, EcoreGenerationMode.ON_SAVE, true, "sample.json", "urn:sample", "sample", Map.of(), Map.of(), Map.of());

        assertThat(service.generate(metamodel, targets(invalid)).status()).isEqualTo(ProjectionStatus.FAILED);
    }

    @Test
    void replacesAnExistingArtifactAtomically() throws Exception {
        Path artifact = metamodelDirectory.resolve("sample-runtime.ecore");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, "old");

        ProjectionResult result = service.generate(metamodel, targets(configuration()));

        assertThat(result.status()).isEqualTo(ProjectionStatus.UP_TO_DATE);
        assertThat(Files.readString(artifact)).doesNotContain("old");
        assertThat(artifact.resolveSibling("sample-runtime.ecore.tmp")).doesNotExist();
    }

    @Test
    void deletesTheConfiguredArtifactAndTemporaryFile() throws Exception {
        Path artifact = metamodelDirectory.resolve("sample-runtime.ecore");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, "content");
        Files.writeString(artifact.resolveSibling("sample-runtime.ecore.tmp"), "temporary");

        service.deleteArtifact(metamodel.id(), targets(configuration()));

        assertThat(artifact).doesNotExist();
        assertThat(artifact.resolveSibling("sample-runtime.ecore.tmp")).doesNotExist();
    }

    @Test
    void generatedEcorePreservesStableClassifierAndFeatureIdentifiers() {
        service.generate(metamodel, targets(configuration()));

        var adapterProperties = new EcoreAdapterProperties();
        var adapter = new DefaultEcoreMetamodelAdapter(adapterProperties, new EcorePrimitiveTypeRegistry(adapterProperties), new NeutralMetamodelValidator());
        MetaModel imported = adapter.fromPath(metamodelDirectory.resolve("sample-runtime.ecore")).value();

        assertThat(imported.classes()).extracting(MetaClass::id).containsExactlyInAnyOrder("class-person", "class-company");
        assertThat(imported.classes()).flatExtracting(MetaClass::attributes).extracting(MetaAttribute::id).contains("attribute-name");
        assertThat(imported.relations()).extracting(MetaRelation::id).contains("reference-owner");
    }

    private TargetConfigurationDocument targets(EcoreProjectionConfiguration configuration) {
        return new TargetConfigurationDocument(TargetConfigurationDocument.CURRENT_SCHEMA_VERSION, metamodel.id(), configuration, Map.of());
    }

    private EcoreProjectionConfiguration configuration() {
        return configurationWith(
                Map.of("attribute-name", EcoreAttributeConfiguration.defaults()),
                Map.of("reference-owner", EcoreRelationConfiguration.defaults()),
                Map.of("datatype-money", EcoreDataTypeConfiguration.defaults()));
    }

    private EcoreProjectionConfiguration configurationWith(Map<String, EcoreAttributeConfiguration> attributes, Map<String, EcoreRelationConfiguration> relations, Map<String, EcoreDataTypeConfiguration> dataTypes) {
        return new EcoreProjectionConfiguration(true, EcoreGenerationMode.ON_SAVE, true, "sample-runtime.ecore", "https://example.org/sample", "sampleRuntime", attributes, relations, dataTypes);
    }

    private EPackage loadPackage(Path path) {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        var resource = resourceSet.getResource(URI.createFileURI(path.toAbsolutePath().toString()), true);
        return (EPackage) resource.getContents().getFirst();
    }

    private EClass requiredClass(EPackage ePackage, String name) {
        return (EClass) ePackage.getEClassifier(name);
    }

    private MetaModel model() {
        MetaAttribute name = new MetaAttribute("attribute-name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaClass person = new MetaClass("class-person", "Person", MetaClassKind.CLASS, List.of(name), List.of(), Map.of());
        MetaClass company = new MetaClass("class-company", "Company", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaDataType money = new MetaDataType("datatype-money", "Money", Map.of());
        MetaRelation owner = new MetaRelation("reference-owner", "owner", "class-company", "class-person", MetaRelationKind.REFERENCE, null, Multiplicity.one(), "", true, true, Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "Sample", List.of(person, company), List.of(), List.of(money), List.of(owner), Map.of());
    }
}
