package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.importing.importers.LangiumMetamodelImporter;
import com.example.editor.metamodel.importing.importers.XtextMetamodelImporter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assignments whose value is a keyword (Langium's domain-model example: {@code (many?='many')?}; Xtext's {@code (abstract?='abstract')?})
 * must be imported like any other feature, wherever they appear (found by the battery scenario INT-REAL on the real Langium grammar).
 */
class GrammarKeywordAssignmentImportTests {

    @Test
    void langiumKeywordAssignmentsInsideAndOutsideGroupsBecomeFeatures() {
        String grammar = """
                grammar Shop

                entry Model:
                    (features+=Feature)*;

                Feature:
                    (many?='many')? name=ID ('readonly' locked?='locked')? sign='+' ':' type=ID (unit=('kg'|'g'))?;

                terminal ID: /[_a-zA-Z][\\w_]*/;
                """;
        Map<String, MetaAttribute> feature = attributes(new LangiumMetamodelImporter().importMetamodel(source("shop.langium", grammar), MetamodelImportOptions.defaults()), "Feature");

        assertThat(feature).containsOnlyKeys("many", "name", "locked", "sign", "type", "unit");
        assertThat(feature.get("many").type().targetId()).isEqualTo(MetaPrimitiveType.BOOLEAN.name());
        assertThat(feature.get("many").multiplicity().lowerBound()).isZero();
        assertThat(feature.get("locked").type().targetId()).isEqualTo(MetaPrimitiveType.BOOLEAN.name());
        // a keyword value is text, and the assignment that follows it keeps its own type
        assertThat(feature.get("sign").type().targetId()).isEqualTo(MetaPrimitiveType.STRING.name());
        assertThat(feature.get("sign").multiplicity().lowerBound()).isEqualTo(1);
        assertThat(feature.get("name").type().targetId()).isEqualTo(MetaPrimitiveType.STRING.name());
    }

    @Test
    void xtextOptionalKeywordFlagsAreImported() {
        String grammar = """
                grammar org.example.Classes with org.eclipse.xtext.common.Terminals

                generate classes "http://www.example.org/classes"

                Model:
                    classes+=Class*;

                Class:
                    (abstract?='abstract')? (final?="final")? 'class' name=ID '{' '}';
                """;
        Map<String, MetaAttribute> type = attributes(new XtextMetamodelImporter().importMetamodel(source("classes.xtext", grammar), MetamodelImportOptions.defaults()), "Class");

        assertThat(type).containsOnlyKeys("abstract", "final", "name");
        assertThat(type.get("abstract").type().targetId()).isEqualTo(MetaPrimitiveType.BOOLEAN.name());
        assertThat(type.get("final").type().targetId()).isEqualTo(MetaPrimitiveType.BOOLEAN.name());
    }

    private static Map<String, MetaAttribute> attributes(MetamodelImportOutput output, String className) {
        MetaClass value = output.metamodel().classes().stream().filter(item -> item.name().equals(className)).findFirst().orElseThrow();
        return value.attributes().stream().collect(Collectors.toMap(MetaAttribute::name, Function.identity()));
    }

    private static MetamodelImportSource source(String name, String content) {
        return new MetamodelImportSource(name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }
}
