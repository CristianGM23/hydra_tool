package com.example.editor.integration.roundtrip;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.integration.InjectionFileOwnership;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import tools.jackson.databind.json.JsonMapper;

class RoundTripSyncServiceTests {
    @TempDir Path root;
    private final JsonMapper mapper=JsonMapper.builder().findAndAddModules().build();
    private final RoundTripSyncService service=new RoundTripSyncService(mapper,new ManualRegionPreserver());

    @Test void newGeneratedFileIsSelectable(){var preview=service.preview(compilation(file("src/A.java","new")),"profile",root);assertEquals(1,preview.changes().size());assertEquals(RoundTripChangeKind.ADD,preview.changes().get(0).kind());assertTrue(preview.changes().get(0).selectable());}
    @Test void unchangedGeneratedFileIsNotSelectable() throws Exception{writeManifestAndFile("src/A.java","same");var preview=service.preview(compilation(file("src/A.java","same")),"profile",root);assertEquals(RoundTripChangeKind.UNCHANGED,preview.changes().get(0).kind());}
    @Test void canonicalChangeRegeneratesUnmodifiedFile() throws Exception{writeManifestAndFile("src/A.java","old");var preview=service.preview(compilation(file("src/A.java","new")),"profile",root);assertEquals(List.of("src/A.java"),preview.selectablePaths());assertEquals(RoundTripChangeKind.REGENERATE,preview.changes().get(0).kind());}
    @Test void simultaneousManualAndCanonicalChangeBlocksSync() throws Exception{writeManifestAndFile("src/A.java","old");Files.writeString(root.resolve("src/A.java"),"manual");var preview=service.preview(compilation(file("src/A.java","new")),"profile",root);assertTrue(preview.blocked());assertTrue(preview.conflicts().contains("src/A.java"));}
    @Test void orphanNativeFileBlocksAddition() throws Exception{Files.createDirectories(root.resolve("src"));Files.writeString(root.resolve("src/A.java"),"manual");var preview=service.preview(compilation(file("src/A.java","new")),"profile",root);assertEquals(RoundTripChangeKind.ORPHAN_NATIVE,preview.changes().get(0).kind());}
    @Test void missingManagedFileCanBeRegenerated() throws Exception{writeManifest("src/A.java","old");var preview=service.preview(compilation(file("src/A.java","new")),"profile",root);assertEquals(RoundTripChangeKind.NATIVE_DELETED,preview.changes().get(0).kind());assertTrue(preview.changes().get(0).selectable());}
    @Test void obsoleteManagedFileRequiresManagedRemoval() throws Exception{writeManifestAndFile("src/A.java","old");var preview=service.preview(compilation(),"profile",root);assertEquals(1,preview.changes().size());assertEquals(RoundTripChangeKind.NATIVE_DELETED,preview.changes().get(0).kind());assertFalse(preview.changes().get(0).selectable());}
    @Test void selectiveCompilationPreservesManualRegions() throws Exception{writeManifestAndFile("src/A.java","// <hydra:manual id=\"custom\">OLD// </hydra:manual>");var generated=compilation(file("src/A.java","// <hydra:manual id=\"custom\">NEW// </hydra:manual>"));var preview=service.preview(generated,"profile",root);var selected=service.selectiveCompilation(generated,preview,root,Set.of("src/A.java"),true);String value=new String(selected.files().get(0).content(),StandardCharsets.UTF_8);assertTrue(value.contains("OLD"));assertFalse(value.contains("NEW"));}
    @Test void selectionRejectsBlockedPath(){var generated=compilation(file("src/A.java","new"));var preview=new RoundTripSyncPreview(IntegrationTechnology.LANGIUM,"profile",List.of(),List.of("src/A.java"),List.of(),Map.of());assertThrows(TechnologyCompilationException.class,()->service.selectiveCompilation(generated,preview,root,Set.of("src/A.java"),true));}
    @Test void unsafeGeneratedPathIsRejected(){assertThrows(TechnologyCompilationException.class,()->service.preview(compilation(file("../escape","bad")),"profile",root));}
    @Test void manualRegionParserKeepsNamedBodies(){var bodies=new ManualRegionPreserver().bodies("# <hydra:manual id=\"one\">hello# </hydra:manual>");assertEquals("hello",bodies.get("one"));}

    private TechnologyGeneratedFile file(String path,String value){return new TechnologyGeneratedFile(path,"text/plain",value.getBytes(StandardCharsets.UTF_8),InjectionFileOwnership.GENERATED,true,List.of("people"),Map.of());}
    private TechnologyCompilationResult compilation(TechnologyGeneratedFile... files){return new TechnologyCompilationResult(IntegrationTechnology.LANGIUM,List.of(files),List.of(),List.of(),List.of(),List.of(),List.of(),Map.of());}
    private void writeManifestAndFile(String path,String value) throws Exception{writeManifest(path,value);Path target=root.resolve(path);Files.createDirectories(target.getParent());Files.writeString(target,value);}
    private void writeManifest(String path,String value) throws Exception{var mapping=new RoundTripElementMapping("people",com.example.editor.integration.IntegrationArtifactKind.METAMODEL,path,List.of(),sha(value),InjectionFileOwnership.GENERATED,Map.of());var manifest=new RoundTripManifest("1.0","profile",IntegrationTechnology.LANGIUM,"test",java.time.Instant.EPOCH,List.of(mapping),Map.of());Path target=root.resolve(RoundTripSyncService.MANIFEST_PATH);Files.createDirectories(target.getParent());Files.write(target,mapper.writeValueAsBytes(manifest));}
    private String sha(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
