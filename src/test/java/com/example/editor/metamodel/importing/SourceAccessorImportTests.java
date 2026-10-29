package com.example.editor.metamodel.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.importing.importers.JavaMetamodelImporter;
import com.example.editor.metamodel.importing.importers.PythonMetamodelImporter;
import com.example.editor.metamodel.importing.python.PythonAstImportProperties;
import com.example.editor.metamodel.importing.python.PythonProcessAstAnalyzer;

import tools.jackson.databind.json.JsonMapper;

/**
 * The accessors of a recovered field (JavaBeans get/is/set, Python get_/set_) and the Object protocol (equals/hashCode/toString,
 * __eq__/__str__/__repr__/__hash__) are how source code represents an attribute or a relation, not domain operations. Importing them as
 * operations invented 252 operations on a metamodel with none, and the next Java export declared every accessor twice and did not compile
 * (battery scenario MULTIGEN, third generation: 400 OPERATION_ID_DUPLICATE). Real operations, including get-prefixed ones that are not
 * accessors of a field, are kept.
 */
class SourceAccessorImportTests {

    private final MetamodelImportProperties properties = new MetamodelImportProperties();

    @Test
    void javaBeanAccessorsAndObjectMethodsOfRecoveredFieldsAreNotOperations() {
        String source = """
                package demo;
                import java.util.List;
                public class Order {
                    private String code;
                    private boolean paid;
                    private List<Line> lines;
                    public String getCode() { return code; }
                    public void setCode(String value) { this.code = value; }
                    public boolean isPaid() { return paid; }
                    public void setPaid(boolean value) { this.paid = value; }
                    public List<Line> getLines() { return lines; }
                    public void setLines(List<Line> value) { this.lines = value; }
                    public double total() { return 0; }
                    public String getSummary(int width) { return ""; }
                    public String getLabel() { return code; }
                    @Override public boolean equals(Object other) { return this == other; }
                    @Override public int hashCode() { return 1; }
                    @Override public String toString() { return code; }
                }
                class Line { }
                """;
        MetamodelImportOutput output = new JavaMetamodelImporter(new SafeImportArchiveReader(properties))
                .importMetamodel(new MetamodelImportSource("Order.java", "text/x-java", source.getBytes(StandardCharsets.UTF_8)), MetamodelImportOptions.defaults());
        MetaClass order = output.metamodel().classes().stream().filter(c -> c.name().equals("Order")).findFirst().orElseThrow();

        // total() is a real operation; getSummary(int) has a parameter and getLabel() has no field "label": neither is an accessor
        assertThat(order.operations()).extracting(MetaOperation::name).containsExactly("total", "getSummary", "getLabel");
        assertThat(order.attributes()).extracting(a -> a.name()).containsExactly("code", "paid");
        assertThat(output.warnings()).extracting(TransformationWarning::code).contains("IMPORT_JAVA_ACCESSORS_OMITTED");
    }

    @Test
    void pythonAccessorsAndDunderProtocolOfRecoveredFieldsAreNotOperations() {
        String source = """
                from dataclasses import dataclass, field
                from typing import List

                @dataclass
                class Line:
                    sku: str = ""

                @dataclass
                class Order:
                    code: str = ""
                    lines: List[Line] = field(default_factory=list)

                    def get_code(self) -> str:
                        return self.code

                    def set_code(self, value: str) -> None:
                        self.code = value

                    def get_lines(self) -> List[Line]:
                        return self.lines

                    def set_lines(self, value: List[Line]) -> None:
                        self.lines = value

                    def total(self) -> float:
                        return 0.0

                    def get_label(self) -> str:
                        return self.code

                    def __eq__(self, other: object) -> bool:
                        return self is other

                    def __str__(self) -> str:
                        return self.code
                """;
        PythonAstImportProperties settings = new PythonAstImportProperties();
        settings.setExecutable("python");
        settings.setFallbackEnabled(true);
        MetamodelImportOutput output = new PythonMetamodelImporter(new SafeImportArchiveReader(properties), new PythonProcessAstAnalyzer(JsonMapper.builder().build(), settings))
                .importMetamodel(new MetamodelImportSource("order.py", "text/x-python", source.getBytes(StandardCharsets.UTF_8)), MetamodelImportOptions.defaults());
        MetaClass order = output.metamodel().classes().stream().filter(c -> c.name().equals("Order")).findFirst().orElseThrow();

        assertThat(order.operations()).extracting(MetaOperation::name).containsExactly("total", "get_label");
        assertThat(output.warnings()).extracting(TransformationWarning::code).contains("IMPORT_PYTHON_ACCESSORS_OMITTED");
    }
}
