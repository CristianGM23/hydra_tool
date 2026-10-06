package com.example.editor.integration.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.InjectionFileOwnership;
import com.example.editor.integration.IntegrationTechnology;
import tools.jackson.databind.json.JsonMapper;

class TechnologyCompilationArchiveServiceTests {
    private final TechnologyCompilationArchiveService service=new TechnologyCompilationArchiveService(JsonMapper.builder().findAndAddModules().build());
    @Test void createsDeterministicArchives(){var result=result();assertThat(service.createArchive(result)).isEqualTo(service.createArchive(result));}
    @Test void sortsGeneratedEntries(){assertThat(entries(service.createArchive(result()))).containsExactly("a.txt","z.txt","META-INF/hydra-technology-compilation.json");}
    @Test void includesCompilationManifest(){assertThat(entries(service.createArchive(result()))).contains("META-INF/hydra-technology-compilation.json");}
    @Test void preservesGeneratedContent(){assertThat(content(service.createArchive(result()),"a.txt")).isEqualTo("a");}
    @Test void usesStableTimestamps() throws Exception {byte[] first=service.createArchive(result());byte[] second=service.createArchive(result());try(ZipInputStream left=new ZipInputStream(new ByteArrayInputStream(first));ZipInputStream right=new ZipInputStream(new ByteArrayInputStream(second))){assertThat(left.getNextEntry().getTime()).isEqualTo(right.getNextEntry().getTime());}}

    private TechnologyCompilationResult result(){var a=new TechnologyGeneratedFile("a.txt","text/plain","a".getBytes(StandardCharsets.UTF_8),InjectionFileOwnership.GENERATED,true,List.of(),Map.of());var z=new TechnologyGeneratedFile("z.txt","text/plain","z".getBytes(StandardCharsets.UTF_8),InjectionFileOwnership.GENERATED,true,List.of(),Map.of());return new TechnologyCompilationResult(IntegrationTechnology.EMF,List.of(z,a),List.of(),List.of(),List.of("rollback"),List.of(),List.of(),Map.of());}
    private List<String> entries(byte[] content){try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(content))){List<String> values=new ArrayList<>();for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())values.add(entry.getName());return values;}catch(Exception exception){throw new AssertionError(exception);}}
    private String content(byte[] archive,String name){try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())if(entry.getName().equals(name))return new String(zip.readAllBytes(),StandardCharsets.UTF_8);throw new AssertionError("Missing "+name);}catch(Exception exception){throw new AssertionError(exception);}}
}
