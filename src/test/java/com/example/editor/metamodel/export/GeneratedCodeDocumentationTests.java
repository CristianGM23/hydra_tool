package com.example.editor.metamodel.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba la documentación y las operaciones completas del código generado. */
class GeneratedCodeDocumentationTests {

    @Test
    void javaExportIncludesJavadocsAccessorsAndObjectMethods() throws Exception {
        Map<String, String> files = unzip(new JavaMetamodelExporter().export(CanonicalExportTestFixtures.context()));
        String person = files.get("Person.java");
        String money = files.get("Money.java");

        assertThat(person).contains("Generated representation of the metamodel class \"Person\"");
        assertThat(person).contains("Metamodel attribute \"name\" of type String with multiplicity 1.");
        assertThat(person).contains("public String getName()", "public void setName(String value)");
        assertThat(person).contains("public Company getEmployer()", "public void setEmployer(Company value)");
        assertThat(person).contains("public String toString()", "public boolean equals(Object object)", "public int hashCode()");
        assertThat(person).contains("Generated metamodel operation \"formatName\"", "public String formatName(String prefix)");
        assertThat(files.get("Identifiable.java")).contains("public String displayName()");
        assertThat(money).contains("public String getValue()", "public void setValue(String value)", "public boolean equals(Object object)");
    }

    @Test
    void pythonExportIncludesDocstringsAccessorsStringAndEquality() throws Exception {
        Map<String, String> files = unzip(new PythonMetamodelExporter().export(CanonicalExportTestFixtures.context()));
        String module = files.get("company.py");

        assertThat(module).contains("Generated Python representation of the metamodel Company.");
        assertThat(module).contains("class Person(Identifiable):", "Generated representation of the metamodel class Person.");
        assertThat(module).contains("def get_name(self) -> str:", "def set_name(self, value: str) -> None:");
        assertThat(module).contains("def get_employer(self) -> Optional[Company]:", "def set_employer(self, value: Optional[Company]) -> None:");
        assertThat(module).contains("def __str__(self) -> str:", "def __eq__(self, other: object) -> bool:");
        assertThat(module).contains("def format_name(self, prefix: Optional[str]) -> str:", "def display_name(self) -> str:");
        assertThat(module).contains("class Money:", "def get_value(self) -> Optional[str]:");
    }

    private Map<String, String> unzip(byte[] content) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();

        try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            var entry = zip.getNextEntry();

            while(entry != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                zip.transferTo(output);
                result.put(entry.getName(), output.toString(StandardCharsets.UTF_8));
                entry = zip.getNextEntry();
            }
        }

        return result;
    }
}
