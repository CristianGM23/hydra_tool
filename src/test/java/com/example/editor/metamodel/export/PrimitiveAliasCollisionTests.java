package com.example.editor.metamodel.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;

/** DEF-EXP-PRIMITIVE-ALIAS-COLLISION: the TypeScript and Go exporters declare aliases for the numeric primitives (Integer, Decimal, BigInteger…)
 *  when the generated code mentions them. A metamodel class with one of those names is mentioned too, and the alias must not redeclare it:
 *  the exported code has to keep compiling (tsc --strict, go build). */
class PrimitiveAliasCollisionTests {
    @TempDir Path temporary;

    @Test void aClassNamedLikeANumericAliasIsDeclaredOnceInTypeScript() throws Exception {
        Map<String,String> files = unzip(new TypeScriptMetamodelExporter().export(BlocklyExportFingerprint.context(model())));
        String source = files.get("src/model.ts");
        assertThat(declarations(source, "(?:class|interface|type|enum)", "Decimal")).as(source).isEqualTo(1);
        assertThat(declarations(source, "(?:class|interface|type|enum)", "Integer")).as("the primitive alias still exists when no class takes its name").isEqualTo(1);
        Path root = write(files);
        Path tsc = Path.of(System.getProperty("user.dir"), "frontend", "node_modules", "typescript", "bin", "tsc");
        Assumptions.assumeTrue(Files.isRegularFile(tsc), "tsc is not installed in frontend/node_modules");
        run(root, "node", tsc.toString(), "-p", root.toString(), "--noEmit", "--strict");
    }

    @Test void aClassNamedLikeANumericAliasIsDeclaredOnceInGo() throws Exception {
        Map<String,String> files = unzip(new GoMetamodelExporter().export(BlocklyExportFingerprint.context(model())));
        String source = files.get("model/model.go");
        assertThat(declarations(source, "type", "Decimal")).as(source).isEqualTo(1);
        run(write(files), "go", "build", "./...");
    }

    /** A class Decimal (referenced by a relation) next to attributes typed by the INTEGER and DECIMAL primitives. */
    private static MetaModel model() {
        MetaAttribute count = new MetaAttribute("attribute-count", "count", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER), Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute rate = new MetaAttribute("attribute-rate", "rate", MetaTypeReference.primitive(MetaPrimitiveType.DECIMAL), Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute scale = new MetaAttribute("attribute-scale", "scale", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER), Multiplicity.optional(), true, true, "", Map.of());
        MetaClass order = new MetaClass("class-order", "Order", MetaClassKind.CLASS, List.of(count, rate), List.of(), Map.of());
        MetaClass decimal = new MetaClass("class-decimal", "Decimal", MetaClassKind.CLASS, List.of(scale), List.of(), Map.of());
        MetaRelation amount = new MetaRelation("relation-amount", "amount", "class-order", "class-decimal", MetaRelationKind.REFERENCE, null, Multiplicity.optional(), "", true, true, Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, "money", "Money", List.of(order, decimal), List.of(), List.of(), List.of(amount), Map.of());
    }

    private static long declarations(String source, String keyword, String name) {
        return Pattern.compile("(?m)^\\s*(?:export\\s+)?(?:abstract\\s+)?" + keyword + "\\s+" + name + "\\b").matcher(source).results().count();
    }

    private static Map<String,String> unzip(byte[] archive) throws Exception {
        Map<String,String> files = new java.util.LinkedHashMap<>();
        try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for(ZipEntry entry; (entry = zip.getNextEntry()) != null; ) files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
        return files;
    }

    private Path write(Map<String,String> files) throws Exception {
        Path root = Files.createTempDirectory(temporary, "export");
        for(var file : files.entrySet()) { Path target = root.resolve(file.getKey()); Files.createDirectories(target.getParent()); Files.writeString(target, file.getValue()); }
        return root;
    }

    private static void run(Path directory, String... command) throws Exception {
        Process process;
        try { process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start(); }
        catch(java.io.IOException unavailable) { Assumptions.abort(command[0] + " is not available to compile the exported code"); return; }
        boolean finished = process.waitFor(180, TimeUnit.SECONDS);
        if(!finished) process.destroyForcibly();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(finished).as("%s finished", command[0]).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
    }
}
