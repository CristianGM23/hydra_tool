package com.example.editor.metamodel.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.design.MetamodelDiagramDocument;
import com.example.editor.metamodel.target.EcoreProjectionConfiguration;
import com.example.editor.metamodel.target.TargetConfigurationDocument;

/** Un nombre del metamodelo que es palabra reservada de Python (p. ej. la relación «from» de un movimiento) no puede dejar código que no compila. */
class PythonMetamodelExporterKeywordTests {
    @TempDir Path temporary;

    @Test void reservedWordsAreEscapedAndTheModuleCompiles() throws Exception {
        String source = export();
        assertThat(source).contains("    from_: ").contains("    class_: ").contains("    None_ = 0").contains("class Movement");
        assertThat(source).doesNotContain("    from: ").doesNotContain("    class: ").doesNotContain("    None = 0");

        Path file = temporary.resolve("model.py");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        String python = System.getenv().getOrDefault("PYTHON", "python");
        Process process;
        try { process = new ProcessBuilder(python, "-m", "py_compile", file.toString()).redirectErrorStream(true).start(); }
        catch(java.io.IOException unavailable) { Assumptions.abort("python is not available to compile the generated module"); return; }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(output).isZero();
    }

    private String export() throws Exception {
        MetaEnum flag = new MetaEnum("enum-flag", "Flag", List.of(new MetaEnumLiteral("literal-none", "None", 0, Map.of()),
                new MetaEnumLiteral("literal-ok", "OK", 1, Map.of())), Map.of());
        MetaAttribute from = new MetaAttribute("attribute-from", "from", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute state = new MetaAttribute("attribute-flag", "flag", MetaTypeReference.enumType("enum-flag"), Multiplicity.optional(), true, true, "", Map.of());
        MetaClass movement = new MetaClass("class-movement", "Movement", MetaClassKind.CLASS, List.of(from, state), List.of(), Map.of());
        MetaClass location = new MetaClass("class-location", "Location", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation clazz = new MetaRelation("reference-class", "class", "class-movement", "class-location", MetaRelationKind.REFERENCE, null, Multiplicity.optional(), "", true, true, Map.of());
        MetaModel model = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "keywords", "Keywords", List.of(movement, location), List.of(flag), List.of(), List.of(clazz), Map.of());
        TargetConfigurationDocument targets = new TargetConfigurationDocument(TargetConfigurationDocument.CURRENT_SCHEMA_VERSION, model.id(),
                EcoreProjectionConfiguration.defaultsFor(model), Map.of());
        NeutralMetamodelExportContext context = new DefaultNeutralMetamodelExportContext(model, new MetamodelDiagramDocument(model.id(), List.of(), List.of()), targets,
                List.of(), MetamodelExportOptions.defaults());
        try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(new PythonMetamodelExporter().export(context)))) {
            assertThat(zip.getNextEntry()).isNotNull();
            return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
