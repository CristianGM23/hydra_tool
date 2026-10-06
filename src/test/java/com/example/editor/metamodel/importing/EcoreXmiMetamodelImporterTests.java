package com.example.editor.metamodel.importing;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.adapter.ecore.EcoreMetamodelAdapter;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.TransformationResult;
import com.example.editor.metamodel.importing.importers.EcoreMetamodelImporter;
import com.example.editor.metamodel.importing.importers.XmiMetamodelImporter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba Ecore y EPackage XMI.
 */
class EcoreXmiMetamodelImporterTests {

    @Test
    void importsEcorePackageRoot() throws Exception {
        StubAdapter adapter = new StubAdapter();
        var output = new EcoreMetamodelImporter(adapter).importMetamodel(new MetamodelImportSource("sample.ecore", "application/xml", ecoreBytes()), MetamodelImportOptions.defaults());
        assertThat(adapter.received.getName()).isEqualTo("sample");
        assertThat(output.metamodel().classes()).extracting(item -> item.name()).containsExactly("Person");
    }

    @Test
    void importsSamePackageThroughXmiImporter() throws Exception {
        StubAdapter adapter = new StubAdapter();
        var output = new XmiMetamodelImporter(adapter).importMetamodel(new MetamodelImportSource("sample.xmi", "application/xml", ecoreBytes()), MetamodelImportOptions.defaults());
        assertThat(output.metamodel().id()).isEqualTo("sample");
    }

    private byte[] ecoreBytes() throws Exception {
        EPackage ePackage = EcoreFactory.eINSTANCE.createEPackage();
        ePackage.setName("sample");
        ePackage.setNsPrefix("sample");
        ePackage.setNsURI("urn:sample");
        EClass person = EcoreFactory.eINSTANCE.createEClass();
        person.setName("Person");
        ePackage.getEClassifiers().add(person);
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        Resource resource = resourceSet.createResource(URI.createURI("memory:/sample.ecore"));
        resource.getContents().add(ePackage);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        resource.save(output, Map.of());
        return output.toByteArray();
    }

    private static class StubAdapter implements EcoreMetamodelAdapter {
        private EPackage received;

        @Override
        public TransformationResult<EPackage> toEPackage(MetaModel metamodel) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransformationResult<byte[]> toBytes(MetaModel metamodel) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransformationResult<MetaModel> fromPath(Path path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransformationResult<MetaModel> fromEPackage(EPackage ePackage, String artifactName) {
            received = ePackage;
            MetaClass person = new MetaClass("class-person", "Person", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
            return new TransformationResult<>(new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "sample", "sample", List.of(person), List.of(), List.of(), List.of(), Map.of()), List.of());
        }
    }
}
