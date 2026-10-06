package com.example.editor.metamodel.export;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba las exportaciones binarias.
 */
class NeutralBinaryExportTests {

    @Test
    void exportsValidPngSignature() {
        byte[] png = new PngMetamodelExporter().export(CanonicalExportTestFixtures.context());

        assertThat(png).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
    }

    @Test
    void exportsJavaClassesAtZipRootByDefault() throws Exception {
        byte[] content = new JavaMetamodelExporter().export(CanonicalExportTestFixtures.context());

        assertThat(entries(content)).contains("Person.java", "Company.java", "Status.java");
    }

    @Test
    void exportsJavaClassesUnderConfiguredPackage() throws Exception {
        NeutralMetamodelExportContext source = CanonicalExportTestFixtures.context();
        MetamodelExportOptions options = new MetamodelExportOptions(true, true, true, false, 2, "com.example.generated");
        NeutralMetamodelExportContext context = new DefaultNeutralMetamodelExportContext(source.neutralMetamodel(), source.diagram(), source.targets(), source.warnings(), options);
        byte[] content = new JavaMetamodelExporter().export(context);

        assertThat(entries(content)).contains("com/example/generated/Person.java");
    }

    @Test
    void exportsPythonModule() throws Exception {
        byte[] content = new PythonMetamodelExporter().export(CanonicalExportTestFixtures.context());

        assertThat(entries(content)).containsExactly("company.py");
    }

    private List<String> entries(byte[] content) throws Exception {
        List<String> result = new ArrayList<>();

        try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            var entry = zip.getNextEntry();

            while(entry != null) {
                result.add(entry.getName());
                entry = zip.getNextEntry();
            }
        }

        return result;
    }
}
