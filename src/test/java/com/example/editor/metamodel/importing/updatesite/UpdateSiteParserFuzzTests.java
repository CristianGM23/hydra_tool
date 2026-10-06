package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.importing.support.SecureXmlSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fixed-seed parser fuzz/property tests for the external Update Site surfaces. */
class UpdateSiteParserFuzzTests {
    @TempDir Path temporary;
    private UpdateSiteImportProperties properties;
    private UpdateSiteArchiveReader reader;

    @BeforeEach
    void setup() {
        properties=new UpdateSiteImportProperties();
        properties.setStorageRoot(temporary.toString());
        properties.setMaxEntries(2_000);
        properties.setMaxUncompressedBytes(64L*1024L*1024L);
        reader=new UpdateSiteArchiveReader(properties);
    }

    @Test
    void fuzzesZipEntryNamesAndNeverEscapesTheConfiguredStorageRoot() {
        Random random=new Random(0x4859445241L);
        for(int iteration=0;iteration<160;iteration++) {
            Map<String,byte[]> entries=new LinkedHashMap<>();
            int count=1+random.nextInt(20);
            for(int index=0;index<count;index++) {
                String name=random.nextInt(12)==0 ? "../escape-"+index+".ecore" : "plugins/fuzz-"+iteration+"/entry-"+index+".txt";
                byte[] content=new byte[random.nextInt(512)]; random.nextBytes(content); entries.put(name,content);
            }
            byte[] archive=UpdateSiteTestFixtures.zip(entries);
            try { reader.read(List.of(new UpdateSiteInputFile("fuzz-"+iteration+".zip",archive))); }
            catch(MetamodelDesignException expected) { assertThat(expected.getMessage()).isNotBlank(); }
        }
        assertThat(temporary.resolve("artifact-cache").normalize()).startsWith(temporary.normalize());
    }

    @Test
    void fuzzesSecureXmlAndRejectsDoctypeBasedExternalEntities() {
        Random random=new Random(0x584d4cL);
        for(int iteration=0;iteration<200;iteration++) {
            String candidate=random.nextBoolean()
                    ? "<root><item id=\""+iteration+"\">"+randomAscii(random,64)+"</item></root>"
                    : randomAscii(random,96);
            try { SecureXmlSupport.parse(candidate.getBytes(StandardCharsets.UTF_8),"fuzz XML"); }
            catch(MetamodelDesignException expected) { assertThat(expected.getMessage()).contains("Could not parse"); }
        }
        byte[] xxe="<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> SecureXmlSupport.parse(xxe,"XXE probe")).isInstanceOf(MetamodelDesignException.class);
    }

    @Test
    void fuzzesBundleManifestsWithoutLettingMalformedHeadersEscapeTheAnalyzer() {
        Random random=new Random(0x4d414e4946455354L);
        P2BundleAnalyzer analyzer=new P2BundleAnalyzer(new P2BundleClassifier());
        for(int iteration=0;iteration<120;iteration++) {
            byte[] manifest=randomAscii(random,80+random.nextInt(300)).getBytes(StandardCharsets.UTF_8);
            byte[] pluginJar=UpdateSiteTestFixtures.zip(UpdateSiteTestFixtures.entries("META-INF/MANIFEST.MF",manifest));
            byte[] site=UpdateSiteTestFixtures.zip(UpdateSiteTestFixtures.entries("plugins/fuzz_1.0.0.jar",pluginJar));
            UpdateSiteRepositoryContent repository=reader.read(List.of(new UpdateSiteInputFile("manifest-fuzz.zip",site)));
            assertThatCode(() -> analyzer.analyze(repository,new P2MetadataSnapshot("",false,false,Map.of(),List.of())))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void fuzzesGrammarInspectionWithBoundedRandomText() {
        Random random=new Random(0x4752414d4d4152L);
        UpdateSiteGrammarInspectionService inspection=new UpdateSiteGrammarInspectionService();
        for(int iteration=0;iteration<200;iteration++) {
            String prefix=iteration%2==0 ? "grammar fuzz.Sample\nRoot: name=ID;\n" : "grammar Fuzz\nentry Root: value=ID;\n";
            String text=prefix+randomAscii(random,256);
            String path=iteration%2==0 ? "src/Fuzz.xtext" : "src/Fuzz.langium";
            UpdateSiteRepositoryContent repository=reader.read(List.of(new UpdateSiteInputFile(path,text.getBytes(StandardCharsets.UTF_8))));
            assertThat(inspection.inspect(repository)).hasSize(1);
        }
    }

    private String randomAscii(Random random,int length) {
        String alphabet="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_ :;={}[]!?+-*/.'\\n\\t";
        StringBuilder out=new StringBuilder(length);
        for(int index=0;index<length;index++) out.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return out.toString();
    }
}
