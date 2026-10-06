package com.example.editor.metamodel.export;

import java.io.ByteArrayInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba exportaciones binarias desde el contexto canónico. */
class MetamodelBinaryExporterTests {

    @Test
    void exportsPngJavaTypeScriptJavaScriptGoAndPython() throws Exception {
        NeutralMetamodelExportContext context = CanonicalExportTestFixtures.context();

        byte[] png = new PngMetamodelExporter().export(context);
        byte[] javaZip = new JavaMetamodelExporter().export(context);
        byte[] typeScriptZip = new TypeScriptMetamodelExporter().export(context);
        byte[] javaScriptZip = new JavaScriptMetamodelExporter().export(context);
        byte[] goZip = new GoMetamodelExporter().export(context);
        byte[] pythonZip = new PythonMetamodelExporter().export(context);

        assertThat(png).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
        assertThat(readEntries(javaZip)).contains("Person.java", "Company.java");
        assertThat(readEntries(typeScriptZip)).contains("src/model.ts", "package.json", "tsconfig.json");
        assertThat(readEntries(javaScriptZip)).contains("src/model.js", "src/index.js", "package.json");
        assertThat(readEntries(goZip)).contains("model/model.go", "go.mod");
        assertThat(readEntries(pythonZip)).contains("company.py");
    }

    private java.util.List<String> readEntries(byte[] content) throws Exception {
        java.util.List<String> names = new java.util.ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
