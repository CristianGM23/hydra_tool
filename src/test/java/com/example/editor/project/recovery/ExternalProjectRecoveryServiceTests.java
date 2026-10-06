package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.example.editor.project.LocalProjectProperties;

class ExternalProjectRecoveryServiceTests {
    @Test
    void inventoriesMixedProjectsAndKeepsUnknownResources() throws Exception {
        byte[] archive = zip(
                "language/src/model/people.ecore", "<ecore:EPackage xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><eClassifiers xsi:type=\"ecore:EClass\" name=\"Person\"/></ecore:EPackage>",
                "language/src/People.xtext", "grammar People with org.eclipse.xtext.common.Terminals\nPerson: name=ID;",
                "language/plugin.xml", "<plugin><extension point=\"org.eclipse.ui.editors\"><editor id=\"people\" name=\"People\"/></extension></plugin>",
                "assets/custom.bin", "opaque");
        ExternalProjectRecoveryReport report = service().analyze("people.zip", new ByteArrayInputStream(archive), archive.length);

        assertThat(report.technologies()).contains("EMF", "Xtext", "Eclipse");
        assertThat(report.resources()).anyMatch(resource -> resource.path().equals("assets/custom.bin")
                && resource.kind() == ExternalProjectResourceKind.PROJECT_RESOURCE);
        assertThat(report.mappingEvidence()).anyMatch(item -> item.targetId().equals("METACLASS:Person"));
        assertThat(report.integrationSuggestions()).anyMatch(item -> item.technology().equals("Xtext"));
        assertThat(report.grammarInspections()).singleElement().satisfies(grammar -> {
            assertThat(grammar.technology()).isEqualTo("XTEXT");
            assertThat(grammar.inferredEntryRule()).isEqualTo("Person");
            assertThat(grammar.parserRules()).contains("Person");
        });
    }

    @Test
    void reportsSafeAndProblematicRecursionBeforeExternalProjectMaterialization() throws Exception {
        byte[] archive = zip(
                "language/main.langium", "grammar Main\nentry Model: expression=Expression;\nExpression: Expression '+' INT | INT;",
                "language/tree.g4", "grammar Tree;\nnode: 'node' node?;\nWS: [ \t\r\n]+ -> skip;");
        ExternalProjectRecoveryReport report = service().analyze("grammars.zip", new ByteArrayInputStream(archive), archive.length);

        assertThat(report.grammarInspections()).hasSize(2);
        assertThat(report.grammarInspections()).filteredOn(item -> item.technology().equals("LANGIUM")).singleElement().satisfies(grammar -> {
            assertThat(grammar.inferredEntryRule()).isEqualTo("Model");
            assertThat(grammar.leftRecursiveRules()).contains("Expression");
            assertThat(grammar.automaticResolutionRules()).contains("Expression");
            assertThat(grammar.explanations()).anyMatch(message -> message.contains("Direct left recursion"));
        });
    }

    @Test
    void traversesNestedArchivesWithVirtualPaths() throws Exception {
        byte[] nested = zip("src/model.ecore", "<ecore:EPackage xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\"/>");
        byte[] outer = zipBytes("tools/generator.zip", nested);
        ExternalProjectRecoveryReport report = service().analyze("project.zip", new ByteArrayInputStream(outer), outer.length);
        assertThat(report.resources()).anyMatch(resource -> resource.path().equals("tools/generator.zip!/src/model.ecore"));
    }

    private ExternalProjectRecoveryService service() {
        LocalProjectProperties properties = new LocalProjectProperties();
        properties.setMaxNestedArchiveDepth(4);
        properties.setMaxArchiveCompressionRatio(500);
        return new ExternalProjectRecoveryService(new ExternalProjectArchiveWalker(properties));
    }

    private byte[] zip(String... entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try(ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for(int index = 0; index < entries.length; index += 2) {
                zip.putNextEntry(new ZipEntry(entries[index]));
                zip.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] zipBytes(String path, byte[] content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try(ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(content);
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
