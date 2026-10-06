package com.example.editor.metamodel.importing;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.importing.importers.PythonMetamodelImporter;
import com.example.editor.metamodel.importing.python.PythonAstImportProperties;
import com.example.editor.metamodel.importing.python.PythonProcessAstAnalyzer;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PythonMetamodelImporterTests {

    private PythonMetamodelImporter importer(String executable, boolean fallback) {
        PythonAstImportProperties settings = new PythonAstImportProperties();
        settings.setExecutable(executable);
        settings.setFallbackEnabled(fallback);
        return new PythonMetamodelImporter(new SafeImportArchiveReader(new MetamodelImportProperties()), new PythonProcessAstAnalyzer(JsonMapper.builder().build(), settings));
    }

    @Test void importsDataclassesAndTypedFields() {
        var person = importPython("from dataclasses import dataclass\n@dataclass\nclass Person:\n    name: str\n    age: int = 0\n").metamodel().classes().getFirst();
        assertThat(person.attributes()).extracting(item -> item.name()).containsExactly("name", "age");
    }

    @Test void importsEnums() {
        var model = importPython("from enum import Enum\nclass State(Enum):\n    ACTIVE='a'\n    INACTIVE='i'\n").metamodel();
        assertThat(model.enums()).singleElement().satisfies(item -> assertThat(item.literals()).extracting(literal -> literal.name()).containsExactly("ACTIVE", "INACTIVE"));
    }

    @Test void importsProtocolAsInterface() {
        var item = importPython("from typing import Protocol\nclass Named(Protocol):\n    def label(self)->str: ...\n").metamodel().classes().getFirst();
        assertThat(item.kind()).isEqualTo(MetaClassKind.INTERFACE);
        assertThat(item.annotations()).containsEntry("python:declaredProtocol", "true");
    }

    @Test void preservesQualifiedProtocolProvenance() {
        var item = importPython("import typing\nclass Named(typing.Protocol):\n    value: str\n").metamodel().classes().getFirst();
        assertThat(item.annotations()).containsEntry("python:declaredProtocol", "true");
    }

    @Test void doesNotMarkOrdinaryClassesAsProtocols() {
        var item = importPython("class Named:\n    value: str\n").metamodel().classes().getFirst();
        assertThat(item.annotations()).doesNotContainKey("python:declaredProtocol");
    }

    @Test void importsAbstractClassFromAbstractMethods() {
        var item = importPython("from abc import ABC, abstractmethod\nclass Base(ABC):\n    @abstractmethod\n    def run(self)->None: ...\n").metamodel().classes().getFirst();
        assertThat(item.kind()).isEqualTo(MetaClassKind.ABSTRACT_CLASS);
    }

    @Test void derivesOptionalAndCollectionRelations() {
        var output = importPython("from typing import Optional, List\nclass Address: pass\nclass Person:\n    address: Optional[Address]\n    friends: List['Person']\n");
        assertThat(output.metamodel().relations()).filteredOn(item -> item.name().equals("address")).singleElement();
        assertThat(output.metamodel().relations())
                .filteredOn(item -> item.name().equals("friends"))
                .singleElement()
                .satisfies(item -> assertThat(item.targetMultiplicity().upperBound()).isNull());
    }

    @Test void importsPropertiesAsStructuralFeatures() {
        var item = importPython("class Person:\n    @property\n    def name(self)->str:\n        return 'x'\n").metamodel().classes().getFirst();
        assertThat(item.attributes()).extracting(attribute -> attribute.name()).contains("name");
    }

    @Test void importsOperationsAndParameters() {
        var item = importPython("class Person:\n    def label(self, max_len:int=20)->str:\n        return ''\n").metamodel().classes().getFirst();
        assertThat(item.operations()).singleElement().satisfies(operation -> {
            assertThat(operation.name()).isEqualTo("label");
            assertThat(operation.parameters()).extracting(parameter -> parameter.name()).containsExactly("max_len");
        });
    }

    @Test void neverExecutesImportedTopLevelCode() {
        var output = importPython("raise RuntimeError('must not run')\nclass Safe:\n    value: str\n");
        assertThat(output.metamodel().classes()).singleElement().extracting(item -> item.name()).isEqualTo("Safe");
    }

    @Test void preservesMethodBodiesOnlyAsWarnings() {
        var output = importPython("class Person:\n    def label(self)->str:\n        return 'x'\n");
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_PYTHON_METHOD_BODY_OMITTED");
    }

    @Test void adaptsStructuralProtocolsUsedByFieldsWithoutBreakingRelations() {
        var output = importPython("from typing import Protocol\nclass Node(Protocol):\n    id: str\nclass Diagram:\n    root: Node\n");
        assertThat(output.metamodel().classes())
                .filteredOn(item -> item.name().equals("Node"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.kind()).isEqualTo(MetaClassKind.ABSTRACT_CLASS);
                    assertThat(item.annotations()).containsEntry("python:declaredProtocol", "true");
                });
        assertThat(output.metamodel().relations()).filteredOn(item -> item.name().equals("root")).singleElement();
        assertThat(output.warnings()).extracting(item -> item.code()).contains("IMPORT_PYTHON_STRUCTURAL_INTERFACE_ADAPTED");
    }

    @Test void usesConservativeFallbackWhenPythonIsUnavailable() {
        var fallbackImporter = importer("missing-python-executable-hydra", true);
        var output = fallbackImporter.importMetamodel(source("class Person:\n    name: str\n"), MetamodelImportOptions.defaults());
        assertThat(output.metamodel().classes()).singleElement();
        assertThat(output.metamodel().annotations()).containsEntry("import:pythonAnalyzer", "CONSERVATIVE_FALLBACK");
    }

    @Test void canDisableConservativeFallback() {
        var strictImporter = importer("missing-python-executable-hydra", false);
        assertThatThrownBy(() -> strictImporter.importMetamodel(source("class Person: pass"), MetamodelImportOptions.defaults())).isInstanceOf(RuntimeException.class).hasMessageContaining("could not be started");
    }

    @Test void importsMultipleModulesFromZip() throws Exception {
        byte[] archive = zip(Map.of("domain/address.py", "class Address:\n    street: str\n", "domain/person.py", "from .address import Address\nclass Person:\n    address: Address\n"));
        var output = importer("python", true).importMetamodel(new MetamodelImportSource("project.python.zip", "application/zip", archive), MetamodelImportOptions.defaults());
        assertThat(output.metamodel().classes()).hasSize(2);
        assertThat(output.metamodel().annotations().get("import:modules")).contains("domain.address", "domain.person");
    }

    @Test void rejectsSyntaxErrorsWithoutExecutingFallback() {
        assertThatThrownBy(() -> importPython("class Broken(\n")).isInstanceOf(RuntimeException.class).hasMessageContaining("Python AST parsing failed");
    }

    private MetamodelImportOutput importPython(String text) {
        return importer("python", true).importMetamodel(source(text), MetamodelImportOptions.defaults());
    }

    private MetamodelImportSource source(String text) {
        return new MetamodelImportSource("sample.py", "text/x-python", text.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] zip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try(ZipOutputStream output = new ZipOutputStream(bytes)) {
            for(var entry : files.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
