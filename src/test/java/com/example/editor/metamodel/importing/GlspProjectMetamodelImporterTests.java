package com.example.editor.metamodel.importing;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.importing.importers.GlspProjectMetamodelImporter;
import com.example.editor.metamodel.importing.importers.JavaMetamodelImporter;
import com.example.editor.metamodel.importing.importers.PythonMetamodelImporter;
import com.example.editor.metamodel.importing.python.PythonAstImportProperties;
import com.example.editor.metamodel.importing.python.PythonProcessAstAnalyzer;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlspProjectMetamodelImporterTests {

    private final MetamodelImportProperties properties = new MetamodelImportProperties();
    private final SafeImportArchiveReader reader = new SafeImportArchiveReader(properties);
    private final tools.jackson.databind.ObjectMapper mapper = JsonMapper.builder().build();
    private final JavaMetamodelImporter javaImporter = new JavaMetamodelImporter(reader);
    private final PythonMetamodelImporter pythonImporter = pythonImporter();
    private final GlspProjectMetamodelImporter importer = new GlspProjectMetamodelImporter(reader, null, javaImporter, pythonImporter, mapper);

    @Test void requiresAProjectArchive() {
        assertThatThrownBy(() -> importer.importMetamodel(new MetamodelImportSource("model.ts", "text/plain", "export class A{}".getBytes()), MetamodelImportOptions.defaults()))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("project ZIP");
    }

    @Test void importsTypeScriptClassesInterfacesAndEnums() throws Exception {
        var output = importProject(Map.of("server/src/model.ts", "export interface Node { id: string; }\nexport class Diagram { root: Node; }\nexport enum Kind { A, B }", "server/package.json", glspPackage()));
        assertThat(output.metamodel().classes()).extracting(item -> item.name()).containsExactly("Node", "Diagram");
        assertThat(output.metamodel().enums()).extracting(item -> item.name()).containsExactly("Kind");
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_GLSP_STRUCTURAL_INTERFACE_ADAPTED");
    }

    @Test void derivesTypeScriptFieldsRelationsAndOperations() throws Exception {
        var output = importProject(Map.of("server/model.ts", "export interface Node { id: string; children: Node[]; validate(limit: number): boolean; }", "package.json", glspPackage()));
        var node = output.metamodel().classes().getFirst();
        assertThat(node.attributes()).extracting(item -> item.name()).contains("id");
        assertThat(output.metamodel().relations()).extracting(item -> item.name()).contains("children");
        assertThat(node.operations()).extracting(item -> item.name()).contains("validate");
    }

    @Test void detectsDiagramTypesFromSourceDeclarations() throws Exception {
        var output = importProject(Map.of("server/model.ts", "export class Node{}", "server/module.ts", "export const diagramType = 'workflow';", "package.json", glspPackage()));
        assertThat(output.metamodel().annotations()).containsEntry("glsp:diagramTypes", "workflow");
    }

    @Test void detectsDiagramTypesFromJsonContracts() throws Exception {
        var output = importProject(Map.of("server/model.ts", "export class Node{}", "server/config.json", "{\"diagramTypes\":[\"one\",\"two\"]}", "package.json", glspPackage()));
        assertThat(output.metamodel().annotations().get("glsp:diagramTypes")).contains("one", "two");
    }

    @Test void detectsVsCodeTheiaAndStandalonePlatforms() throws Exception {
        String descriptor = "{\"dependencies\":{\"@eclipse-glsp/client\":\"2.6.0\",\"@eclipse-glsp/vscode-integration\":\"2.6.0\",\"@eclipse-glsp/theia-integration\":\"2.6.0\"}}";
        var output = importProject(Map.of("server/model.ts", "export class Node{}", "client/package.json", descriptor));
        assertThat(output.metamodel().annotations().get("glsp:platforms")).contains("VS_CODE", "THEIA", "STANDALONE_WEB");
    }

    @Test void prefersJavaServerModelsWhenNoEcoreExists() throws Exception {
        var output = importProject(Map.of("server/src/main/java/demo/Node.java", "package demo; public class Node { String id; }", "server/pom.xml", "<dependency><groupId>org.eclipse.glsp</groupId></dependency>"));
        assertThat(output.metamodel().classes()).singleElement().extracting(item -> item.name()).isEqualTo("Node");
        assertThat(output.metamodel().annotations()).containsEntry("glsp:serverLanguage", "JAVA");
    }

    @Test void importsPythonServerModelsAsAConservativeExtension() throws Exception {
        var output = importProject(Map.of("server/model.py", "class Node:\n    id: str\n", "client/package.json", glspPackage()));
        assertThat(output.metamodel().classes()).singleElement().extracting(item -> item.name()).isEqualTo("Node");
        assertThat(output.metamodel().annotations()).containsEntry("glsp:serverLanguage", "PYTHON");
    }

    @Test void reportsMissingDiagramTypeWithoutInventingOne() throws Exception {
        var output = importProject(Map.of("server/model.ts", "export class Node{}", "package.json", glspPackage()));
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_GLSP_DIAGRAM_TYPE_UNKNOWN");
        assertThat(output.metamodel().annotations()).containsEntry("glsp:diagramTypes", "");
    }

    @Test void preservesProjectModulesAndManualHandlersAsProvenance() throws Exception {
        var output = importProject(Map.of("client/package.json", glspPackage(), "server/src/model.ts", "export class Node{}", "server/src/handlers.ts", "export class CreateNodeHandler{}"));
        assertThat(output.metamodel().annotations().get("glsp:modules")).contains("client", "server");
        assertThat(output.metamodel().annotations()).containsEntry("glsp:manualOperationHandlersPreserved", "true");
    }

    @Test void rejectsProjectsWithoutRecognizableSemanticSources() throws Exception {
        assertThatThrownBy(() -> importProject(Map.of("package.json", glspPackage(), "README.md", "No model")))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("does not contain an Ecore, Java, Python or TypeScript semantic source");
    }

    @Test void rejectsZipSlipPaths() throws Exception {
        byte[] content = zip(Map.of("../server/model.ts", "export class Unsafe{}", "package.json", glspPackage()));
        assertThatThrownBy(() -> importer.importMetamodel(new MetamodelImportSource("unsafe.glsp.zip", "application/zip", content), MetamodelImportOptions.defaults()))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Unsafe import archive path");
    }

    @Test void detectorRecognizesGlspBeforeGenericJavaOrTypeScript() throws Exception {
        byte[] content = zip(Map.of("server/src/Node.java", "class Node{}", "server/pom.xml", "<artifactId>org.eclipse.glsp.server</artifactId>"));
        assertThat(new MetamodelImportDetector().detect(new MetamodelImportSource("project.zip", "application/zip", content))).isEqualTo(MetamodelImportFormat.GLSP);
    }

    private MetamodelImportOutput importProject(Map<String, String> files) throws Exception {
        return importer.importMetamodel(new MetamodelImportSource("sample.glsp.zip", "application/zip", zip(files)), MetamodelImportOptions.defaults());
    }

    private PythonMetamodelImporter pythonImporter() {
        PythonAstImportProperties settings = new PythonAstImportProperties();
        settings.setExecutable("python");
        return new PythonMetamodelImporter(reader, new PythonProcessAstAnalyzer(mapper, settings));
    }

    private String glspPackage() {
        return "{\"dependencies\":{\"@eclipse-glsp/client\":\"2.6.0\"}}";
    }

    private byte[] zip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try(ZipOutputStream output = new ZipOutputStream(bytes)) {
            for(var entry : new LinkedHashMap<>(files).entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
